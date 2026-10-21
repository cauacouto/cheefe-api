# Login com Google (BFF) — Design

**Data:** 2026-10-20
**Status:** v2 — BFF aprovado em conversa, aguardando revisão do spec
**Branch:** `feat/login-google`

## Objetivo

Substituir o login por OTP (código por e-mail via Resend + Redis) por login social com Google, de forma que **clientes e chefs** consigam autenticar, o cadastro fique preso a um e-mail verificado pelo Google, **nenhum token do Google passe pelo navegador** e o restante da API continue autenticando pelo cookie `accessToken`.

## Decisões

| Decisão | Escolha |
|---|---|
| Quem conduz o OAuth | **BFF dentro desta API** (pacote `Security/bff`): Authorization Code + PKCE com `spring-boot-starter-security-oauth2-client`. Client secret só no servidor. Navegador só vê redirects e cookies HttpOnly. |
| Estado do fluxo OAuth | Authorization request guardado em **cookie cifrado** (AES-GCM), sem `HttpSession`. API continua stateless. |
| Identidade de chef | Mantém tabelas `usuarios` e `chefe` separadas; JWT ganha claim `tipo`. Um e-mail é cliente **ou** chef, nunca os dois. |
| Sessão | JWT próprio da API (HS256, 8h) em cookie HttpOnly `accessToken`, como hoje. |
| Validação no filtro | Principal montado só pelos claims do token, sem consulta ao banco. Conta inativada/deletada mantém acesso até o token expirar (máx. 8h) — aceito. |
| Cadastro | E-mail vem de um `registroToken` emitido pelo BFF, nunca do corpo da requisição. |
| OTP / Resend / Redis | Removidos por completo, sem fallback. |

## Fluxo

1. Front: botão "Entrar com Google" faz `window.location.href = API + "/oauth2/authorization/google"`.
2. BFF (`OAuth2AuthorizationRequestRedirectFilter`): gera `state`, `nonce` e PKCE (`code_verifier`/`code_challenge` S256), grava o authorization request no cookie `oauth2_auth_request` (cifrado, HttpOnly, SameSite=Lax, Max-Age 180s) e redireciona (302) para `accounts.google.com`.
3. Google autentica e redireciona para `GET /login/oauth2/code/google?code=...&state=...`.
4. BFF (`OAuth2LoginAuthenticationFilter`): lê e apaga o cookie, confere `state`, troca o `code` por tokens no endpoint do Google usando client secret + `code_verifier`, valida o ID token (assinatura, `iss`, `aud`, `exp`, `nonce` — `OidcIdTokenValidator` do Spring) e produz um `OidcUser`.
5. `GoogleLoginSuccessHandler`:
   - `email_verified != true` → redirect `{front}/login?erro=google`.
   - Monta `GoogleUser(email, nome, foto)` e chama `LoginService.loginGoogle(googleUser)`.
   - **Conta existe** (procura em `usuarios`, depois em `chefe`): grava `accessToken` e redireciona para `{front}/`.
   - **Conta não existe:** grava `registroToken` (JWT de registro, 15 min, `Path=/auth/register`) e redireciona para `{front}/cadastro`.
6. `GoogleLoginFailureHandler`: qualquer erro do fluxo (state inválido, usuário negou, troca do code falhou) → redirect `{front}/login?erro=google`.
7. Front em `/cadastro` chama `GET /auth/register` → `200 { email, nome, foto }` a partir do `registroToken` (401 se ausente/expirado), para pré-preencher o formulário.
8. `POST /auth/register` (multipart: parte `dados` = `RegisterRequestDto` sem `email`; parte opcional `foto`):
   - `registroToken` ausente/inválido/expirado → **401**.
   - E-mail já existe em `usuarios` ou `chefe` → **409**.
   - Cria `CLIENTE` (`UserModel` + endereços) ou `CHEFE` (`ChefeModel`) com o e-mail do token.
   - Sem `foto` enviada: `imageUrl` = foto do Google (claim do token de registro).
   - Apaga `registroToken`, grava `accessToken` e responde **201** com o DTO criado.
9. Front em `/` chama `GET /auth/me` → `200 { id, email, tipo }` ou **401**.
10. `POST /auth/logout` apaga `accessToken` e `registroToken`.

## Componentes

### Novos

