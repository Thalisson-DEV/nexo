package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.OrdenacaoAcompanhamentoMeta;
import com.nexo.app.modules.sst.domain.StatusMeta;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Filtro do acompanhamento de metas de inspeção.
 *
 * <p>Os recortes são aplicados sobre o colaborador alcançado pela meta e aceitam
 * múltipla seleção: repita o parâmetro ({@code ?idFuncao=a&idFuncao=b}) ou separe
 * por vírgula ({@code ?idFuncao=a,b}). Um único valor continua funcionando como antes.</p>
 */
public record AcompanhamentoMetaFilter(

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Data usada para localizar a janela de apuração. Padrão: hoje")
        LocalDate dataReferencia,

        List<UUID> idTipoInspecao,
        List<UUID> idFuncao,
        List<UUID> idColaborador,
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) da base operacional do colaborador. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        @Parameter(description = "Processo de negócio ao qual o colaborador pertence")
        List<UUID> idProcesso,

        @Parameter(description = "Restringe o resultado às situações informadas "
                + "(ATINGIDA, EM_ANDAMENTO, NAO_ATINGIDA)")
        List<StatusMeta> status,

        @Parameter(description = "Critério de ordenação da lista. Padrão: PERCENTUAL")
        OrdenacaoAcompanhamentoMeta ordenarPor,

        @Parameter(description = "Direção da ordenação (ASC ou DESC). Padrão: ASC")
        Sort.Direction direcao
) {

    public AcompanhamentoMetaFilter {
        idTipoInspecao = SelecaoMultipla.normalizar(idTipoInspecao);
        idFuncao = SelecaoMultipla.normalizar(idFuncao);
        idColaborador = SelecaoMultipla.normalizar(idColaborador);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        status = SelecaoMultipla.normalizar(status);
    }

    /** Filtro sem nenhum recorte — usado quando a requisição não envia parâmetros. */
    public static AcompanhamentoMetaFilter vazio() {
        return new AcompanhamentoMetaFilter(null, null, null, null, null, null, null, null, null, null);
    }

    public OrdenacaoAcompanhamentoMeta ordenacaoEfetiva() {
        return ordenarPor != null ? ordenarPor : OrdenacaoAcompanhamentoMeta.PERCENTUAL;
    }

    public Sort.Direction direcaoEfetiva() {
        return direcao != null ? direcao : Sort.Direction.ASC;
    }
}
