# Pagamento com retenção, reembolso e repasse ao chef — Design

## Objetivo

Dar ao cliente um motivo para reservar e pagar pelo ChefHome em vez de mandar Pix direto ao chef: o pagamento é feito na plataforma, fica retido até o serviço acontecer, volta para o cliente pelas regras de cancelamento e só então é repassado ao chef, com uma garantia de 48h depois do jantar. Hoje o README diz que o app "registra e avisa, sem multa nem reembolso"; este spec muda isso. Depende de `2026-10-31-menus-pedido-detalhado-design.md` (valor por menu, um chef por agendamento). Prepara `comissão por origem` (spec seguinte), que substitui a taxa fixa daqui.

## Decisões (aprovadas)

- **Interface de gateway com implementação simulada primeiro.** Toda a lógica (cobrança, retenção, liberação, reembolso, repasse) fica no ChefHome, testada contra um `GatewaySimulado`. O adaptador de um provedor real entra depois, sem mexer nas regras. Motivo: o split pronto do Mercado Pago manda o dinheiro direto ao vendedor e não documenta controle da data de liberação; retenção real pela plataforma é custódia de valores de terceiros e exige CNPJ e análise regulatória antes de produção.
- **Valor integral, depois que o chef aceita:** o agendamento vai para `AGUARDANDO_PAGAMENTO` e o cliente tem até 24h para pagar.
- **Reembolso escalonado** quando o cliente cancela; 100% sempre que o chef desiste.
- **Parcelamento de 1 a 12 vezes** agora; **conta dividida** fica para um spec próprio.
- **Garantia de 48h:** depois da conclusão, o repasse fica retido 48h; uma denúncia do cliente sobre o agendamento nesse prazo suspende o repasse até a moderação decidir.
- **Taxa da plataforma fixa e configurável** (padrão 15%), descontada do valor que fica com o chef, até o spec de comissão por origem.

## Regras

### Ciclo do agendamento (mudanças)

`StatusAgendamento` ganha `AGUARDANDO_PAGAMENTO`.

| De | Ação | Quem | Regra | Vai para |
|---|---|---|---|---|
| `PENDENTE` | `PATCH /confirmar` (aceitar) | chef | regras atuais de conflito, agenda e cidade | `AGUARDANDO_PAGAMENTO`, com `pagamentoAte` |
| `AGUARDANDO_PAGAMENTO` | pagamento aprovado | sistema (gateway) | antes de `pagamentoAte` | `CONFIRMADO` |
| `AGUARDANDO_PAGAMENTO` | (rotina) | sistema | `agora ≥ pagamentoAte` | `EXPIRADO` |
| `AGUARDANDO_PAGAMENTO` | `PATCH /cancelar` | cliente | antes do início | `CANCELADO` (nada a reembolsar) |
| `AGUARDANDO_PAGAMENTO` | `PATCH /desistir` | chef | antes do início | `CANCELADO` (nada a reembolsar) |
| `CONFIRMADO` | `PATCH /cancelar` | cliente | **antes do início** (cai a regra de 24h) | `CANCELADO` + reembolso pela tabela |
| `CONFIRMADO` | `PATCH /desistir` | chef | antes do início | `CANCELADO` + reembolso de 100% |
| `CONFIRMADO` | `PATCH /concluir` ou rotina | chef ou sistema | regras atuais | `CONCLUIDO` + repasse `RETIDO` por 48h |

- `pagamentoAte = min(aceite + 24h, início − 2h)`. Se o chef aceita com menos de 2h para o início, `pagamentoAte` é o próprio momento do aceite mais 15 minutos, limitado ao início (o cliente ainda consegue pagar na hora).
- **Horário reservado:** `AGUARDANDO_PAGAMENTO` conta como ocupado em toda checagem de conflito (inclusive com a folga do chef, spec de ferramentas do chef) e na agenda pública, exatamente como `CONFIRMADO`. A expiração libera o horário.
- Lembrete ao cliente 4h antes de `pagamentoAte` (`LEMBRETE_PAGAMENTO`), uma vez (`lembrete_pagamento_em`). Se o prazo total for menor que 4h, não há lembrete.
- Notificações novas: `AGUARDANDO_PAGAMENTO` (para o cliente, no aceite), `PAGAMENTO_APROVADO` (para os dois), `PAGAMENTO_RECUSADO` (cliente), `AGENDAMENTO_EXPIRADO` passa a cobrir também a falta de pagamento (com o motivo), `REEMBOLSO_REALIZADO` (cliente), `REPASSE_LIBERADO` e `REPASSE_SUSPENSO` (chef).
- Lembretes do spec de ferramentas do chef continuam valendo só para `CONFIRMADO`.