- **`Security/bff/CookieOAuth2AuthorizationRequestRepository`** — implementa `AuthorizationRequestRepository<OAuth2AuthorizationRequest>`. Serializa o request (Java serialization), cifra com `Encryptors.stronger(chave, salt)` (AES-256-GCM; a autenticação do GCM é verificada **antes** de desserializar, então conteúdo forjado nunca chega ao `ObjectInputStream`), codifica em Base64URL e grava no cookie `oauth2_auth_request`. `removeAuthorizationRequest` lê e expira o cookie. Cookie corrompido/adulterado → trata como ausente (o Spring responde com falha → failure handler).
- **`Security/bff/GoogleLoginSuccessHandler`** — `AuthenticationSuccessHandler` descrito no passo 5.
- **`Security/bff/GoogleLoginFailureHandler`** — `AuthenticationFailureHandler` descrito no passo 6.
- **`Security/AuthCookies`** — componente único que cria/expira os cookies `accessToken`, `registroToken` e `oauth2_auth_request` (HttpOnly, SameSite=Lax, `Secure` conforme `auth.cookie.secure`). Usado pelo BFF, pelo `AuthController` e pelo repositório de authorization request.
- **`Security/GoogleUser`** — record `(String email, String nome, String foto)`.
- **`Security/UsuarioAutenticado`** — record `(UUID id, String email, TipoUsuario tipo)` implementando `UserDetails`: `getUsername()` = email, `getPassword()` = null, authorities = `ROLE_CLIENTE` ou `ROLE_CHEFE`. Corrige o NPE em `PratosController` (`@AuthenticationPrincipal UserDetails`) e o `authentication.getName()` em `UsuarioController`, sem alterá-los.
- **`Dtos/RegistroPendenteDto`** — record `(String email, String nome, String foto)`.
- **`Dtos/SessaoDto`** — record `(UUID id, String email, TipoUsuario tipo)`.
- **`Excepitons/RegistroTokenInvalidoException`** (401) e **`Excepitons/EmailJaCadastradoException`** (409) — com `@ResponseStatus`. (O pacote mantém o nome atual; renomear fica para a refatoração.)

### Alterados

- **`TokenService`**
  - `gerarToken(UsuarioAutenticado)` → JWT com `iss=chefe-api`, `sub=email`, claims `id`, `tipo`, `finalidade=acesso`, expira em `Instant.now().plus(8h)`.
  - `validarToken(String)` → `Optional<UsuarioAutenticado>`; rejeita tokens sem `finalidade=acesso`.
  - `gerarTokenRegistro(GoogleUser)` → JWT com `sub=email`, claims `nome`, `foto`, `finalidade=registro`, expira em 15 min.
  - `validarTokenRegistro(String)` → `Optional<GoogleUser>`; rejeita tokens sem `finalidade=registro`.
- **`SecurityFilter`** — lê cookie `accessToken`, chama `validarToken`; se presente, autentica com `UsuarioAutenticado` e suas authorities. Remove `shouldNotFilter` (o filtro é inofensivo sem cookie e precisa rodar em `/auth/me`), a dependência de `UsuarioRepository` e o `orElseThrow(UsuarioException)`.
- **`SecurityConfiguration`**
  - `oauth2Login`: `authorizationEndpoint` com o repositório em cookie e um `DefaultOAuth2AuthorizationRequestResolver` com `OAuth2AuthorizationRequestCustomizers.withPkce()`; `successHandler`/`failureHandler` do BFF.
  - `exceptionHandling.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED))` — sem isso o `oauth2Login` redirecionaria requisições não autenticadas para o Google. Requisições sem autenticação passam a receber **401** (hoje recebem 403).
  - Regras: `/auth/me` autenticado; `/auth/**`, `/oauth2/**`, `/login/oauth2/**`, `/error`, `/enderecos/cep/**` públicos; resto autenticado. (`/error` liberado para que erros reais — 400/404/409/500 — não virem 403.)
  - Sessão continua `STATELESS`.
- **`LoginService`**
  - Remove `solicitarOtp`, `login` e as dependências de `OtpService` e `ResendService`.
  - `loginGoogle(GoogleUser)` → `LoginResult(String accessToken, String registroToken)` — exatamente um dos dois é não-nulo.
  - `dadosRegistro(String registroToken)` → `RegistroPendenteDto`.
  - `register(RegisterRequestDto, MultipartFile, String registroToken)` → `RegisterResult(String accessToken, Object corpo)` onde corpo é `UserResponseDto` ou `ChefeResponseDto`.
  - Checagem de duplicidade consulta as duas tabelas.
  - `uploadImg` permanece como está (extração fica para a refatoração).
- **`AuthController`**
  - `GET /auth/register`, `POST /auth/register` (ambos com `@CookieValue(name="registroToken", required=false)`), `GET /auth/me`, `POST /auth/logout`.
  - Remove `/auth/login` e `/auth/request-otp`.
