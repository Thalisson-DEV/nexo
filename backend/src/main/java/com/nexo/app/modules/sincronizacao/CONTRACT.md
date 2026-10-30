# Contrato de sincronização durável

## Recebimento

`POST /v1/sincronizacao/operacoes` é o cofre de recebimento durável para a fase
de transição. Ele recebe um snapshot completo e imutável; os adaptadores tipados
que aplicam TURNO/EXECUCAO/etc. às tabelas de negócio são usados pelo
reprocessamento administrativo. O recebimento apenas preserva o snapshot e abre
o caso; ele ainda não substitui as escritas operacionais de sucesso do mobile.
O endpoint rejeita a reutilizacao do UUID local com qualquer mudanca no envelope:
tipo, IDs, instante, payload ou metadados dos anexos.
`idOperacaoLocal` é o UUID criado pelo aparelho e é a chave de idempotência.
O servidor responde `201` (ou `200` quando já recebido) somente depois de
gravar a operação e todos os metadados enviados em uma transação.

```json
{
  "idOperacaoLocal":"uuid",
  "tipo":"TURNO|EXECUCAO|SOLICITACAO|TRANSFERENCIA|INSPECAO|ANEXO",
  "idRegistroLocal":"uuid",
  "instanteOrigem":"2026-09-25T10:00:00Z",
  "payload":{"...":"snapshot completo"},
  "anexos":[{"idLocal":"uuid","nome":"foto.jpg","contentType":"image/jpeg","sha256":"hex","conteudoBase64":"..."}]
}
```

Anexos tem limite de 15 MB por arquivo. O request JSON duravel aceita ate 32 MB
para acomodar a expansao Base64; cada conteudo e validado contra SHA-256.

Tipos fora da lista são rejeitados (`422`). O servidor não executa comandos,
URLs ou métodos informados pelo cliente. O snapshot é preservado como recebido.

`GET /v1/sincronizacao/operacoes/{idOperacaoLocal}` permite reconciliar timeout
e retorna `SALVO_NO_SERVIDOR`, `EM_TRATAMENTO`, `RESOLVIDO` ou `REJEITADO`.

## Backoffice

`GET /v1/sincronizacao/casos?page=&size=&status=&tipo=&busca=` lista casos.
`GET /v1/sincronizacao/casos/{id}` detalha original, tentativas, revisões e
anexos. `POST /{id}/atribuicao`, `POST /{id}/correcoes` e
`POST /{id}/reprocessamento` registra a tentativa com usuário e justificativa;
ele não marca o caso como resolvido enquanto um adaptador não confirmar commit.
O reprocessamento usa a última correção ou, sem correção, o original: casos que
não precisam de ajuste (ex.: nota lançada antes de a equipe abrir o turno) são
reenviados sem alterar nada.
Não há exclusão nem alteração do snapshot original.

Cada caso traz `diagnostico` (`codigo`, `titulo`, `explicacao`, `orientacao` e
`acao`: `REENVIAR`, `CORRIGIR`, `CONFERIR`, `AGUARDAR` ou `DESCARTAR`) traduzido
do último erro. Na listagem ele é o resumo, sem consultar outros módulos; no
detalhe ele usa turno e equipe reais. O detalhe traz também `referencias`, o nome
de cada cadastro citado no registro (equipe, turno, tipo de serviço, ações,
materiais, veículo) pela sua referência, e `resolvidoEm`.

Nota recusada por falta de turno aberto é reenviada sozinha quando a equipe abre
o turno do mesmo dia da nota (`TurnoAbertoEvent`); a falha de um reenvio fica
registrada no caso.

Permissões: `sincronizacao:read` e `sincronizacao:manage`. A atribuição,
correção e reprocessamento exigem `manage`; o recebimento e a consulta do
próprio aparelho exigem autenticação e escopo do usuário de origem.

POST /v1/sincronizacao/casos/{id}/descarte recebe {"justificativa":"..."} e encerra o caso e a operação como REJEITADO, sem executar o adaptador. A justificativa é retornada em justificativaUltima. Casos encerrados não aceitam nova atribuição, correção ou descarte.