### Cobrança

- `POST /agendamentos/{id}/pagamento` `{ "parcelas": 3 }` (CLIENTE dono, agendamento em `AGUARDANDO_PAGAMENTO`, antes de `pagamentoAte`): cria a cobrança do `valorTotal` no gateway e devolve **201** `{ pagamentoId, status, valor, parcelas, checkout }` (`checkout` é o que o front precisa para concluir no provedor; no simulado, só o id).
- `parcelas` de 1 a 12 (fora → **400**). Juros do parcelamento, se houver, são do provedor e não mudam o `valorTotal` do agendamento.
- Uma cobrança ativa por agendamento: chamar de novo com cobrança `CRIADA` devolve a mesma (**200**); com a anterior `RECUSADA`, cria uma nova (o cliente tenta outro cartão).
- Fora do status ou do prazo → **409**.
- `GET /agendamentos/{id}/pagamento` (cliente dono ou chef do agendamento): `{ status, valor, parcelas, aprovadoEm, reembolsos: [{ valor, motivo, criadoEm }], repasse: { status, liberarEm } }`; o chef vê o repasse, o cliente não.

### Retorno do gateway

- Real: `POST /webhooks/pagamentos/{gateway}` (público, autenticado pela assinatura do provedor; inválida → **401**). Simulado em desenvolvimento: `POST /dev/pagamentos/{id}/aprovar` e `POST /dev/pagamentos/{id}/recusar` (só existem com o gateway simulado ativo).
- Os dois chamam o mesmo `PagamentoService.processarRetorno(gatewayId, status)`, **idempotente**: o mesmo aprovado duas vezes confirma uma vez só.
- Aprovado com o agendamento ainda em `AGUARDANDO_PAGAMENTO` → `CONFIRMADO`. Aprovado **depois** da expiração ou do cancelamento (corrida) → reembolso automático de 100% (`motivo = PAGAMENTO_APOS_EXPIRACAO`), agendamento não volta.
- Recusado → cobrança `RECUSADA`, agendamento continua em `AGUARDANDO_PAGAMENTO` até o prazo.

### Reembolso

| Quem cancela | Antecedência (até o início) | Reembolso ao cliente | Fica para repasse ao chef |
|---|---|---|---|
| Cliente | 7 dias (168h) ou mais | 100% | 0 |
| Cliente | menos de 168h e 48h ou mais | 50% | 50% |
| Cliente | menos de 48h | 0 | 100% |
| Chef desiste | qualquer | 100% | 0 |
| Moderação (garantia) | — | o que a moderação decidir: 100% | 0 |

- A antecedência é medida no momento do `PATCH /cancelar`, com o relógio do servidor (`RelogioConfig`).
- Valores com 2 casas, `HALF_UP`; o reembolso arredonda a favor do cliente quando a divisão não fecha (50% de R$ 100,01 → R$ 50,01 ao cliente).
- O reembolso é pedido ao gateway na mesma operação do cancelamento (chave de idempotência = agendamento + motivo). Falha do gateway → o agendamento é cancelado mesmo assim e o reembolso fica `PENDENTE` para a rotina tentar de novo (máximo de 10 tentativas, com intervalo crescente; depois `FALHOU` e entra na fila da moderação).
- Quando sobra valor para o chef (50% ou 100%), é criado um repasse `RETIDO` com `liberarEm = agora + 48h` (a garantia vale também aqui).
- A resposta do `PATCH /cancelar` passa a trazer `{ reembolso: { valor, percentual } }` e a notificação ao chef cita o valor que ele vai receber.

### Repasse ao chef

