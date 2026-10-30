package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.GranularidadeDashboard;
import com.nexo.app.modules.financeiro.domain.GrauPrioridade;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDespesa;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro do dashboard financeiro. Quando {@code inicio}/{@code fim} são omitidos,
 * o período padrão é o mês corrente.
 *
 * <p>O período recorta pela <b>data de abertura</b> da solicitação, e não pela
 * data da decisão: é quando a despesa foi proposta que define a qual mês ela
 * pertence, e essa data não se move quando o aprovador demora a responder. A
 * consequência é que o dashboard lê coortes — "do que foi pedido em março, quanto
 * foi autorizado" — e não fluxo de caixa.</p>
 *
 * <p>Todos os recortes aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idFornecedor=a&idFornecedor=b}) ou separe por vírgula. Valores dentro
 * de uma mesma dimensão são combinados com OU; dimensões diferentes, com E.</p>
 */
public record DashboardFinanceiroFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime inicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime fim,

        @Parameter(description = "Restringe a um domínio. Omitido, soma compras e pagamentos.")
        TipoSolicitacao tipo,

        @Parameter(description = "Variante de formulário utilizada")
        List<UUID> idFormulario,

        List<UUID> idBase,

        @Parameter(description = "Setor / centro de custo (processo de negócio)")
        List<UUID> idProcesso,

        @Parameter(description = "Obra (contrato) à qual a despesa se vincula")
        List<UUID> idObra,

        List<UUID> idFornecedor,

        @Parameter(description = "Usuário que abriu a solicitação")
        List<UUID> idSolicitante,

        List<StatusSolicitacao> status,

        List<TipoDespesa> tipoDespesa,

        List<GrauPrioridade> prioridade,

        @Parameter(description = "Recorte da série temporal (DIA, MES ou ANO). "
                + "Omitido, é escolhido pelo tamanho do período.")
        GranularidadeDashboard granularidade
) {

    public DashboardFinanceiroFilter {
        idFormulario = SelecaoMultipla.normalizar(idFormulario);
        idBase = SelecaoMultipla.normalizar(idBase);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idObra = SelecaoMultipla.normalizar(idObra);
        idFornecedor = SelecaoMultipla.normalizar(idFornecedor);
        idSolicitante = SelecaoMultipla.normalizar(idSolicitante);
        status = SelecaoMultipla.normalizar(status);
        tipoDespesa = SelecaoMultipla.normalizar(tipoDespesa);
        prioridade = SelecaoMultipla.normalizar(prioridade);
    }

    /** Filtro sem nenhum recorte — usado quando a requisição não envia parâmetros. */
    public static DashboardFinanceiroFilter vazio() {
        return new DashboardFinanceiroFilter(null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** {@code true} quando o domínio pedido inclui compras. */
    public boolean incluiCompras() {
        return tipo == null || tipo == TipoSolicitacao.COMPRA;
    }

    /** {@code true} quando o domínio pedido inclui pagamentos. */
    public boolean incluiPagamentos() {
        return tipo == null || tipo == TipoSolicitacao.PAGAMENTO;
    }

    /**
     * O mesmo recorte, sem período e restrito ao que não teve desfecho — é o que o
     * backlog do dashboard carrega.
     *
     * <p>O filtro de status escolhido pelo usuário é deliberadamente descartado aqui:
     * backlog é, por definição, o que não foi resolvido, e respeitar uma seleção de
     * "APROVADO" esvaziaria a conta em vez de recortá-la. O período também sai, porque
     * um pedido aberto em janeiro e não decidido continua parado em março.</p>
     */
    public DashboardFinanceiroFilter apenasPendentes() {
        return new DashboardFinanceiroFilter(null, null, tipo, idFormulario, idBase, idProcesso, idObra,
                idFornecedor, idSolicitante,
                List.of(StatusSolicitacao.RASCUNHO, StatusSolicitacao.SOLICITADO),
                tipoDespesa, prioridade, granularidade);
    }
}