- **`LoginResult`** → `(String accessToken, String registroToken)`.
- **`RegisterRequestDto`** — remove o campo `email`.
- **`UsuarioMapper` / `ChefeMapper`** — `toEntity`/`toModel` ignoram `email` (setado pelo serviço).
- **`pom.xml`** — adiciona `spring-boot-starter-security-oauth2-client` e, em teste, `spring-boot-starter-security-test`; remove `spring-boot-starter-data-redis`.
- **`application-test.yml`** — remove `resend`; adiciona registro Google fictício, `app.front-url`, `auth.cookie.secure: false`.
- **`.github/workflows/rotinaCi.yml`** — remove `RESEND_API_KEY`.

### Removidos

`Security/otp/OtpService`, `Config/Infra/ResendService`, `Config/Infra/WebclientResendConfig`, `Dtos/EmailRequest`, `Dtos/SolicitarOtpDto`, `Dtos/OtpResponseDto`, `Dtos/otpData`, `Dtos/DadosLoginDto`, `Dtos/LoginResponseDto`, `Excepitons/OtpException`.

## Configuração

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,profile,email
app:
  front-url: ${FRONT_URL:http://localhost:5173}
auth:
  cookie:
    secure: ${AUTH_COOKIE_SECURE:true}
```

- O provider `google` já vem pré-configurado no Spring (`CommonOAuth2Provider`); redirect URI padrão `{baseUrl}/login/oauth2/code/google`.
- A chave de cifra do cookie de authorization request é derivada de `jwt.key` (mesmo segredo, salt fixo distinto) — sem nova variável.

**Pré-requisito externo:** criar OAuth Client ID tipo **Web** no Google Cloud Console com *Authorized redirect URI* `http://localhost:8080/login/oauth2/code/google` (e a URL de produção da API).

**Restrição de deploy:** como os cookies são `SameSite=Lax`, front e API precisam ser *same-site* (mesmo domínio registrável, ex.: `app.dominio.com` e `api.dominio.com`). `localhost:5173` e `localhost:8080` atendem.

## Erros

| Situação | Resultado |
|---|---|
| Qualquer falha no fluxo OAuth (state, code, ID token, usuário negou) | redirect `{front}/login?erro=google` |
| `email_verified` falso | redirect `{front}/login?erro=google` |
| `registroToken` ausente/inválido/expirado em `/auth/register` | 401 |
| E-mail já cadastrado em `usuarios` ou `chefe` | 409 |
| Rota protegida sem `accessToken` válido | 401 |

Sem `@ControllerAdvice` global neste escopo.

## Testes (TDD)

- **`CookieOAuth2AuthorizationRequestRepositoryTest`** — round-trip save/load; `remove` devolve o request e expira o cookie; cookie adulterado (1 byte trocado) → `null`; sem cookie → `null`.
- **`TokenServiceTest`** — round-trip de acesso e de registro; token de registro rejeitado por `validarToken` e vice-versa; token adulterado e expirado rejeitados.
- **`LoginServiceTest`** — cliente existente → accessToken com `CLIENTE`; chef existente → `CHEFE`; e-mail novo → registroToken; `dadosRegistro`; register com token válido cria e loga; register com e-mail existente → 409; register sem token → 401; foto do Google usada quando não há upload.
- **`GoogleLoginSuccessHandlerTest`** — conta existente → cookie `accessToken` + redirect `/`; nova → cookie `registroToken` + redirect `/cadastro`; `email_verified=false` → redirect de erro sem cookies.
- **`AuthControllerTest`** — adaptado: `GET /auth/register`, `POST /auth/register` grava/apaga cookies, `GET /auth/me`, logout expira os dois cookies.
- **`SecurityFilterTest`** — cookie válido → principal `UsuarioAutenticado` com email e role; cookie inválido → sem autenticação, sem exceção.
- **`OAuth2FlowIntegrationTest`** (MockMvc, perfil test) — `GET /oauth2/authorization/google` → 302 para `accounts.google.com` com `state`, `code_challenge` e `code_challenge_method=S256`, grava cookie `oauth2_auth_request` e **não** cria `JSESSIONID`; rota protegida sem cookie → 401.
- **`ChefeApiApplicationTests.contextLoads`** — continua passando sem Redis.

## Impacto no front

- Botão "Entrar com Google" navega para `{API}/oauth2/authorization/google` (navegação, não `fetch`).
- Rotas `/cadastro` (lê `GET /auth/register`, envia `POST /auth/register` sem `email`) e `/login?erro=google`.
- Estado de login via `GET /auth/me` (401 = deslogado).
- Todas as chamadas com `credentials: 'include'`.

## Fora de escopo

Handler global de erros, autorização por papel nas rotas (`hasRole`), checagem de dono nos endpoints, CORS (PATCH e origens configuráveis), CSRF, renomeação de pacotes/classes, extração do upload do Cloudinary, unificação de identidade — tratados na revisão/refatoração (`revisao-engenharia.md`).
