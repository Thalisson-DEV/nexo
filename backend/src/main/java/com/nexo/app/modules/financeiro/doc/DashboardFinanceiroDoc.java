package com.nexo.app.modules.financeiro.doc;

import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroFilter;
import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

/**
 * Todos os recortes deste dashboard aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idFornecedor=a&idFornecedor=b}) ou separe os valores por vírgula
 * ({@code ?idFornecedor=a,b}). Valores de uma mesma dimensão são combinados com
 * OU; dimensões diferentes, com E.
 */
@Tag(name = "Financeiro - Dashboard",
        description = "Indicadores executivos de compras e pagamentos: volume e valor autorizado, "
                + "gargalos da fila de aprovação, vencimentos e conformidade documental")
@SecurityRequirement(name = "bearerAuth")
public interface DashboardFinanceiroDoc {

    @Operation(
            summary = "Dashboard executivo do financeiro",
            description = """
                    Consolida, em uma única chamada, quatro leituras do módulo financeiro. Compras e
                    pagamentos são somados por padrão — os dois consomem o mesmo orçamento e disputam a
                    mesma fila de aprovação; o parâmetro `tipo` restringe a um só domínio.

                    **Quanto**
                    - `resumo`: volume por status, pendentes, taxas de aprovação, rejeição e cancelamento,
                      solicitantes e fornecedores distintos e a fatia de pagamentos que veio de uma compra.
                      O denominador das taxas de decisão é `decididas` (aprovadas mais rejeitadas): contar
                      rascunhos junto faria a taxa cair sempre que alguém abrisse pedidos novos.
                    - `valores`: dinheiro em cada estágio. O **corte na aprovação** é a diferença entre o
                      que se pediu e o que se autorizou, apenas nas aprovadas — ele não se confunde com o
                      valor rejeitado, que é o pedido negado por inteiro. `valorVencido` e `valorAVencer`
                      olham só o que já foi aprovado e ainda não tem comprovante de pagamento anexado.

                    **Como flui**
                    - `funil`: conversão da coorte — abertas → submetidas → aprovadas. Uma aprovada conta
                      também como submetida, então a cascata só estreita da esquerda para a direita.
                      Rejeitadas e canceladas vêm marcadas com `vazamento: true` e ficam fora da cascata:
                      uma é decisão de quem autoriza, a outra é desistência de quem pediu.
                    - `serieTemporal`: evolução por DIA, MES ou ANO conforme `granularidade` — omitida, é
                      escolhida pelo tamanho do período (até 62 dias = DIA, até 3 anos = MES, acima = ANO).
                      Todos os baldes são devolvidos, inclusive os zerados.
                    - `ciclo`: dias de cada etapa com média, mediana e P90. O P90 é o número que serve para
                      prometer prazo; a média sozinha esconde o pedido esquecido. O tempo de preenchimento
                      (abertura → submissão) é medido separado do tempo de decisão de propósito.
                    - `envelhecimento`: backlog da fila por faixa de idade, contada desde a entrada na
                      **etapa atual**. Inclui o que foi aberto antes do período e continua parado —
                      backlog não respeita recorte — e ignora o filtro de status.
                    - `vencimentos`: régua do que já foi aprovado, tem data e ainda não tem comprovante.
                      A faixa `VENCIDO` vem primeiro porque não é o próximo balde, é o passivo estourado.

                    **Onde dói**
                    - `porBase`, `porCentroCusto`, `porObra`, `porFornecedor`, `porTipoDespesa`,
                      `porFormulario`, `porSolicitante`: volume e dinheiro lado a lado, ordenados por valor
                      aprovado. Cada linha traz também quantos documentos vieram do grupo e qual fatia das
                      aprovadas dele tem documento fiscal — é onde a despesa fica sem lastro.
                      A `chave` de cada linha é o que o drilldown devolve ao filtro; `id` só existe nas
                      dimensões que apontam para um cadastro.

                    **O que fazer**
                    - `conformidade`: o repositório documental visto de cima — recebidos no período, fila
                      de conferência, `coberturaFiscal` (percentual das aprovadas com nota, boleto ou
                      recibo), duplicados por hash de conteúdo e documentos avulsos sem vínculo.
                    - `qualidade`: solicitações sem fornecedor identificado, sem descrição, sem centro de
                      custo ou sem base; fornecedores ainda não homologados em uso; e aprovadas sem nenhum
                      documento. Registros com dimensão em branco ficam de fora dos rankings — estes
                      números existem para explicar a diferença.
                    - `prioridades`: pendências acionáveis com evidência, ação sugerida, `destino`
                      (SOLICITACOES ou DOCUMENTOS) e o `drilldown` que reproduz o conjunto na listagem.

                    O período recorta pela **data de abertura** da solicitação, com bordas inclusivas;
                    período padrão é o mês corrente. É quando a despesa foi proposta que define a qual mês
                    ela pertence, e essa data não se move quando o aprovador demora — o dashboard lê
                    coortes ("do que foi pedido em março, quanto foi autorizado"), não fluxo de caixa.
                    Todas as variações comparam com a janela anterior equivalente: para um mês de
                    calendário fechado, o mês anterior inteiro. Denominadores iguais a zero retornam 0.""")
    ResponseEntity<DashboardFinanceiroResponse> dashboard(@ParameterObject DashboardFinanceiroFilter filter);
}
