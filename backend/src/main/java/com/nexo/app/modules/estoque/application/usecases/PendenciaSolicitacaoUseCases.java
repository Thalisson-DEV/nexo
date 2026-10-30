package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.RegistrarPendenciaRequest;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendenciaSolicitacaoUseCases {
    private final SolicitacaoMaterialRepository solicitacoes;
    private final MotivoPendenciaUseCases motivos;

    @Transactional
    public void registrar(UUID idSolicitacao, RegistrarPendenciaRequest request) {
        SolicitacaoMaterial solicitacao = buscarPlanejada(idSolicitacao);
        if (request.itensPendencias() != null && !request.itensPendencias().isEmpty()) {
            MotivoPendencia primeiroMotivo = null;
            String primeiraJustificativa = null;
            for (com.nexo.app.modules.estoque.application.dto.ItemPendenciaRequest itemReq : request.itensPendencias()) {
                if (itemReq.idMotivoPendencia() == null) {
                    throw new InvarianteVioladaException("Motivo da pendência é obrigatório para cada item.");
                }
                MotivoPendencia motivo = motivos.buscar(itemReq.idMotivoPendencia());
                item(solicitacao, itemReq.idItem()).registrarPendencia(motivo, itemReq.justificativa());
                if (primeiroMotivo == null) {
                    primeiroMotivo = motivo;
                    primeiraJustificativa = itemReq.justificativa();
                }
            }
            if (primeiroMotivo != null) {
                solicitacao.registrarPendencia(primeiroMotivo, primeiraJustificativa);
            }
        } else {
            if (request.idMotivoPendencia() == null) {
                throw new InvarianteVioladaException("Motivo da pendência é obrigatório.");
            }
            MotivoPendencia motivo = motivos.buscar(request.idMotivoPendencia());
            if (request.idsItens() != null && !request.idsItens().isEmpty()) {
                for (UUID idItem : request.idsItens()) {
                    item(solicitacao, idItem).registrarPendencia(motivo, request.justificativa());
                }
                solicitacao.registrarPendencia(motivo, request.justificativa());
            } else if (request.idItem() != null) {
                item(solicitacao, request.idItem()).registrarPendencia(motivo, request.justificativa());
                solicitacao.registrarPendencia(motivo, request.justificativa());
            } else {
                solicitacao.registrarPendencia(motivo, request.justificativa());
                if (solicitacao.getItens() != null) {
                    for (ItemSolicitacao it : solicitacao.getItens()) {
                        it.registrarPendencia(motivo, request.justificativa());
                    }
                }
            }
        }
        solicitacoes.save(solicitacao);
    }

    @Transactional
    public void remover(UUID idSolicitacao, UUID idItem) {
        SolicitacaoMaterial solicitacao = buscarPlanejada(idSolicitacao);
        if (idItem == null) {
            solicitacao.removerPendencia();
            if (solicitacao.getItens() != null) {
                for (ItemSolicitacao it : solicitacao.getItens()) {
                    it.removerPendencia();
                }
            }
        } else {
            item(solicitacao, idItem).removerPendencia();
            boolean temItemComPendencia = solicitacao.getItens() != null
                    && solicitacao.getItens().stream().anyMatch(ItemSolicitacao::possuiPendencia);
            if (!temItemComPendencia) {
                solicitacao.removerPendencia();
            } else {
                ItemSolicitacao restante = solicitacao.getItens().stream()
                        .filter(ItemSolicitacao::possuiPendencia)
                        .findFirst()
                        .orElse(null);
                if (restante != null) {
                    solicitacao.registrarPendencia(restante.getMotivoPendencia(), restante.getJustificativaPendencia());
                }
            }
        }
        solicitacoes.save(solicitacao);
    }

    private SolicitacaoMaterial buscarPlanejada(UUID id) {
        SolicitacaoMaterial solicitacao = solicitacoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada."));
        if (solicitacao.estaEncerrada()
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.REJEITADO
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.CANCELADO
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE
                || (solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO
                    && (solicitacao.getIdObra() == null || solicitacao.getAlmoxarifadoAtendimento() == null))
                || (solicitacao.getContexto() == ContextoSolicitacaoMaterial.COMERCIAL
                    && solicitacao.getDataPrevistaRetirada() == null
                    && solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.PENDENTE)) {
            throw new InvarianteVioladaException("A pendência exige uma solicitação planejada ou parcialmente planejada e ainda em andamento.");
        }
        return solicitacao;
    }

    private ItemSolicitacao item(SolicitacaoMaterial solicitacao, UUID idItem) {
        return solicitacao.getItens().stream().filter(item -> item.getId().equals(idItem)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Material não pertence à solicitação."));
    }
}
