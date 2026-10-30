package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.GranularidadeDashboard;
import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro do dashboard de descontos. Quando {@code inicio}/{@code fim} são omitidos,
 * o período padrão é o mês corrente.
 *
 * <p>O período recorta pela <b>data da ocorrência</b>, não pela data de lançamento:
 * é a data do fato gerador que a gestão usa para comparar meses, e ela não se move
 * quando alguém registra o termo com atraso.</p>
 *
 * <p>Todos os recortes aceitam múltipla seleção: repita o parâmetro
 * ({@code ?idMotivo=a&idMotivo=b}) ou separe por vírgula ({@code ?idMotivo=a,b}).
 * Valores dentro de uma mesma dimensão são combinados com OU; dimensões diferentes,
 * com E.</p>
 */
public record DashboardDescontoFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime inicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime fim,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime inicioCriacao,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime fimCriacao,

        List<UUID> idMotivo,

        @Parameter(description = "Tipo de equipamento dos itens lançados. Um desconto entra "
                + "quando ao menos um item dele é do tipo escolhido.")
        List<UUID> idTipoEquipamento,

        List<UUID> idColaborador,

        @Parameter(description = "Base operacional do colaborador descontado.")
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) da base do colaborador. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        @Parameter(description = "Função do colaborador descontado.")
        List<UUID> idFuncao,

        List<UUID> idUsuarioSolicitante,

        List<StatusDesconto> status,

        @Parameter(description = "Recorte da série temporal (DIA, MES ou ANO). "
                + "Omitido, é escolhido pelo tamanho do período.")
        GranularidadeDashboard granularidade
) {

    public DashboardDescontoFilter {
        idMotivo = SelecaoMultipla.normalizar(idMotivo);
        idTipoEquipamento = SelecaoMultipla.normalizar(idTipoEquipamento);
        idColaborador = SelecaoMultipla.normalizar(idColaborador);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idFuncao = SelecaoMultipla.normalizar(idFuncao);
        idUsuarioSolicitante = SelecaoMultipla.normalizar(idUsuarioSolicitante);
        status = SelecaoMultipla.normalizar(status);
    }

    /** Filtro sem nenhum recorte — usado quando a requisição não envia parâmetros. */
    public static DashboardDescontoFilter vazio() {
        return new DashboardDescontoFilter(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * {@code true} quando alguma dimensão do colaborador está selecionada. É o que
     * decide se o dashboard precisa resolver a estrutura organizacional antes de
     * montar as métricas.
     */
    public boolean recortaPorColaborador() {
        return !SelecaoMultipla.vazia(idBaseOperacional)
                || !SelecaoMultipla.vazia(idCentroCusto)
                || !SelecaoMultipla.vazia(idFuncao);
    }
}
