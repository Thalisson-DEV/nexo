package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável por tarefas agendadas relacionadas a solicitações de material.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolicitacaoAgendamentoService {

    private final SolicitacaoMaterialRepository repository;
    private final GravadorEventoSolicitacaoService gravadorEvento;

    /**
     * Verifica periodicamente se existem solicitações com entrega parcial há mais de 72 horas não encerradas.
     * Caso existam, encerra o saldo pendente mantendo o status de entrega parcial.
     */
    @Scheduled(cron = "${ces.estoque.expiracao-parcial-cron:0 0 * * * *}") // Executa a cada hora
    @Transactional
    public void processarSolicitacoesParciaisExpiradas() {
        OffsetDateTime limite = OffsetDateTime.now().minusHours(72);
        
        List<SolicitacaoMaterial> expiradas = repository.findAllByStatusAndEncerradaEmIsNullAndDataEntregaBefore(
            SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL, 
            limite
        );

        if (!expiradas.isEmpty()) {
            log.info("Processando {} solicitações parciais expiradas (mais de 72h).", expiradas.size());
            expiradas.forEach(solicitacao -> {
                solicitacao.encerrarEntregaParcialExpirada();
                repository.save(solicitacao);
                String comando = solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO
                        ? "EXPIRAR_ENTREGA_PARCIAL_TECNICA"
                        : "EXPIRAR_ENTREGA_PARCIAL_COMERCIAL";
                gravadorEvento.registrarFilho(
                        solicitacao,
                        TipoEventoSolicitacao.EXPIRACAO_CONFIRMADA,
                        PayloadEventoSolicitacao.semAlteracoes(
                                comando,
                                Map.of("horas", 72)),
                        new ContextoRegistroEventoSolicitacao(
                                null,
                                "SISTEMA",
                                OrigemComandoTecnico.SYSTEM,
                                "Entrega parcial encerrada automaticamente apos 72 horas."));
                log.info("Solicitação {} ({}) encerrada por tempo de entrega parcial excedido.",
                        solicitacao.getId(), solicitacao.getContexto());
            });
        }
    }
}
