package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.infrastructure.persistence.DashboardSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.*;
import com.nexo.app.modules.rh.EquipeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;

@Service
@RequiredArgsConstructor
public class DashboardSolicitacaoUseCases {

    private final DashboardSolicitacaoRepository repository;
    private final EquipeApi equipeApi;

    @Transactional(readOnly = true)
    public DashboardSolicitacaoResponse obterMetricas(DashboardSolicitacaoFilter filter) {

        boolean filtrarPorEquipes = false;
        List<UUID> idsEquipes = List.of(UUID.randomUUID());

        if (filter.idBaseOperacional() != null || filter.idProcesso() != null) {
            filtrarPorEquipes = true;
            idsEquipes = equipeApi.buscarIdsPorFiltros(filter.idBaseOperacional(), filter.idProcesso());

            if (idsEquipes.isEmpty()) {
                return emptyResponse();
            }
        }

        String dataInicio = filter.dataInicio() != null ? filter.dataInicio().toString() : null;
        String dataFim = filter.dataFim() != null ? filter.dataFim().toString() : null;
        String idOrigem = filter.idEstoqueOrigem() != null ? filter.idEstoqueOrigem().toString() : null;
        String idDestino = filter.idEstoqueDestino() != null ? filter.idEstoqueDestino().toString() : null;

        DashboardSolicitacaoProjection projection = repository.obterMetricasDashboard(
                dataInicio, dataFim, idOrigem, idDestino, filtrarPorEquipes, idsEquipes
        );

        List<TopMaterialItem> topMateriais = repository.obterTopMateriais(
                dataInicio, dataFim, idOrigem, idDestino, filtrarPorEquipes, idsEquipes
        ).stream().map(p -> new TopMaterialItem(p.getCodigo(), p.getNome(), p.getTotalSolicitado())).collect(Collectors.toList());

        List<TopEstoqueItem> topEstoques = repository.obterTopEstoques(
                dataInicio, dataFim, idOrigem, idDestino, filtrarPorEquipes, idsEquipes
        ).stream().map(p -> new TopEstoqueItem(p.getNome(), p.getTotalSolicitacoes())).collect(Collectors.toList());

        List<TopMotivoItem> topMotivos = repository.obterTopMotivos(
                dataInicio, dataFim, idOrigem, idDestino, filtrarPorEquipes, idsEquipes
        ).stream().map(p -> new TopMotivoItem(p.getNome(), p.getTotalOcorrencias())).collect(Collectors.toList());

        List<SegmentoSolicitacaoTecnicaItem> segmentosTecnicos =
                filter.contexto() == ContextoSolicitacaoMaterial.COMERCIAL
                        ? List.of()
                        : repository.obterSegmentosTecnicos(
                                dataInicio,
                                dataFim,
                                filter.tipoTecnico() == null ? null : filter.tipoTecnico().getCodigo(),
                                filter.tipoObra() == null ? null : filter.tipoObra().name(),
                                filter.ocorrencia(),
                                id(filter.idObra()),
                                id(filter.idEquipe()),
                                filter.statusSeparacao() == null ? null : filter.statusSeparacao().name(),
                                id(filter.idAlmoxarifadoAtendimento()))
                        .stream()
                        .map(item -> new SegmentoSolicitacaoTecnicaItem(
                                item.getContexto(), item.getTipoTecnico(),
                                item.getTipoObra(), item.getOcorrencia(), item.getIdObra(),
                                item.getIdEquipe(), item.getStatusSeparacao(),
                                item.getIdAlmoxarifadoAtendimento(),
                                getOrDefault(item.getTotalSolicitacoes()),
                                getOrDefault(item.getTotalItens()),
                                item.getQuantidadeSolicitada() == null
                                        ? BigDecimal.ZERO : item.getQuantidadeSolicitada()))
                        .toList();

        return new DashboardSolicitacaoResponse(
                getOrDefault(projection.getTotalSolicitacoes()),
                getOrDefault(projection.getTotalPendentes()),
                getOrDefault(projection.getTotalAprovadas()),
                getOrDefault(projection.getTotalRejeitadas()),
                getOrDefault(projection.getTotalEntregues()),
                getOrDefault(projection.getTotalEntreguesParciais()),
                getOrDefault(projection.getTotalEstornados()),
                getOrDefault(projection.getTempoMedioAtendimentoHoras()),
                getOrDefault(projection.getTempoMedioAprovacaoHoras()),
                getOrDefault(projection.getMediaSolicitacoesPorDia()),
                getOrDefault(projection.getPercentualPendentes()),
                getOrDefault(projection.getPercentualAprovadas()),
                getOrDefault(projection.getPercentualRejeitadas()),
                getOrDefault(projection.getPercentualEntregues()),
                getOrDefault(projection.getPercentualEntreguesParciais()),
                getOrDefault(projection.getPercentualEstornados()),
                getOrDefault(projection.getIndiceRejeicao()),
                getOrDefault(projection.getIndiceSucessoEntrega()),
                getOrDefault(projection.getTotalItensSolicitados()),
                getOrDefault(projection.getMediaItensPorSolicitacao()),
                topMateriais,
                topEstoques,
                topMotivos,
                segmentosTecnicos
        );
    }

    private long getOrDefault(Long value) {
        return value != null ? value : 0L;
    }

    private Double getOrDefault(Double value) {
        return value != null ? value : 0.0;
    }

    private String id(UUID value) {
        return value == null ? null : value.toString();
    }

    private DashboardSolicitacaoResponse emptyResponse() {
        return new DashboardSolicitacaoResponse(0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, List.of(), List.of(), List.of(), List.of());
    }
}
