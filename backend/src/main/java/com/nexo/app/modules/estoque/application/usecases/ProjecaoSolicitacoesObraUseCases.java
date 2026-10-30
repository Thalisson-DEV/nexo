package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse;
import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse.OcorrenciaResponse;
import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse.SolicitacaoResumo;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consulta somente as solicitações técnicas da obra e agrupa corretivas/preventivas por ocorrência cadastrada. */
@Service
@RequiredArgsConstructor
public class ProjecaoSolicitacoesObraUseCases {

    private final SolicitacaoMaterialRepository repository;
    private final ObraApi obraApi;

    @Transactional(readOnly = true)
    public ProjecaoSolicitacoesObraResponse buscar(UUID idObra) {
        var obra = obraApi.buscarPorId(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra nao encontrada."));
        List<SolicitacaoMaterial> solicitacoes = repository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("idObra"), idObra),
                cb.equal(root.get("contexto"), ContextoSolicitacaoMaterial.TECNICO)));
        List<SolicitacaoResumo> resumos = solicitacoes.stream()
                .sorted(Comparator.comparing(SolicitacaoMaterial::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::resumo)
                .toList();

        var tipo = obra.tipoObra() == null ? null
                : com.nexo.app.modules.estoque.domain.TipoObraSnapshot.valueOf(obra.tipoObra().name());
        if (tipo != com.nexo.app.modules.estoque.domain.TipoObraSnapshot.CORRETIVA
                && tipo != com.nexo.app.modules.estoque.domain.TipoObraSnapshot.PREVENTIVA) {
            return new ProjecaoSolicitacoesObraResponse(idObra, tipo, List.of(), resumos);
        }

        Map<UUID, List<SolicitacaoMaterial>> porOcorrencia = new LinkedHashMap<>();
        solicitacoes.forEach(solicitacao -> porOcorrencia
                .computeIfAbsent(solicitacao.getIdOcorrencia(), ignored -> new java.util.ArrayList<>())
                .add(solicitacao));
        List<OcorrenciaResponse> ocorrencias = porOcorrencia.entrySet().stream()
                .sorted(Comparator.comparing(
                        (Map.Entry<UUID, List<SolicitacaoMaterial>> entry) ->
                                entry.getValue().getFirst().getOcorrencia(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(entry -> new OcorrenciaResponse(
                        entry.getKey(),
                        entry.getValue().getFirst().getOcorrencia(),
                        entry.getValue().stream().map(this::resumo).toList()))
                .toList();
        return new ProjecaoSolicitacoesObraResponse(idObra, tipo, ocorrencias, List.of());
    }

    private SolicitacaoResumo resumo(SolicitacaoMaterial solicitacao) {
        BigDecimal quantidade = solicitacao.getItens().stream()
                .map(item -> item.getQuantidade() == null ? BigDecimal.ZERO : item.getQuantidade())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendente = solicitacao.getItens().stream()
                .map(item -> item.getQuantidadePendente() == null ? BigDecimal.ZERO : item.getQuantidadePendente())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SolicitacaoResumo(solicitacao.getId(), solicitacao.getTipoTecnico(), solicitacao.getStatus(),
                solicitacao.getStatusSeparacao(), solicitacao.getIdEquipeSaque(),
                solicitacao.getDataPrevistaRetirada(), quantidade, pendente);
    }
}
