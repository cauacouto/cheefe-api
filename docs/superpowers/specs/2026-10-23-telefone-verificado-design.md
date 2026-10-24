# Telefone verificado por SMS (Twilio Verify) — Design

> **Status (2026-10-24): parado.** O Twilio foi retirado e nada exige telefone; ficaram a interface `VerificadorTelefone`, o `TelefoneService`, as rotas (503 sem provedor) e o schema da V12. O canal será decidido depois (provavelmente WhatsApp). Ver README "Telefone verificado (parado)".

## Objetivo

Dificultar a volta de quem foi banido com uma conta Google nova: um telefone verificado por conta, exigido antes de agir, e o telefone de quem teve o banimento confirmado não serve para outra conta. Continuação de "Nova conta depois do banimento" em `2026-10-22-avaliacoes-design.md`.

## Decisões (aprovadas)

- Provedor: **Twilio Verify** (gera, envia e confere o código, e limita tentativas). A API não gera nem guarda código.
- Exigido **antes de agir**, não no cadastro: a pessoa loga e navega; contas que já existem caem na regra na próxima ação.
- Clientes **e** chefs.
- Sem IP (ver spec das avaliações).

## Regras

### Formato

- Só celular do Brasil. Entrada aceita com ou sem `+55`, espaços, parênteses e hífen; normalizado para E.164 (`+55` + DDD + 9 dígitos começando por 9). Fora disso → **400** "Telefone inválido".

### Fluxo

1. `POST /telefone/codigo` `{ telefone }` → **202**. Pede ao Twilio o envio do SMS.
   - Telefone já verificado por **outra** conta (cliente ou chef) → **409** "Telefone já usado por outra conta".
   - Telefone banido → **403** "Telefone não pode ser usado".
   - Novo pedido da mesma conta em menos de **60 s** → **429** (guardado em memória por conta, como limite simples; o Twilio também limita).
2. `POST /telefone/verificar` `{ telefone, codigo }` → **200** `{ telefone, verificadoEm }`.
   - Código errado/expirado (o Twilio diz não) → **422** "Código inválido ou expirado".
   - Mesmas checagens de 409/403 de novo (corrida entre o pedido e a verificação); a unicidade também é garantida no banco.
   - Grava o telefone verificado na conta. Verificar outro número substitui o anterior.
- Quem: CLIENTE e CHEFE logados (moderador → 403). Conta bloqueada não chega aqui (plano 1); chef inativo → 403 (`exigirAtivo`).

### Onde é exigido

- Cliente: `POST /agendamentos`, `POST /conversas`, `POST /conversas/{id}/mensagens`, `POST /agendamentos/{id}/avaliacoes`.
- Chef: `POST /conversas/{id}/mensagens`, `POST /agendamentos/{id}/avaliacoes`, e só **aparece na busca, no destaque e nos pratos públicos** com telefone verificado (mesmo critério de visível: `ChefeRepository.VISIVEL` e `ChefeSpecification`), e não pode ser agendado sem ele (`validarChefes`).
- Sem telefone verificado → **403** com corpo ProblemDetail e a propriedade `"codigo": "TELEFONE_NAO_VERIFICADO"`, detalhe "Verifique seu telefone para continuar".
- O resto (ler, editar o que já existe, cancelar, recusar, confirmar, responder avaliação, denunciar) não exige: ninguém fica preso com agendamento em andamento.

### Banimento

- Ao **confirmar** um bloqueio automático (`ModeracaoService.confirmarBloqueio`) e ao **banir** um chef (`banir`), se a conta tem telefone verificado, grava em `telefone_banido` o **hash** `SHA-256(pepper + telefone E.164)` (hex). O pepper vem de `CHEFHOME_PEPPER_TELEFONE` (obrigatório no perfil `prod`; nos testes/local um valor fixo de desenvolvimento).
- `telefone_banido` não guarda o número nem o id da conta: fica mesmo depois de a conta ser apagada (o objetivo é impedir o recadastro). Reverter um bloqueio (`reverterBloqueio`) não grava nada; reativar chef (`reativar`) apaga o hash dele, se houver.
- Inserção idempotente (o mesmo hash duas vezes não é erro).

### LGPD

- O telefone verificado é dado da conta: some com ela (`UsuarioService.deletar` e o soft delete do chef zera o telefone verificado). Só o hash de banido fica, com base em legítimo interesse (prevenção de fraude) — documentar no README.
- O telefone de contato livre do cliente (`telefone`, `PUT /usuario/me`) continua como está, separado.

## Twilio

- Interface `VerificadorTelefone { void enviarCodigo(String e164); boolean conferir(String e164, String codigo); }`.
- `TwilioVerificadorTelefone`: `RestClient` (padrão do `ViaCepClient`), Basic Auth com `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN`; `POST https://verify.twilio.com/v2/Services/{TWILIO_VERIFY_SERVICE_SID}/Verifications` (`To`, `Channel=sms`) e `.../VerificationCheck` (`To`, `Code`; aprovado se `status == "approved"`). Timeout curto; erro do Twilio → **503** "Não foi possível enviar o SMS agora" (sem vazar a resposta do Twilio). As chaves nunca vão para log.
- `VerificadorTelefoneFalso`: ativo quando as chaves não estão configuradas e o perfil não é `prod`; loga "código de verificação para +55…: 000000" (número mascarado no log) e aceita só `000000`.
- Perfil `prod` sem as três variáveis → a API não sobe (falha na inicialização com mensagem clara).
- `.env.example` e `application-prod.yml` ganham as variáveis (só `${VAR}`, nunca valores).

## Dados (V12)

- `usuarios` e `chefe`: `telefone_verificado varchar(20)` (nulo), `telefone_verificado_em timestamp(6)`; índice único parcial por tabela onde não nulo.
- Unicidade entre as duas tabelas: checada no serviço (com trava/ordem que evite a corrida, ou aceitar a corrida rara documentando) — o único por tabela garante dentro de cada uma.
- `telefone_banido`: `hash varchar(64)` PK, `banido_em timestamp(6) not null`.
- Entidades com o mesmo schema (H2 dos testes nasce das entidades; `MigracoesPostgresTest` compara).

## Erros

| Situação | Status |
|---|---|
| telefone fora do formato | 400 |
| código errado/expirado | 422 |
| sem telefone verificado numa ação exigida | 403 `TELEFONE_NAO_VERIFICADO` |
| telefone banido | 403 |
| telefone de outra conta | 409 |
| pedido repetido em < 60 s | 429 |
| Twilio fora | 503 |

## Testes

- Fluxo completo com o falso; 409/403/429/422/400; a exigência em cada rota listada (403 com `codigo`); chef sem telefone some da busca/destaque/pratos e não é agendável; banimento confirmado grava hash e bloqueia o número para outra conta, inclusive depois de a conta banida ser apagada; reverter não grava; conta apagada libera o telefone (não banido); `TwilioVerificadorTelefone` contra um servidor HTTP falso (MockRestServiceServer ou equivalente), sem rede.
- Os dados de teste que hoje criam chefs/clientes que agem passam a ter telefone verificado (helper), senão as listagens e ações quebram.

## Fora do escopo

Verificação por voz/WhatsApp; telefone de fora do Brasil; trocar o login Google.
