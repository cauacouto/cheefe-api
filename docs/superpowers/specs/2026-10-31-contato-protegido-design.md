# Contato protegido (chat, avaliações e pratos) — Design

## Objetivo

Manter cliente e chef dentro do ChefHome: telefone, e-mail, @ de rede social e links não passam pelo chat, pelas avaliações nem pelos pratos. Hoje o `FiltroConteudo` só trata palavrão, ódio e ameaça, e qualquer contato sai livre, o que leva a conversa (e os próximos agendamentos) para o WhatsApp. Primeiro dos specs de diferenciação (seguem: menus e pedido detalhado, pagamento com retenção, comissão por origem, IA de pedido e menu).

## Decisões (aprovadas)

- Contatos ficam **sempre** ocultos, inclusive depois de o agendamento ser confirmado. O chat em tempo real já cobre o contato no dia do serviço, e o endereço está no agendamento. Liberar depois do pagamento é uma decisão do spec de pagamento, não deste.
- **Só mascara, sem consequência:** nada de bloqueio de conta nem fila de moderação por tentativa repetida. O original fica guardado para a moderação, como já acontece com palavrão.
- Detecção por **padrões no código** (componente novo `DetectorContato`), separada do `FiltroConteudo`: palavrão é lista de palavras, contato é padrão de dígitos, e-mail e links. Sem LLM nesta etapa.
- Chat e avaliações **mascaram**; pratos **recusam** (formulário: o chef corrige na hora).
- A verificação de telefone parada (`2026-10-23-telefone-verificado-design.md`) citava o WhatsApp como canal futuro. Ele pode servir para avisos, nunca como canal de conversa entre cliente e chef.

## Regras

### Onde vale

| Texto | Comportamento |
|---|---|
| Mensagem do chat (`POST /conversas/{id}/mensagens`, `PATCH /conversas/{id}/mensagens/{mid}`) | contato vira `***`; original em `textoOriginal` |
| Comentário de avaliação (`POST /agendamentos/{id}/avaliacoes`) | contato vira `***`; original em `comentarioOriginal` |
| Resposta do chef (`PUT /avaliacoes/{id}/resposta`) | contato vira `***`; original em `respostaOriginal` |
| Nome e descrição de prato (`POST /pratos`, `PUT /pratos/{id}`) | **400** `CONTATO_NAO_PERMITIDO`, nada é gravado |

Os campos `*Original` já existem e nada muda na regra de cada um: `textoOriginal` só é preenchido quando o texto exibido difere do enviado; `comentarioOriginal` e `respostaOriginal` são sempre preenchidos.

### O que é contato

A detecção roda sobre uma cópia normalizada do texto e mascara o trecho correspondente no texto **original** (mapa de posições entre as duas cópias).

**Normalização (só para detectar):** NFKC (dígitos de largura total e números circulados viram `0`–`9`); emoji de número (`1️⃣`: dígito + `U+FE0F` + `U+20E3`) vira o dígito; minúsculas; sem acento.

| Tipo | Regra | Exemplos que casam |
|---|---|---|
| Telefone e números longos | sequência com **8 ou mais dígitos**, permitindo entre os grupos até 2 separadores (espaço, `.`, `-`, `/`, `(`, `)`) e `+` no início. Sem limite superior: CPF e chave Pix numérica também são mascarados | `21 98888-7777`, `(24) 9 9999 8888`, `+5521988887777`, `219.888.877.77` |
| Número por extenso ou misturado | sequência de tokens separados por espaço ou pontuação, cada token sendo um grupo de dígitos ou uma palavra numérica (`zero`, `um`, `uma`, `dois`, `duas`, `tres`, `quatro`, `cinco`, `seis`, `meia`, `sete`, `oito`, `nove`), somando **8 ou mais dígitos** e com pelo menos uma palavra | `nove nove oito oito sete sete seis seis`, `21 nove 9888 7777` |
| E-mail | `local@dominio.tld`; disfarçado: `<token> arroba <token>` seguido de `ponto <token>` ou `.tld` (também `(at)` e `[at]`) | `chef@gmail.com`, `chef arroba gmail ponto com`, `chef (at) gmail.com` |
| @ de rede social | `@` seguido de 3 a 30 caracteres `[a-z0-9._]` (fora de e-mail); disfarçado: `arroba <token>` sozinho | `@chef.ana`, `arroba chefana` |
| Link ou domínio | `http://`, `https://`, `www.`; hosts `wa.me`, `api.whatsapp.com`, `instagram.com`, `facebook.com`, `t.me`, `tiktok.com`, `linktr.ee`; e qualquer `<token>.<tld>` com tld em `com`, `com.br`, `net`, `org`, `br`, `me`, `io`, `app`, `ee`, `link`, `bio` | `wa.me/5521...`, `instagram.com/chefana`, `linktr.ee/ana`, `meusite.com.br` |

**Não é contato** (checado antes, e o trecho é ignorado pelas regras acima):

- datas `d/m/aa`, `dd/mm/aaaa`, `dd-mm-aaaa`;
- horários `20:30`, `20h30`, `20h`;
- valores `R$ 1.500,00`, `1500 reais`;
- CEP no formato `00000-000` (o endereço do agendamento já é da plataforma);
- números com menos de 8 dígitos ("para 10 pessoas", "menu de 4 tempos").

