# Login com Google — Design

**Data:** 2026-10-20
**Status:** aprovado (conversa), aguardando revisão do spec
**Branch:** `feat/login-google`

## Objetivo

Substituir o login por OTP (código por e-mail via Resend + Redis) por login social com Google, de forma que **clientes e chefs** consigam autenticar, o cadastro fique preso a um e-mail verificado pelo Google e o restante da API continue autenticando pelo cookie `accessToken`.

## Decisões

| Decisão | Escolha |
|---|---|
| Quem conduz o OAuth | Front (Google Identity Services) obtém o ID token; API só valida (`POST /auth/google`). API continua stateless. |
| Identidade de chef | Mantém tabelas `usuarios` e `chefe` separadas; JWT ganha claim `tipo`. Um e-mail é cliente **ou** chef, nunca os dois. |
| Sessão | JWT próprio da API (HS256, 8h) em cookie HttpOnly `accessToken`, como hoje. |
| Validação no filtro | Principal montado só pelos claims do token, sem consulta ao banco. Conta inativada/deletada mantém acesso até o token expirar (máx. 8h) — aceito. |
| Cadastro | E-mail vem de um `registroToken` emitido pelo `/auth/google`, nunca do corpo da requisição. |
| OTP / Resend / Redis | Removidos por completo, sem fallback. |

## Fluxo

1. Front renderiza o botão do Google e recebe o `credential` (ID token).
2. `POST /auth/google` com `{ "idToken": "..." }`.
3. API valida o ID token (ver *GoogleTokenVerifier*) e obtém `email`, `nome`, `foto`.
4. API procura o e-mail em `usuarios`; se não achar, em `chefe`.
   - **Encontrado:** grava cookie `accessToken` (JWT de acesso) e responde `200 { cadastroPendente: false, tipo, email, nome, foto }`.
   - **Não encontrado:** grava cookie `registroToken` (JWT de registro, 15 min, `Path=/auth/register`) e responde `200 { cadastroPendente: true, tipo: null, email, nome, foto }`.
5. `POST /auth/register` (multipart: parte `dados` = `RegisterRequestDto` sem `email`; parte opcional `foto`).
   - Lê o cookie `registroToken`; ausente/inválido/expirado → **401**.
   - E-mail já existe em `usuarios` ou `chefe` → **409**.
   - Cria `CLIENTE` (`UserModel` + endereços) ou `CHEFE` (`ChefeModel`) com o e-mail do token.
   - Sem `foto` enviada: `imageUrl` = foto do Google (claim do token de registro).
   - Apaga `registroToken` (Max-Age=0), grava `accessToken` e responde **201** com o DTO criado.
6. `POST /auth/logout` apaga `accessToken` e `registroToken`.

## Componentes

### Novos

- **`Security/google/GoogleTokenVerifier`** — recebe um `JwtDecoder` (produção: `NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")`) e aplica validadores:
  - assinatura + `exp` (padrão do Nimbus);
  - `iss` ∈ { `accounts.google.com`, `https://accounts.google.com` };
  - `aud` contém `google.client-id`;
  - `email_verified == true`.
  Retorna `GoogleUser(email, nome, foto)`. Qualquer falha → `TokenGoogleInvalidoException` (`@ResponseStatus(UNAUTHORIZED)`).
- **`Security/google/GoogleUser`** — record `(String email, String nome, String foto)`.
- **`Security/UsuarioAutenticado`** — record `(UUID id, String email, TipoUsuario tipo)` implementando `UserDetails`: `getUsername()` = email, `getPassword()` = null, authorities = `ROLE_CLIENTE` ou `ROLE_CHEFE`. Corrige o NPE em `PratosController` (`@AuthenticationPrincipal UserDetails`) e o `authentication.getName()` em `UsuarioController`, sem alterá-los.
- **`Dtos/GoogleLoginRequest`** — record `(String idToken)`.
- **`Excepitons/TokenGoogleInvalidoException`** (401), **`Excepitons/RegistroTokenInvalidoException`** (401), **`Excepitons/EmailJaCadastradoException`** (409) — todas com `@ResponseStatus`. (O pacote mantém o nome atual; renomear fica para a refatoração.)

### Alterados

- **`TokenService`**
  - `gerarToken(UsuarioAutenticado)` → JWT com `iss=chefe-api`, `sub=email`, claims `id`, `tipo`, `finalidade=acesso`, expira em `Instant.now().plus(8h)`.
  - `validarToken(String)` → `Optional<UsuarioAutenticado>`; rejeita tokens sem `finalidade=acesso`.
  - `gerarTokenRegistro(GoogleUser)` → JWT com `sub=email`, claims `nome`, `foto`, `finalidade=registro`, expira em 15 min.
  - `validarTokenRegistro(String)` → `Optional<GoogleUser>`; rejeita tokens sem `finalidade=registro`.
