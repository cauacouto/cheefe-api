# Login com Google (BFF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o login por OTP/Resend por login com Google conduzido por um BFF dentro da API, com cliente e chef autenticando pelo cookie `accessToken`.

**Architecture:** O `oauth2Login` do Spring Security faz Authorization Code + PKCE; o authorization request fica num cookie cifrado (sem sessão). Um success handler procura o e-mail em `usuarios`/`chefe` e grava `accessToken` (JWT da API com `tipo`) ou `registroToken` (cadastro pendente). O `SecurityFilter` monta um `UsuarioAutenticado` (implementa `UserDetails`) só a partir dos claims.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Security 7.1, `spring-boot-starter-security-oauth2-client`, auth0 `java-jwt` 4.5.2, JUnit 5 + Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-20-login-google-design.md`

## Global Constraints

- Nomes em português, seguindo o projeto; Lombok `@RequiredArgsConstructor` nas classes Spring, exceto quando um construtor recebe `@Value`.
- Cookies: todos `HttpOnly`, `SameSite=Lax`, `Secure` = `auth.cookie.secure` (default `true`).
  - `accessToken`: `Path=/`, `Max-Age=28800` (8h).
  - `registroToken`: `Path=/auth/register`, `Max-Age=900` (15 min).
  - `oauth2_auth_request`: `Path=/`, `Max-Age=180`.
- JWTs da API: HS256 com `jwt.key`, `iss=chefe-api`, `sub=email`, claim `finalidade` = `acesso` | `registro`.
- Redirects do BFF: sucesso com conta → `{app.front-url}/`; sem conta → `{app.front-url}/cadastro`; qualquer falha → `{app.front-url}/login?erro=google`.
- E-mail vindo do Google é normalizado com `trim().toLowerCase(Locale.ROOT)`.
- Sessão continua `STATELESS`; nenhum `JSESSIONID` pode ser criado pelo fluxo.
- **Não editar** (branch de refatoração em paralelo): `Services/**`, `Controller/` exceto `AuthController`, exceções já existentes em `Excepitons/`, `repositorys/**`, DTOs fora dos citados aqui.
- Comando de teste: `./mvnw -q test -Dspring.profiles.active=test` (classe única: acrescente `-Dtest=NomeDaClasse`).
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Usuário cancela no consentimento do Google** (`/login/oauth2/code/google?error=access_denied&state=…`) → redirect `{front}/login?erro=google`, nunca 500. Teste na Task 8.
2. **Callback sem o cookie `oauth2_auth_request`** (expirou após 180s, ou o fluxo foi aberto em outro navegador) → redirect de erro, nunca 500. Teste na Task 8.
3. **`accessToken` expirado ou adulterado numa rota protegida** → 401, sem exceção no filtro (o filtro antigo lançava `UsuarioException`). Testes nas Tasks 3 e 8.
4. **Conta Google sem foto** (`picture` ausente) → `registroToken` com `foto` nula, e o cadastro sem upload grava `imageUrl` nulo. Testes nas Tasks 2 e 4.
5. **E-mail do Google com maiúsculas** (`Ana@Gmail.com`) → encontra a conta já gravada como `ana@gmail.com`. Teste na Task 7.

---

### Task 1: Remover o OTP, o Resend e o Redis

**Files:**
- Delete: `src/main/java/com/couto/chefe_api/Security/otp/OtpService.java`, `Config/Infra/ResendService.java`, `Config/Infra/WebclientResendConfig.java`, `Dtos/EmailRequest.java`, `Dtos/SolicitarOtpDto.java`, `Dtos/OtpResponseDto.java`, `Dtos/otpData.java`, `Dtos/DadosLoginDto.java`, `Excepitons/OtpException.java`
- Modify: `Security/LoginService.java` (remove `solicitarOtp`, `login` e os campos `otpService`/`resendService`)
- Modify: `Controller/AuthController.java` (remove `/login` e `/request-otp`)
- Modify: `pom.xml` (remove `spring-boot-starter-data-redis`)
- Modify: `src/main/resources/application-test.yml` (remove o bloco `resend`)
- Modify: `.github/workflows/rotinaCi.yml` (remove o bloco `env` com `RESEND_API_KEY`)
- Modify: `src/test/java/com/couto/chefe_api/Controller/AuthControllerTest.java` (remove o teste de login; o helper `loginServiceReturning` passa a usar o novo construtor ou some, e o teste de logout fica)

**Interfaces:**
- Produces: `LoginService` com o construtor `(UsuarioRepository, UsuarioMapper, TokenService, ChefeMapper, ChefeRepository, Cloudinary)`. `LoginResult` e `LoginResponseDto` continuam existindo até a Task 4.

- [ ] **Step 1:** Apagar os arquivos e os trechos listados. Rodar `grep -rn -E "Otp|otp|Resend|resend|Redis" src .github pom.xml` → nenhuma ocorrência.
- [ ] **Step 2:** Rodar a suíte. Esperado: `contextLoads` e `logoutExpiresAccessTokenCookie` passam, BUILD SUCCESS.
- [ ] **Step 3:** Commit `refactor: remove OTP login, Resend and Redis`.

---

### Task 2: `GoogleUser`, `UsuarioAutenticado` e o novo `TokenService`

**Files:**
- Create: `Security/GoogleUser.java`, `Security/UsuarioAutenticado.java`
- Modify: `Security/TokenService.java`
- Test: `src/test/java/com/couto/chefe_api/Security/TokenServiceTest.java`

**Interfaces:**
- Produces:
  - `record GoogleUser(String email, String nome, String foto)`
  - `record UsuarioAutenticado(UUID id, String email, TipoUsuario tipo) implements UserDetails`:
    - `getUsername()` = email; `getPassword()` = `null`
    - `getAuthorities()` = `List.of(new SimpleGrantedAuthority("ROLE_" + tipo.name()))`
  - `TokenService(@Value("${jwt.key}") String secret)`, com estes métodos:
    - `String gerarToken(UsuarioAutenticado)`
    - `Optional<UsuarioAutenticado> validarToken(String)`
    - `String gerarTokenRegistro(GoogleUser)`
    - `Optional<GoogleUser> validarTokenRegistro(String)`
  - Os claims são `id` (UUID como string), `tipo` (nome do enum), `nome`, `foto` e `finalidade`.
  - A expiração é `Instant.now().plus(Duration.ofHours(8))` no token de acesso e `Duration.ofMinutes(15)` no de registro.

- [ ] **Step 1: Escrever os testes que falham** (`TokenServiceTest`, com `new TokenService("teste")`):
  - `tokenDeAcessoFazIdaEVolta`: `validarToken(gerarToken(new UsuarioAutenticado(id, "ana@gmail.com", CHEFE)))` → `Optional.of(` o mesmo record `)`, e `getAuthorities()` contém `ROLE_CHEFE`.
  - `tokenDeRegistroFazIdaEVolta`: `GoogleUser("ana@gmail.com", "Ana", "https://foto")` vai e volta igual.
  - `tokenDeRegistroSemFoto`: `GoogleUser("ana@gmail.com", "Ana", null)` volta com `foto()` == `null`.
  - `tokenDeRegistroNaoServeComoAcesso`: `validarToken(gerarTokenRegistro(g))` → `Optional.empty()`.
  - `tokenDeAcessoNaoServeComoRegistro`: `validarTokenRegistro(gerarToken(u))` → `Optional.empty()`.
  - `tokenAdulteradoEhRejeitado`: troca o último caractere → `Optional.empty()`.
  - `tokenDeOutraChaveEhRejeitado`: token gerado por `new TokenService("outra")` → `Optional.empty()`.
  - `tokenVazioOuNuloEhRejeitado`: `""` e `null` → `Optional.empty()`.
- [ ] **Step 2:** Rodar `-Dtest=TokenServiceTest`. Esperado: falha de compilação (as classes ainda não existem).
- [ ] **Step 3:** Implementar `GoogleUser`, `UsuarioAutenticado` e `TokenService`. Toda `JWTVerificationException`, claim ausente ou `finalidade` diferente vira `Optional.empty()`.
- [ ] **Step 4:** Rodar `-Dtest=TokenServiceTest` → PASS.
- [ ] **Step 5:** Commit `feat: typed JWT claims with access and registration tokens`.

---

### Task 3: `AuthCookies` e o novo `SecurityFilter`

**Files:**
- Create: `Security/AuthCookies.java`
- Modify: `Security/SecurityFilter.java`, `Controller/AuthController.java` (o logout passa a usar `AuthCookies` e expira `accessToken` e `registroToken`)
- Test: `src/test/java/com/couto/chefe_api/Security/AuthCookiesTest.java`, `Security/SecurityFilterTest.java`, `Controller/AuthControllerTest.java` (teste de logout)

**Interfaces:**
- Consumes: `TokenService.validarToken`, `UsuarioAutenticado` (Task 2).
- Produces: `@Component AuthCookies(@Value("${auth.cookie.secure:true}") boolean secure)` com:
  - as constantes `ACCESS_TOKEN = "accessToken"`, `REGISTRO_TOKEN = "registroToken"` e `OAUTH2_REQUEST = "oauth2_auth_request"`
  - `ResponseCookie accessToken(String)`, `ResponseCookie registroToken(String)` e `ResponseCookie oauth2Request(String)`, com path e max-age conforme as Global Constraints
  - `ResponseCookie expirar(String nome, String path)`, com `Max-Age=0`
  - `void adicionar(HttpServletResponse, ResponseCookie)`, que adiciona o header `Set-Cookie`
  - `Optional<String> ler(HttpServletRequest, String nome)`, que ignora valores em branco
  - `SecurityFilter(TokenService, AuthCookies)`: sem `shouldNotFilter` e sem `UsuarioRepository`.

- [ ] **Step 1: Escrever os testes que falham:**
  - `AuthCookiesTest.accessTokenTemAtributosCorretos` (`secure=true`): o header contém `accessToken=v`, `Path=/`, `Max-Age=28800`, `HttpOnly`, `SameSite=Lax` e `Secure`.
  - `AuthCookiesTest.registroTokenTemPathDeCadastro`: `Path=/auth/register` e `Max-Age=900`.
  - `AuthCookiesTest.semSecureQuandoDesligado` (`secure=false`): o header não contém `Secure`.
  - `AuthCookiesTest.lerIgnoraCookieEmBranco`: um request com `accessToken=""` → `Optional.empty()`.
  - `SecurityFilterTest.cookieValidoAutentica`: um request com o cookie `accessToken` de `gerarToken(u)` → depois do `doFilter`, o principal de `SecurityContextHolder` é igual a `u`, e as authorities contêm `ROLE_CLIENTE`. A cadeia segue.
  - `SecurityFilterTest.cookieInvalidoNaoAutenticaNemLanca`: `accessToken=lixo` → a autenticação é `null` e a cadeia segue.
  - `SecurityFilterTest.semCookieNaoAutentica`.
  - `AuthControllerTest.logoutExpiraOsDoisCookies`: os headers `Set-Cookie` contêm `accessToken=` com `Max-Age=0` e `registroToken=` com `Path=/auth/register` e `Max-Age=0`.
  - Limpe o `SecurityContextHolder` com `clearContext()` num `@AfterEach`.
- [ ] **Step 2:** Rodar `-Dtest='AuthCookiesTest,SecurityFilterTest,AuthControllerTest'` → FAIL.
- [ ] **Step 3:** Implementar `AuthCookies`, reescrever o `SecurityFilter` e ajustar o logout.
- [ ] **Step 4:** Rodar os mesmos testes → PASS. Depois a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: typed principal from JWT claims and shared auth cookies`.

---

### Task 4: `LoginService`: login com Google e cadastro

**Files:**
- Create: `Dtos/RegisterResult.java`, `Dtos/RegistroPendenteDto.java`, `Excepitons/RegistroTokenInvalidoException.java` (`@ResponseStatus(UNAUTHORIZED)`), `Excepitons/EmailJaCadastradoException.java` (`@ResponseStatus(CONFLICT)`)
- Modify: `Dtos/LoginResult.java` → `(String accessToken, String registroToken)`
- Modify: `Dtos/RegisterRequestDto.java` (remove `email`)
- Modify: `Mapper/UsuarioMapper.java` e `Mapper/ChefeMapper.java` (`@Mapping(target = "email", ignore = true)` em `toEntity`/`toModel`)
- Modify: `Security/LoginService.java`
- Delete: `Dtos/LoginResponseDto.java`
- Test: `src/test/java/com/couto/chefe_api/Security/LoginServiceTest.java`

**Interfaces:**
- Consumes: `TokenService`, `GoogleUser` e `UsuarioAutenticado` (Task 2).
- Produces:
  - `record RegisterResult(String accessToken, Object corpo)`, onde o corpo é um `UserResponseDto` ou um `ChefeResponseDto`
  - `record RegistroPendenteDto(String email, String nome, String foto)`
  - `LoginResult loginGoogle(GoogleUser)`: exatamente um dos dois campos vem não-nulo
  - `RegistroPendenteDto dadosRegistro(String registroToken)`
  - `RegisterResult register(RegisterRequestDto dto, MultipartFile foto, String registroToken)`

- [ ] **Step 1: Escrever os testes que falham** (Mockito para repositórios e `Cloudinary`; `new TokenService("teste")` real; mappers via `Mappers.getMapper(...)`; nos stubs de `save`, a entidade volta com `id` preenchido):
  - `clienteExistenteRecebeTokenDeAcesso`: `usuarioRepository.findByEmail("ana@gmail.com")` acha → `validarToken(result.accessToken())` tem `tipo` `CLIENTE` e o `id` do usuário; `registroToken()` é `null`.
  - `chefExistenteRecebeTokenDeAcesso`: o usuário não existe e o chef existe → `tipo` `CHEFE`.
  - `emailNovoRecebeTokenDeRegistro`: nenhum dos dois existe → `accessToken()` é `null`, e `validarTokenRegistro(result.registroToken())` devolve o mesmo `GoogleUser`.
  - `dadosRegistroDevolveDadosDoToken` → `RegistroPendenteDto("ana@gmail.com", "Ana", "https://foto")`.
  - `dadosRegistroSemTokenLanca401`: `null` → `RegistroTokenInvalidoException`.
  - `registerClienteUsaEmailDoTokenELoga`: o dto tem `tipoUsuario=CLIENTE` e `endereco=List.of()` → o `UserModel` salvo tem `email` `ana@gmail.com`; `validarToken(accessToken)` tem `tipo` `CLIENTE`; `corpo` é instância de `UserResponseDto`.
  - `registerChefUsaEmailDoTokenELoga`: `tipoUsuario=CHEFE` → o `ChefeModel` salvo tem o e-mail do token e `tipo` `CHEFE`; `corpo` é instância de `ChefeResponseDto`.
  - `registerSemFotoUsaFotoDoGoogle`: `foto=null` → `imageUrl` salvo é `"https://foto"`, e `cloudinary` nunca é chamado.
  - `registerSemFotoEContaGoogleSemFotoGravaNulo`: o token tem `foto=null` → `imageUrl` salvo é `null`.
  - `registerComEmailJaExistenteEmQualquerTabelaLanca409`: e-mail presente em `chefeRepository` e `tipoUsuario=CLIENTE` → `EmailJaCadastradoException`, sem nenhum `save`.
  - `registerSemTokenOuComTokenDeAcessoLanca401`: `null` e `gerarToken(u)` → `RegistroTokenInvalidoException`.
  - `registerClienteSemEnderecoNaoQuebra`: `endereco=null` → salva com a lista vazia.
- [ ] **Step 2:** Rodar `-Dtest=LoginServiceTest` → FAIL.
- [ ] **Step 3:** Implementar. `uploadImg` continua como está. A checagem de duplicidade consulta as duas tabelas antes de qualquer upload.
- [ ] **Step 4:** Rodar `-Dtest=LoginServiceTest` → PASS. Depois a suíte completa → PASS (o `AuthController` ainda compila na Task 5; se não compilar aqui, junte as Tasks 4 e 5 num commit só).
- [ ] **Step 5:** Commit `feat: Google login and registration bound to verified email`.

---

### Task 5: Endpoints do `AuthController`

**Files:**
- Create: `Dtos/SessaoDto.java`, um `record(UUID id, String email, TipoUsuario tipo)`
- Modify: `Controller/AuthController.java`
- Test: `src/test/java/com/couto/chefe_api/Controller/AuthControllerTest.java` (`LoginService` mockado com Mockito, `new AuthCookies(false)`)

**Interfaces:**
- Consumes: `LoginService.dadosRegistro` e `LoginService.register` (Task 4); `AuthCookies` (Task 3).
- Produces:
  - `GET /auth/register` → `200 RegistroPendenteDto`
  - `POST /auth/register` (multipart; `@Valid @RequestPart("dados")`, `foto` opcional, `@CookieValue(name = AuthCookies.REGISTRO_TOKEN, required = false)`) → `201` com o corpo; grava o cookie `accessToken` e expira o `registroToken`
  - `GET /auth/me` (`@AuthenticationPrincipal UsuarioAutenticado`) → `200 SessaoDto`
  - `POST /auth/logout` (Task 3)

- [ ] **Step 1: Escrever os testes que falham:**
  - `getRegisterDevolveDadosPendentes`: o corpo é igual ao que `dadosRegistro("tok")` devolve.
  - `postRegisterGravaAccessTokenEExpiraRegistro`: com `register(...)` devolvendo `new RegisterResult("jwt", dto)` → status 201, corpo `dto`, um header com `accessToken=jwt` e outro com `registroToken=` e `Max-Age=0`.
  - `meDevolveSessao`: `UsuarioAutenticado(id, "ana@gmail.com", CHEFE)` → `SessaoDto(id, "ana@gmail.com", CHEFE)`.
- [ ] **Step 2:** Rodar `-Dtest=AuthControllerTest` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: register, me and logout endpoints for cookie session`.

---

### Task 6: `CookieOAuth2AuthorizationRequestRepository`

**Files:**
- Modify: `pom.xml` (adiciona `spring-boot-starter-security-oauth2-client`)
- Create: `Security/bff/CookieOAuth2AuthorizationRequestRepository.java`
- Test: `src/test/java/com/couto/chefe_api/Security/bff/CookieOAuth2AuthorizationRequestRepositoryTest.java`

**Interfaces:**
- Consumes: `AuthCookies.oauth2Request`, `AuthCookies.expirar` e `AuthCookies.ler` (Task 3).
- Produces: `@Component CookieOAuth2AuthorizationRequestRepository(AuthCookies, @Value("${jwt.key}") String chave) implements AuthorizationRequestRepository<OAuth2AuthorizationRequest>`.
  - Cifra com `Encryptors.stronger(chave, "6368656665617574")` (AES-256-GCM).
  - Serializa com `ObjectOutputStream`/`ObjectInputStream`.
  - Codifica em Base64URL sem padding.
  - A decifração acontece antes da desserialização. Qualquer exceção na leitura devolve `null`.
  - `save(null, …)` expira o cookie.

- [ ] **Step 1: Escrever os testes que falham.** O request de exemplo é `OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").clientId("cid").redirectUri("http://localhost/login/oauth2/code/google").state("st").attributes(a -> a.put("registration_id", "google")).build()`. Para levar o cookie gravado de uma resposta para a requisição seguinte, use um helper que lê o header `Set-Cookie` da resposta.
  - `salvaECarrega`: `load` devolve um request com `state` igual a `"st"`, `clientId` igual a `"cid"` e o atributo `registration_id` igual a `"google"`. O header contém `oauth2_auth_request=` e `Max-Age=180`.
  - `removeDevolveEExpira`: `remove` devolve o request e grava `oauth2_auth_request=` com `Max-Age=0`.
  - `cookieAdulteradoDevolveNulo`: trocar um caractere no meio do valor → `load` devolve `null`, sem lançar.
  - `cookieDeOutraChaveDevolveNulo`: um valor salvo por um repositório com chave `"outra"` → `null`.
  - `semCookieDevolveNulo`.
  - `salvarNuloExpira`: `save(null, req, res)` → `Max-Age=0`.
- [ ] **Step 2:** Rodar `-Dtest=CookieOAuth2AuthorizationRequestRepositoryTest` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar o teste → PASS. Depois a suíte completa → PASS (`contextLoads` sobe sem registro Google configurado).
- [ ] **Step 5:** Commit `feat: encrypted cookie storage for OAuth2 authorization request`.

---

### Task 7: Handlers de sucesso e de falha do BFF

**Files:**
- Create: `Security/bff/GoogleLoginSuccessHandler.java`, `Security/bff/GoogleLoginFailureHandler.java`
- Test: `src/test/java/com/couto/chefe_api/Security/bff/GoogleLoginSuccessHandlerTest.java`

**Interfaces:**
- Consumes: `LoginService.loginGoogle` (Task 4), `AuthCookies` (Task 3) e `GoogleUser` (Task 2).
- Produces:
  - `@Component GoogleLoginSuccessHandler(LoginService, AuthCookies, @Value("${app.front-url}") String frontUrl) implements AuthenticationSuccessHandler`. Ele lê o `OidcUser` do `OAuth2AuthenticationToken`: `getEmail()`, `getFullName()`, `getPicture()` e `getEmailVerified()`.
  - `@Component GoogleLoginFailureHandler(@Value("${app.front-url}") String frontUrl) implements AuthenticationFailureHandler`. Ele sempre redireciona para `{frontUrl}/login?erro=google`.

- [ ] **Step 1: Escrever os testes que falham.** O `LoginService` é mockado. O `Authentication` é `new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), idToken), List.of(), "google")`, onde o `idToken` tem `sub=123`, `email=Ana@Gmail.com`, `email_verified=true`, `name=Ana` e `picture=https://foto`. O `frontUrl` é `http://localhost:5173`.
  - `contaExistenteGravaAccessTokenERedirecionaParaRaiz`: `loginGoogle` devolve `LoginResult("jwt", null)` → redirect `http://localhost:5173/` e um header com `accessToken=jwt`.
  - `contaNovaGravaRegistroERedirecionaParaCadastro`: `LoginResult(null, "reg")` → redirect `http://localhost:5173/cadastro` e um header com `registroToken=reg`.
  - `emailEhNormalizado`: verifica `loginGoogle(new GoogleUser("ana@gmail.com", "Ana", "https://foto"))`.
  - `emailNaoVerificadoRedirecionaParaErroSemCookies`: com `email_verified=false` → redirect `http://localhost:5173/login?erro=google`, nenhum `Set-Cookie` e `loginGoogle` nunca é chamado.
  - `falhaRedirecionaParaErro` (`GoogleLoginFailureHandler`) → redirect `http://localhost:5173/login?erro=google`.
- [ ] **Step 2:** Rodar `-Dtest=GoogleLoginSuccessHandlerTest` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar o teste → PASS.
- [ ] **Step 5:** Commit `feat: BFF success and failure handlers for Google login`.

---

### Task 8: Ligar tudo no `SecurityConfiguration` e testar o fluxo

**Files:**
- Modify: `pom.xml` (adiciona `spring-boot-starter-security-test` com `<scope>test</scope>`)
- Modify: `Security/SecurityConfiguration.java`
- Modify: `src/main/resources/application-test.yml`:
  - `spring.security.oauth2.client.registration.google`: `client-id: teste-client-id`, `client-secret: teste-secret`, `scope: openid,profile,email`
  - `app.front-url: http://localhost:5173`
  - `auth.cookie.secure: false`
- Test: `src/test/java/com/couto/chefe_api/Security/OAuth2FlowIntegrationTest.java`. Use `@SpringBootTest` e `@ActiveProfiles("test")`, com `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()`.

**Interfaces:**
- Consumes: `CookieOAuth2AuthorizationRequestRepository` (Task 6), os handlers (Task 7), `SecurityFilter` (Task 3) e `TokenService` (Task 2).
- Produces: a cadeia final, com:
  - `oauth2Login`, cujo `authorizationEndpoint` recebe o repositório em cookie e um `DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization")` com `setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())`, além do `successHandler` e do `failureHandler`
  - `exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`
  - regras: `/auth/me` autenticado; `/auth/**`, `/oauth2/**`, `/login/oauth2/**`, `/error` e `/enderecos/cep/**` públicos; o resto autenticado

- [ ] **Step 1: Escrever os testes que falham:**
  - `inicioDoFluxoRedirecionaParaGoogleComPkce`: `GET /oauth2/authorization/google` → 302. O `Location` começa com `https://accounts.google.com/o/oauth2/v2/auth` e contém `state=`, `code_challenge=` e `code_challenge_method=S256`. Algum `Set-Cookie` contém `oauth2_auth_request=`, e `result.getRequest().getSession(false)` é `null`.
  - `callbackSemCookieRedirecionaParaErro`: `GET /login/oauth2/code/google?code=abc&state=xyz` → 302 para `http://localhost:5173/login?erro=google`.
  - `usuarioCancelouRedirecionaParaErro`: `GET /login/oauth2/code/google?error=access_denied&state=xyz` → 302 para `http://localhost:5173/login?erro=google`.
  - `rotaProtegidaSemCookieDa401`: `GET /usuario/enderecos` → 401 (e não um 302 para o Google).
  - `rotaProtegidaComTokenAdulteradoDa401`: cookie `accessToken=lixo` → 401.
  - `meComTokenValidoDevolveSessao`: cookie com `tokenService.gerarToken(new UsuarioAutenticado(id, "ana@gmail.com", CLIENTE))` → 200, `$.email` igual a `ana@gmail.com` e `$.tipo` igual a `CLIENTE`.
  - `meSemCookieDa401`.
- [ ] **Step 2:** Rodar `-Dtest=OAuth2FlowIntegrationTest` → FAIL.
- [ ] **Step 3:** Implementar a configuração.
- [ ] **Step 4:** Rodar a suíte completa → PASS, incluindo `contextLoads`.
- [ ] **Step 5:** Commit `feat: wire Google OAuth2 BFF into security chain`.
- [ ] **Step 6:** Na entrega, informar ao usuário o que adicionar no `application.yml` local (ignorado pelo git) e no deploy:
  - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FRONT_URL` e `AUTH_COOKIE_SECURE`, no formato do bloco "Configuração" do spec
  - o redirect URI `http://localhost:8080/login/oauth2/code/google`, a cadastrar no Google Cloud Console