**Máscara:** cada trecho detectado vira `***`. Todo trecho detectado tem pelo menos 4 caracteres (o menor é `@abc`), então o texto exibido nunca fica maior que o enviado e o limite das colunas continua valendo.

### Ordem dentro de `ModeracaoConteudoService.verificar`

1. `FiltroConteudo.analisar` sobre o texto enviado: ódio ou ameaça bloqueiam a conta e lançam `ConteudoBloqueadoException`, como hoje.
2. Ofensa já mascarada no texto exibido pelo passo 1.
3. `DetectorContato.mascarar` sobre o texto exibido do passo 2.

Moderador como autor segue a regra atual do passo 1.

## Componentes

### `DetectorContato` (novo, `service/`)

Puro, sem banco, testável sozinho.

```java
@Component
public class DetectorContato {
    public record Resultado(String texto, boolean contatoOculto) {}

    /** Devolve o texto com cada contato trocado por "***". */
    public Resultado mascarar(String texto);

    /** true se o texto tem algum contato (usado nos pratos). */
    public boolean contem(String texto);
}
```

Texto nulo ou em branco volta como veio, com `contatoOculto = false`.

### `ModeracaoConteudoService`

`verificar(String texto, UsuarioAutenticado autor)` passa a devolver `ConteudoVerificado(String texto, boolean contatoOculto)` em vez de `String`. Os três pontos que chamam (`ChatService.enviar`, `ChatService.editar`, `AvaliacaoService` em comentário e resposta) usam `.texto()`; o chat também usa `.contatoOculto()`.

A orientação atual continua: chamar **antes** de travar ou gravar a linha do autor.

### `PratosService`

Em criar e editar, `detectorContato.contem(nomePrato)` ou `contem(descricao)` → `ContatoNaoPermitidoException` (nova, filha de `NegocioException`), tratada no `GlobalExceptionHandler` como **400** ProblemDetail com `"codigo": "CONTATO_NAO_PERMITIDO"` e detalhe "Não é permitido colocar telefone, e-mail, rede social ou link no prato".

### Chat

- `Mensagem` ganha `contatoOculto` (`boolean`, padrão `false`), gravado em enviar e recalculado em editar.
- `MensagemDto` ganha `contatoOculto`, **true só para o autor** (`minha && mensagem.contatoOculto`). Vale para a resposta das rotas, para `GET /conversas/{id}/mensagens` e para o evento SSE `mensagem` / `mensagem_editada` (cada destinatário recebe o seu valor, como já acontece com `minha`).
- O front usa a flag para mostrar ao autor: "Por segurança, telefones, e-mails e redes sociais ficam ocultos no ChefHome."

## Dados (V14)

- `V14__contato_protegido.sql`: `ALTER TABLE mensagem ADD COLUMN contato_oculto boolean NOT NULL DEFAULT false;`
- Entidade `Mensagem` com o mesmo schema (o `MigracoesPostgresTest` compara).
- Avaliações e pratos não mudam de schema.

## Erros

| Situação | Status |
|---|---|
| contato no nome ou na descrição do prato | 400 `CONTATO_NAO_PERMITIDO` |
| contato no chat ou na avaliação | sem erro: mascarado |
| ódio ou ameaça (inalterado) | 403 e conta bloqueada |

## Testes

- **`DetectorContatoTest`** (tabela parametrizada):
  - casam: cada exemplo da tabela "O que é contato", mais disfarces com emoji de número, dígitos de largura total e números circulados;
  - não casam: datas, horários, valores, CEP, "para 10 pessoas", "menu de 4 tempos", "um jantar para dois", e-mail sem arroba nem ponto;
  - máscara: posições corretas com mais de um contato na mesma frase, texto exibido nunca maior que o original, texto nulo ou em branco.
- **`ModeracaoConteudoServiceIntegrationTest`**: ódio continua bloqueando antes da máscara de contato; ofensa e contato na mesma mensagem viram dois `***`.
- **Chat (integração):** enviar e editar mascaram e gravam `textoOriginal`; `contatoOculto` true para o autor e false para o outro participante, nas rotas e no evento SSE; editar removendo o contato zera a flag; a fila de denúncia de mensagem mostra o original.
- **Avaliações (integração):** comentário e resposta mascarados, originais gravados.
- **Pratos (integração):** criar e editar com contato no nome ou na descrição → 400 com `codigo`, nada gravado; texto sem contato segue normal.
- **`MigracoesPostgresTest`** cobre a V14.

## README

Na seção do chat e na de "Bloqueio automático": contatos (telefone, e-mail, @, links) viram `***` no chat e nas avaliações, o original fica só para a moderação, e a `MensagemDto` traz `contatoOculto` para o autor; pratos com contato → 400 `CONTATO_NAO_PERMITIDO`. Na seção "Telefone verificado (parado)", o WhatsApp deixa de ser citado como próximo canal de conversa: ele só pode servir para avisos.

## Fora do escopo

- Intenção sem o dado ("me chama no zap", "procura meu nome no insta").
- Contato escrito dentro de fotos de prato ou de perfil.
- Punição, fila de moderação ou métrica por tentativas repetidas.
- Liberação do contato depois do pagamento (decisão do spec de pagamento).
- Chave Pix aleatória (UUID) e chave Pix por e-mail fora do formato de e-mail.