- **`SecurityFilter`** — lê cookie `accessToken`, chama `validarToken`; se presente, autentica com `UsuarioAutenticado` e suas authorities. Remove a dependência de `UsuarioRepository` e o `orElseThrow(UsuarioException)`.
- **`LoginService`**
  - Remove `solicitarOtp`, `login`, dependências de `OtpService` e `ResendService`.
  - `loginGoogle(String idToken)` → `LoginResult(accessToken, registroToken, LoginResponseDto)`.
  - `register(RegisterRequestDto, MultipartFile, String registroToken)` → `RegisterResult(accessToken, Object corpo)` onde corpo é `UserResponseDto` ou `ChefeResponseDto`.
  - Checagem de duplicidade consulta as duas tabelas.
  - `uploadImg` permanece como está (extração fica para a refatoração).
- **`AuthController`**
  - `POST /auth/google`, `POST /auth/register` (lê `@CookieValue("registroToken", required=false)`), `POST /auth/logout`.
  - Remove `/auth/login` e `/auth/request-otp`.
  - Flag `secure` dos cookies vem de `auth.cookie.secure` (default `true`).
- **`LoginResponseDto`** → `(Boolean cadastroPendente, TipoUsuario tipo, String email, String nome, String foto)`.
- **`LoginResult`** → `(String accessToken, String registroToken, LoginResponseDto responseDto)`.
- **`RegisterRequestDto`** — remove o campo `email`.
- **`UsuarioMapper` / `ChefeMapper`** — `toEntity`/`toModel` ignoram `email` (setado pelo serviço).
- **`SecurityConfiguration`** — sem mudança de regras; `/auth/**` continua público.
- **`pom.xml`** — adiciona `spring-security-oauth2-jose`; remove `spring-boot-starter-data-redis`.
- **`application-test.yml`** — remove `resend`; adiciona `google.client-id: teste` e `auth.cookie.secure: false`.
- **`.github/workflows/rotinaCi.yml`** — remove `RESEND_API_KEY`.

### Removidos

`Security/otp/OtpService`, `Config/Infra/ResendService`, `Config/Infra/WebclientResendConfig`, `Dtos/EmailRequest`, `Dtos/SolicitarOtpDto`, `Dtos/OtpResponseDto`, `Dtos/otpData`, `Dtos/DadosLoginDto`, `Excepitons/OtpException`.

## Configuração

| Propriedade | Origem | Uso |
|---|---|---|
| `google.client-id` | env `GOOGLE_CLIENT_ID` | `aud` esperado no ID token |
| `auth.cookie.secure` | env, default `true` | flag `Secure` dos cookies (`false` em dev HTTP) |
| `jwt.key` | já existe | assinatura dos JWTs da API |

Pré-requisito externo: criar OAuth Client ID tipo **Web** no Google Cloud Console com origens JavaScript autorizadas `http://localhost:5173` e `http://localhost:3000` (e a origem de produção).

## Erros

| Situação | Status |
|---|---|
| ID token do Google inválido/expirado/aud ou iss errado/e-mail não verificado | 401 |
| `registroToken` ausente/inválido/expirado em `/auth/register` | 401 |
| E-mail já cadastrado em `usuarios` ou `chefe` | 409 |

Sem `@ControllerAdvice` global neste escopo.

## Testes (TDD)

- **`GoogleTokenVerifierTest`** — chave RSA gerada no teste, `NimbusJwtDecoder.withPublicKey`; casos: válido, `aud` errado, `iss` errado, expirado, `email_verified=false`.
- **`TokenServiceTest`** — round-trip de acesso e de registro; token de registro rejeitado por `validarToken` e vice-versa; token adulterado rejeitado.
- **`LoginServiceTest`** — cliente existente → `CLIENTE` + accessToken; chef existente → `CHEFE`; e-mail novo → `cadastroPendente` + registroToken; register com token válido cria e loga; register com e-mail existente → 409; register sem token → 401; foto do Google usada quando não há upload.
- **`AuthControllerTest`** — adaptado: cookies gravados/apagados com atributos corretos (`HttpOnly`, `Path`, `Max-Age`, `SameSite=Lax`, `Secure` conforme config).
- **`SecurityFilterTest`** — cookie válido → principal `UsuarioAutenticado` com email e role; cookie inválido → sem autenticação, sem exceção.
- **`ChefeApiApplicationTests.contextLoads`** — passa sem Redis.

## Impacto no front

- Integrar Google Identity Services e enviar `credential` para `POST /auth/google`.
- Remover `email` do formulário de cadastro.
- Ler `cadastroPendente` (antes `cadastroPedende`) e `tipo`.
- Todas as chamadas com `credentials: 'include'`.

## Fora de escopo

Handler global de erros, autorização por papel nas rotas (`hasRole`), checagem de dono nos endpoints, renomeação de pacotes/classes, extração do upload do Cloudinary, unificação de identidade — tratados na revisão/refatoração.
