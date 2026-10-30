package com.nexo.app.modules.financeiro.application.usecases;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.financeiro.domain.DecisaoDesfeita;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraAprovadaEvent;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraDecisaoDesfeitaEvent;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraRejeitadaEvent;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraSubmetidaEvent;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Traduz uma solicitação no evento correspondente.
 *
 * <p>Existe porque compra e pagamento percorrem o mesmo ciclo com o mesmo conteúdo, e
 * a única diferença — o {@code tipo} — já é perguntada ao próprio agregado. Repetir a
 * montagem nos dois casos de uso faria com que um campo acrescentado a um dos eventos
 * ficasse pela metade no outro.</p>
 */
@Component
@RequiredArgsConstructor
class EventosSolicitacaoFinanceira {

    private final MontadorContextoRoteamentoFinanceiro montadorContexto;

    SolicitacaoFinanceiraSubmetidaEvent submetida(SolicitacaoBase solicitacao) {
        var contexto = montadorContexto.montar(solicitacao);
        return new SolicitacaoFinanceiraSubmetidaEvent(
                solicitacao.getId(),
                solicitacao.getTipoSolicitacao().name(),
                solicitacao.getNumero(),
                solicitacao.getNucleo().getValor(),
                solicitacao.getNucleo().getDescricao(),
                nomeDaPrioridade(solicitacao),
                solicitacao.getUsuarioSolicitanteId(),
                contexto.base(),
                contexto.uf(),
                contexto.frota(),
                contexto.obraCivil());
    }

    SolicitacaoFinanceiraAprovadaEvent aprovada(SolicitacaoBase solicitacao) {
        var contexto = montadorContexto.montar(solicitacao);
        return new SolicitacaoFinanceiraAprovadaEvent(
                solicitacao.getId(),
                solicitacao.getTipoSolicitacao().name(),
                solicitacao.getNumero(),
                solicitacao.getValorAprovado(),
                solicitacao.getNucleo().getDescricao(),
                solicitacao.getObservacaoDecisao(),
                solicitacao.getUsuarioSolicitanteId(),
                solicitacao.getUsuarioDecisaoId(),
                contexto.obraCivil());
    }

    SolicitacaoFinanceiraRejeitadaEvent rejeitada(SolicitacaoBase solicitacao) {
        return new SolicitacaoFinanceiraRejeitadaEvent(
                solicitacao.getId(),
                solicitacao.getTipoSolicitacao().name(),
                solicitacao.getNumero(),
                solicitacao.getNucleo().getValor(),
                solicitacao.getNucleo().getDescricao(),
                solicitacao.getMotivoRejeicao(),
                solicitacao.getUsuarioSolicitanteId(),
                solicitacao.getUsuarioDecisaoId());
    }

    SolicitacaoFinanceiraDecisaoDesfeitaEvent decisaoDesfeita(SolicitacaoBase solicitacao, DecisaoDesfeita anterior,
                                                              String motivo, UUID responsavelId) {
        return new SolicitacaoFinanceiraDecisaoDesfeitaEvent(
                solicitacao.getId(),
                solicitacao.getTipoSolicitacao().name(),
                solicitacao.getNumero(),
                solicitacao.getNucleo().getValor(),
                solicitacao.getNucleo().getDescricao(),
                anterior.eraAprovacao() ? "Aprovação" : "Rejeição",
                motivo == null ? null : motivo.trim(),
                solicitacao.getUsuarioSolicitanteId(),
                anterior.usuarioDecisaoAnterior(),
                responsavelId);
    }

    private static String nomeDaPrioridade(SolicitacaoBase solicitacao) {
        var prioridade = solicitacao.getNucleo().getPrioridade();
        return prioridade == null ? null : prioridade.name();
    }
}
