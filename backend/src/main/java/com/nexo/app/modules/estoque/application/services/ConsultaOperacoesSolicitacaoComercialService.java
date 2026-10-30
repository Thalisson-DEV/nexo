package com.nexo.app.modules.estoque.application.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.estoque.application.dto.OperacaoSolicitacaoComercialResponse;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.EventoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Monta operacoes comerciais atuais e recupera correlacoes gravadas antes do vinculo direto. */
@Service
@RequiredArgsConstructor
public class ConsultaOperacoesSolicitacaoComercialService {

    private final MovimentacaoEstoqueRepository movimentacoes;
    private final EventoSolicitacaoMaterialRepository eventos;
    private final ObjectMapper objectMapper;

    public List<OperacaoSolicitacaoComercialResponse> listar(SolicitacaoMaterial solicitacao) {
        Map<UUID, MovimentacaoEstoque> movimentos = new LinkedHashMap<>();
        movimentacoes.findAllBySolicitacaoIdOrderByDataHoraAscIdAsc(solicitacao.getId())
                .forEach(movimento -> movimentos.put(movimento.getId(), movimento));

        Set<UUID> idsHistoricos = idsOperacoesHistoricas(solicitacao.getId());
        if (!idsHistoricos.isEmpty()) {
            movimentacoes.findAllByIdReferenciaOperacaoInOrderByDataHoraAscIdAsc(idsHistoricos)
                    .forEach(movimento -> movimentos.putIfAbsent(movimento.getId(), movimento));
        }

        Map<UUID, List<MovimentacaoEstoque>> porOperacao = new LinkedHashMap<>();
        movimentos.values().stream()
                .filter(movimento -> movimento.getTipo() == MovimentacaoEstoque.TipoMovimentacao.ENTREGA
                        || movimento.getTipo() == MovimentacaoEstoque.TipoMovimentacao.ESTORNO)
                .sorted(java.util.Comparator.comparing(MovimentacaoEstoque::getDataHora)
                        .thenComparing(MovimentacaoEstoque::getId))
                .forEach(movimento -> porOperacao
                        .computeIfAbsent(movimento.getIdReferenciaOperacao(), ignorado -> new ArrayList<>())
                        .add(movimento));

        UUID idEquipe = solicitacao.getIdEquipeSaque();
        return porOperacao.entrySet().stream().map(entry -> {
            MovimentacaoEstoque primeira = entry.getValue().getFirst();
            TipoOperacaoSolicitacao tipo = primeira.getTipo() == MovimentacaoEstoque.TipoMovimentacao.ESTORNO
                    ? TipoOperacaoSolicitacao.ESTORNO : TipoOperacaoSolicitacao.ENTREGA;
            return new OperacaoSolicitacaoComercialResponse(
                    entry.getKey(), tipo, idEquipe, solicitacao.getIdColaboradorSaque(),
                    solicitacao.isSaqueForaEquipe(),
                    primeira.getIdUsuarioOperador(), primeira.getDataHora(), null, null,
                    entry.getValue().stream().map(OperacaoSolicitacaoComercialResponse.Item::de).toList());
        }).toList();
    }

    private Set<UUID> idsOperacoesHistoricas(UUID idSolicitacao) {
        Set<UUID> ids = new LinkedHashSet<>();
        eventos.findAllByEscopoAndIdSolicitacaoOrderBySequenciaAsc(
                        EscopoEventoSolicitacao.SOLICITACAO, idSolicitacao)
                .forEach(evento -> {
                    try {
                        JsonNode id = objectMapper.readTree(evento.getPayloadCanonico())
                                .path("snapshots").path("idOperacao");
                        if (id.isTextual()) ids.add(UUID.fromString(id.asText()));
                    } catch (Exception ignored) {
                        // Um evento legado sem correlacao operacional continua valido na linha do tempo.
                    }
                });
        return ids;
    }
}
