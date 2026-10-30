package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filtro da listagem geral de desvios — a visão do SESMT sobre toda a empresa, sem o
 * recorte pelo inspetor autenticado que restringe {@link MeuDesvioFilter}.
 *
 * <p>O desvio é a unidade da consulta, e não a inspeção que o originou: um mesmo
 * lançamento pode ter gerado cinco não-conformidades de gravidades e prazos diferentes,
 * e quem cobra tratamento precisa filtrar cada uma delas isoladamente.</p>
 *
 * <p>Todos os recortes aceitam múltipla seleção: repita o parâmetro
 * ({@code ?gravidade=ALTA&gravidade=CRITICA}) ou separe por vírgula
 * ({@code ?gravidade=ALTA,CRITICA}). Omitido, um filtro não restringe nada.</p>
 */
public record DesvioSegurancaFilter(
        List<StatusDesvio> status,

        @Parameter(description = "Atalho para tudo que ainda não foi resolvido, independente do status exato")
        Boolean apenasPendentes,

        List<GravidadeDesvio> gravidade,

        @Parameter(description = "Somente desvios com prazo estourado e ainda não resolvidos")
        Boolean apenasVencidos,

        @Parameter(description = "Usuário que lançou a inspeção de origem")
        List<UUID> idInspetor,

        @Parameter(description = "Colaboradores a quem o desvio foi atribuído")
        List<UUID> idColaborador,

        List<UUID> idFormulario,

        @Parameter(description = "Item do formulário (pergunta) que gerou o desvio")
        List<UUID> idPergunta,

        List<UUID> idProcesso,
        List<UUID> idProcessoInspetor,
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) das bases operacionais. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        List<UUID> idEquipe,
        List<UUID> idVeiculo,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataInspecaoDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataInspecaoAte,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate prazoDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate prazoAte,

        @Parameter(description = "Somente desvios sem prazo definido — a fila que ninguém cobra porque não vence")
        Boolean semPrazo,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataResolucaoDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataResolucaoAte,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por descrição ou ação corretiva")
        String buscaGlobal
) {

    public DesvioSegurancaFilter {
        status = SelecaoMultipla.normalizar(status);
        gravidade = SelecaoMultipla.normalizar(gravidade);
        idInspetor = SelecaoMultipla.normalizar(idInspetor);
        idColaborador = SelecaoMultipla.normalizar(idColaborador);
        idFormulario = SelecaoMultipla.normalizar(idFormulario);
        idPergunta = SelecaoMultipla.normalizar(idPergunta);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idProcessoInspetor = SelecaoMultipla.normalizar(idProcessoInspetor);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idEquipe = SelecaoMultipla.normalizar(idEquipe);
        idVeiculo = SelecaoMultipla.normalizar(idVeiculo);
    }

    public DesvioSegurancaFilter(
            List<StatusDesvio> status,
            Boolean apenasPendentes,
            List<GravidadeDesvio> gravidade,
            Boolean apenasVencidos,
            List<UUID> idInspetor,
            List<UUID> idColaborador,
            List<UUID> idFormulario,
            List<UUID> idPergunta,
            List<UUID> idProcesso,
            List<UUID> idBaseOperacional,
            List<UUID> idCentroCusto,
            List<UUID> idEquipe,
            List<UUID> idVeiculo,
            OffsetDateTime dataInspecaoDe,
            OffsetDateTime dataInspecaoAte,
            LocalDate prazoDe,
            LocalDate prazoAte,
            Boolean semPrazo,
            OffsetDateTime dataResolucaoDe,
            OffsetDateTime dataResolucaoAte,
            String buscaGlobal
    ) {
        this(status, apenasPendentes, gravidade, apenasVencidos, idInspetor, idColaborador, idFormulario, idPergunta,
                idProcesso, null, idBaseOperacional, idCentroCusto, idEquipe, idVeiculo, dataInspecaoDe, dataInspecaoAte,
                prazoDe, prazoAte, semPrazo, dataResolucaoDe, dataResolucaoAte, buscaGlobal);
    }

    public static DesvioSegurancaFilter vazio() {
        return new DesvioSegurancaFilter(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
