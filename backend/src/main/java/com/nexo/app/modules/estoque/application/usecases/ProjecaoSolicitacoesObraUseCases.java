package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse.ItemMovimentacaoResumo;
import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse.ItemSolicitacaoResumo;
import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse.MovimentacaoResumo;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import org.springframework.data.domain.PageRequest;
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

    /** Linhas de movimentação lidas por obra; acima disso a tela avisa e aponta para Movimentações. */
    private static final int LIMITE_MOVIMENTACOES = 500;

    private final SolicitacaoMaterialRepository repository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final EstoqueRepository estoqueRepository;
    private final ObraApi obraApi;

    @Transactional(readOnly = true)
    public ProjecaoSolicitacoesObraResponse buscar(UUID idObra) {
        var obra = obraApi.buscarPorId(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra nao encontrada."));
        List<SolicitacaoMaterial> solicitacoes = repository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("idObra"), idObra),
                cb.equal(root.get("contexto"), ContextoSolicitacaoMaterial.TECNICO)));
        List<SolicitacaoResumo> resumos = solicitacoes.stream()
                .sorted(Comparator.comparing(SolicitacaoMaterial::getDataSolicitacao,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::resumo)
                .toList();

        PageRequest pagina = PageRequest.of(0, LIMITE_MOVIMENTACOES);
        List<MovimentacaoEstoque> linhas = estoqueRepository.findByIdObra(idObra)
                .map(estoque -> movimentacaoRepository.findDaObraOuDoEstoque(idObra, estoque.getId(), pagina))
                .orElseGet(() -> movimentacaoRepository.findByIdObraOrderByDataHoraDesc(idObra, pagina));
        List<MovimentacaoResumo> movimentacoes = agruparPorOperacao(linhas);
        boolean truncadas = linhas.size() == LIMITE_MOVIMENTACOES;

        var tipo = obra.tipoObra() == null ? null
                : com.nexo.app.modules.estoque.domain.TipoObraSnapshot.valueOf(obra.tipoObra().name());
        if (tipo != com.nexo.app.modules.estoque.domain.TipoObraSnapshot.CORRETIVA
                && tipo != com.nexo.app.modules.estoque.domain.TipoObraSnapshot.PREVENTIVA) {
            return new ProjecaoSolicitacoesObraResponse(idObra, tipo, List.of(), resumos, movimentacoes, truncadas);
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
        return new ProjecaoSolicitacoesObraResponse(idObra, tipo, ocorrencias, List.of(), movimentacoes, truncadas);
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
                solicitacao.getDataPrevistaRetirada(), quantidade, pendente,
                solicitacao.getDataSolicitacao(),
                solicitacao.getAlmoxarifadoAtendimento() != null ? solicitacao.getAlmoxarifadoAtendimento().getNomeIdentificador() : null,
                solicitacao.getItens().stream().map(item -> itemResumo(solicitacao, item)).toList());
    }

    private ItemSolicitacaoResumo itemResumo(SolicitacaoMaterial solicitacao, ItemSolicitacao item) {
        boolean devolucao = solicitacao.getTipoTecnico() != null && solicitacao.getTipoTecnico().isDevolucao();
        Material material = item.getMaterial();
        return new ItemSolicitacaoResumo(
                material.getId(), material.getCodigo(), material.getDescricao(), material.getUnidadeMedida(),
                item.getQuantidade(),
                devolucao ? item.getQuantidadeDevolucaoLiquida() : item.getQuantidadeEntregaLiquida(),
                item.getQuantidadePendente());
    }

    private List<MovimentacaoResumo> agruparPorOperacao(List<MovimentacaoEstoque> linhas) {
        Map<UUID, List<MovimentacaoEstoque>> porOperacao = new LinkedHashMap<>();
        linhas.forEach(linha -> porOperacao
                .computeIfAbsent(linha.getIdReferenciaOperacao() != null ? linha.getIdReferenciaOperacao() : linha.getId(),
                        ignored -> new java.util.ArrayList<>())
                .add(linha));
        return porOperacao.entrySet().stream().map(operacao -> {
            MovimentacaoEstoque primeira = operacao.getValue().getFirst();
            return new MovimentacaoResumo(
                    operacao.getKey(), primeira.getTipo(), primeira.getTipoTecnico(), primeira.getDataHora(),
                    primeira.getEstoqueOrigem() != null ? primeira.getEstoqueOrigem().getNomeIdentificador() : null,
                    primeira.getEstoqueDestino() != null ? primeira.getEstoqueDestino().getNomeIdentificador() : null,
                    primeira.getSolicitacao() != null ? primeira.getSolicitacao().getId() : null,
                    operacao.getValue().stream().map(linha -> new ItemMovimentacaoResumo(
                            linha.getMaterial().getCodigo(), linha.getMaterial().getDescricao(), linha.getMaterial().getUnidadeMedida(),
                            linha.getQuantidade(), linha.getCondicao(), linha.getNumSerie())).toList());
        }).toList();
    }
}