- Criado na conclusão (chef ou rotina) com o valor pago, ou no cancelamento quando sobra valor para o chef.
- `valorBruto` = parte do pagamento que ficou com o chef; `taxa = valorBruto × taxaPercentual` (2 casas, `HALF_UP`); `valorLiquido = valorBruto − taxa`. `taxaPercentual` vem de `chefhome.pagamento.taxa-percentual` (padrão 15, de 0 a 50) e é **gravada** no repasse (mudar a configuração não altera repasses existentes).
- Status: `RETIDO` → `LIBERADO` (rotina, quando `agora ≥ liberarEm`, chama `repassar` no gateway) | `SUSPENSO` | `CANCELADO` (moderação reembolsou).
- **Garantia:** uma denúncia do cliente sobre o agendamento (`POST /denuncias` com o `agendamentoId`, qualquer motivo, regras atuais) criada enquanto o repasse está `RETIDO` muda o repasse para `SUSPENSO` e notifica o chef. Denúncia depois da liberação não mexe no repasse (segue o fluxo normal de moderação).
- Falha no `repassar` → continua `RETIDO` e a rotina tenta de novo (mesma política do reembolso).
- `GET /chefe/me/repasses?status=&page=&size=` (CHEFE): lista com agendamento, valores e datas, mais `{ totalRetido, totalLiberado }` do chef.

### Moderação

- `GET /moderacao/repasses-suspensos` (com a denúncia e o agendamento) e `GET /moderacao/reembolsos-falhos`.
- `PATCH /moderacao/repasses/{id}/liberar`: `SUSPENSO` → `LIBERADO` (repassa na hora); a denúncia segue o fluxo normal dela.
- `PATCH /moderacao/repasses/{id}/reembolsar`: `SUSPENSO` → `CANCELADO` e reembolso de 100% do valor pago ao cliente (`motivo = GARANTIA`).
- `PATCH /moderacao/reembolsos/{id}/tentar-novamente`.
- Todas entram no histórico `GET /moderacao/acoes` (`TipoAcaoModeracao` ganha `REPASSE_LIBERADO`, `REPASSE_REEMBOLSADO`, `REEMBOLSO_REPROCESSADO`).

### Gateway

```java
public interface GatewayPagamento {
    CobrancaCriada criarCobranca(NovaCobranca cobranca);      // valor, parcelas, descrição, chave de idempotência
    StatusCobranca consultar(String gatewayId);
    ResultadoOperacao reembolsar(String gatewayId, BigDecimal valor, String chave);
    ResultadoOperacao repassar(UUID chefeId, BigDecimal valor, String chave);
}
```

- `GatewaySimulado`: guarda as cobranças em memória; `criarCobranca` devolve `CRIADA`; aprovação e recusa pelas rotas `/dev`; `reembolsar` e `repassar` sempre aprovam, com um modo de teste que falha nas N primeiras chamadas.
- Ativação: perfis de desenvolvimento e teste usam o simulado; em `prod`, a API **não sobe** sem gateway real configurado, a não ser com `CHEFHOME_PAGAMENTO_SIMULADO=true` (demo de portfólio; o README avisa que nenhum dinheiro real circula).
- Chaves do provedor real nunca em log; `.env.example` e `application-prod.yml` só com `${VAR}`.

## Componentes

- **Domínio:** `Pagamento` (agendamento, gatewayId, valor, parcelas, status `CRIADA`/`APROVADA`/`RECUSADA`, chave, criadoEm, aprovadoEm), `Reembolso` (pagamento, valor, percentual, motivo `CANCELAMENTO_CLIENTE`/`DESISTENCIA_CHEF`/`GARANTIA`/`PAGAMENTO_APOS_EXPIRACAO`, status `PENDENTE`/`REALIZADO`/`FALHOU`, tentativas, chave), `Repasse` (agendamento, chefe, valorBruto, taxaPercentual, taxa, valorLiquido, status, liberarEm, liberadoEm, chave).
- **`PagamentoService`:** cobrança, retorno idempotente, reembolso pela tabela (`PoliticaReembolso`, classe pura e testável), criação de repasse.
- **`RepasseService`:** liberação, suspensão por denúncia, ações da moderação.
- **`PagamentoRotina`:** expira `AGUARDANDO_PAGAMENTO` vencidos, lembrete de pagamento, libera repasses, refaz reembolsos e repasses pendentes.
- **`Agendamento`:** novas transições (`aguardarPagamento`, `confirmarPagamento`, cancelar e desistir em `AGUARDANDO_PAGAMENTO`), `pagamentoAte`.
- **`DenunciaService`:** ao criar, suspende o repasse `RETIDO` do agendamento.
- Trava: cobrança, retorno do gateway, cancelamento e desistência travam a linha do agendamento (`SELECT … FOR UPDATE`), para que pagamento e cancelamento simultâneos tenham um único resultado.

## Dados (V17)

- `agendamento`: `pagamento_ate timestamp(6)`, `lembrete_pagamento_em timestamp(6)`; status aceita `AGUARDANDO_PAGAMENTO`.
- `pagamento`: `id`, `agendamento_id` (FK), `gateway_id varchar(100)`, `valor numeric(10,2)`, `parcelas int`, `status varchar`, `chave_idempotencia varchar(100) UNIQUE`, `criado_em`, `aprovado_em`; índice único parcial: uma cobrança `CRIADA` ou `APROVADA` por agendamento.
- `reembolso`: `id`, `pagamento_id` (FK), `valor numeric(10,2)`, `percentual int`, `motivo varchar`, `status varchar`, `tentativas int`, `proxima_tentativa_em`, `chave_idempotencia UNIQUE`, `criado_em`, `realizado_em`.
- `repasse`: `id`, `agendamento_id` (FK, único), `chefe_id` (FK), `valor_bruto`, `taxa_percentual numeric(5,2)`, `taxa`, `valor_liquido` (`numeric(10,2)`), `status varchar`, `liberar_em`, `liberado_em`, `tentativas`, `chave_idempotencia UNIQUE`, `criado_em`.
- **Sem exclusão:** o `chefhome_app` só tem `SELECT`/`INSERT`/`UPDATE` nessas três tabelas (sem `DELETE`), verificado no `MigracoesPostgresTest`, como o histórico de moderação.
- **Conta apagada:** hoje o agendamento some com a conta do cliente. Com pagamento, agendamentos com `pagamento` ficam, com o cliente desvinculado (`user_id` nulo, endereço desvinculado, observações apagadas) para manter o rastro financeiro; `UsuarioService.deletar` passa a anonimizar em vez de apagar esses agendamentos. O README e a seção de LGPD documentam a base (obrigação legal e exercício regular de direitos).

## Erros

| Situação | Status |
|---|---|
| parcelas fora de 1 a 12 | 400 |
| assinatura inválida no webhook | 401 |
| pagar fora de `AGUARDANDO_PAGAMENTO` ou depois do prazo | 409 |
| ação de moderação em repasse fora de `SUSPENSO` | 409 |
| gateway indisponível ao criar cobrança | 503 (o cliente tenta de novo; o prazo continua correndo) |

## Testes

- **`PoliticaReembolsoTest`:** as três faixas e as bordas (exatamente 168h e 48h), desistência do chef, arredondamento a favor do cliente.
- **Ciclo:** aceite → aguardando com `pagamentoAte` nas três situações (24h, início − 2h, aceite em cima da hora); pagamento confirma; expiração libera a agenda; cancelar e desistir em aguardando sem reembolso; cancelar confirmado nas três faixas; desistir confirmado com 100%.
- **Agenda:** `AGUARDANDO_PAGAMENTO` bloqueia o horário para outro pedido e aparece na agenda pública; expirado libera.
- **Idempotência:** cobrança repetida, aprovado duas vezes, reembolso e repasse repetidos, webhook repetido.
- **Corrida (Postgres):** aprovação e cancelamento simultâneos; aprovação depois da expiração gera reembolso de 100%.
- **Repasse:** criado na conclusão e no cancelamento com saldo para o chef; taxa gravada; liberado em 48h pela rotina; suspenso por denúncia dentro do prazo e intocado depois; moderação libera e reembolsa; falha do gateway com nova tentativa.
- **Moderação:** filas, ações e histórico.
- **Conta apagada:** agendamento com pagamento anonimizado, sem pagamento apagado como hoje; registros financeiros intactos.
- **Privilégios:** `chefhome_app` sem `DELETE` nas tabelas financeiras (`MigracoesPostgresTest`, com a V17).
- **Inicialização:** `prod` sem gateway real e sem `CHEFHOME_PAGAMENTO_SIMULADO` não sobe.

## README

Seções novas "Pagamento" (fluxo, prazo, parcelamento, demo simulada) e "Reembolso e repasse" (tabela, garantia de 48h); "Ciclo do agendamento" atualizado com `AGUARDANDO_PAGAMENTO`; remover "sem multa nem reembolso".

## Fora do escopo

- Conta dividida entre convidados.
- Adaptador de provedor real (Mercado Pago ou outro) e onboarding da conta do chef no provedor.
- Nota fiscal e obrigações fiscais.
- Cupom e desconto.
- Mudança de convidados após a confirmação com cobrança ou devolução de diferença.
- Comissão por origem do cliente (próximo spec).
- Chargeback e disputa aberta no cartão.
