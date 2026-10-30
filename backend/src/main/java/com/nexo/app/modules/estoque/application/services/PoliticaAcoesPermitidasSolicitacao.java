package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.AcaoPermitidaSolicitacao;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/** Centraliza a projecao de acoes para que a tela nunca precise inferir regras de estado. */
@Service
public class PoliticaAcoesPermitidasSolicitacao {

    public Set<AcaoPermitidaSolicitacao> calcular(SolicitacaoMaterial solicitacao) {
        EnumSet<AcaoPermitidaSolicitacao> acoes = EnumSet.noneOf(AcaoPermitidaSolicitacao.class);
        if (solicitacao.getContexto() != ContextoSolicitacaoMaterial.TECNICO) {
            adicionarComercial(solicitacao, acoes);
            return Set.copyOf(acoes);
        }

        if (tem("estoque:solicitacao:plan") && !solicitacao.estaEncerrada()
                && solicitacao.getStatus() != SolicitacaoMaterial.StatusSolicitacao.CANCELADO
                && !solicitacao.possuiFatoOperacional()) {
            acoes.add(AcaoPermitidaSolicitacao.EDITAR_PLANEJAMENTO);
        }
        if (!solicitacao.estaEncerrada() && tem("estoque:solicitacao:reserve")) {
            acoes.add(solicitacao.getReserva() == null
                    ? AcaoPermitidaSolicitacao.REGISTRAR_RESERVA
                    : AcaoPermitidaSolicitacao.ALTERAR_RESERVA);
        }
        adicionarSeparacao(solicitacao, acoes);
        adicionarMovimentacao(solicitacao, acoes);
        if (tem("estoque:solicitacao:close") && !solicitacao.estaEncerrada()
                && solicitacao.possuiFatoOperacional()
                && solicitacao.getItens().stream().anyMatch(item -> item.getQuantidadePendente().signum() > 0)) {
            acoes.add(AcaoPermitidaSolicitacao.ENCERRAR_SALDO);
        }
        if (tem("estoque:solicitacao:reverse") && solicitacao.possuiFatoOperacional()) {
            acoes.add(AcaoPermitidaSolicitacao.ESTORNAR);
        }
        if ((tem("estoque:solicitacao:plan") || tem("estoque:solicitacao:write"))
                && !solicitacao.estaEncerrada()
                && solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                && !solicitacao.possuiFatoOperacional()
                && solicitacao.getStatusSeparacao() != null
                && solicitacao.getStatusSeparacao() != StatusSeparacao.AGUARDANDO_AGENDAMENTO) {
            acoes.add(AcaoPermitidaSolicitacao.REJEITAR);
        }
        if (tem("estoque:solicitacao:audit:read")) {
            acoes.add(AcaoPermitidaSolicitacao.VER_HISTORICO);
        }
        return Set.copyOf(acoes);
    }

    private void adicionarSeparacao(SolicitacaoMaterial solicitacao, EnumSet<AcaoPermitidaSolicitacao> acoes) {
        if (solicitacao.getTipoTecnico() == null || !solicitacao.getTipoTecnico().isAdicao()
                || !tem("estoque:solicitacao:separate")) {
            return;
        }
        if (solicitacao.getStatusSeparacao() == StatusSeparacao.PRONTO_PARA_SEPARACAO) {
            acoes.add(AcaoPermitidaSolicitacao.INICIAR_SEPARACAO);
            acoes.add(AcaoPermitidaSolicitacao.PULAR_SEPARACAO);
        } else if (solicitacao.getStatusSeparacao() == StatusSeparacao.EM_SEPARACAO) {
            acoes.add(AcaoPermitidaSolicitacao.CONCLUIR_SEPARACAO);
        }
    }

    private void adicionarMovimentacao(SolicitacaoMaterial solicitacao, EnumSet<AcaoPermitidaSolicitacao> acoes) {
        if (solicitacao.estaEncerrada()
                || solicitacao.getTipoTecnico() == null) {
            return;
        }
        boolean temSaldoPendente = solicitacao.getItens() != null
                && solicitacao.getItens().stream().anyMatch(item -> item.getQuantidadePendente().signum() > 0);

        if (solicitacao.getTipoTecnico().isAdicao()
                && solicitacao.getDataPrevistaRetirada() != null
                && (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL
                || (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO && temSaldoPendente))
                && solicitacao.getStatusSeparacao() == StatusSeparacao.SEPARADO
                && tem("estoque:solicitacao:deliver")) {
            acoes.add(AcaoPermitidaSolicitacao.ENTREGAR);
        }
        if (solicitacao.getTipoTecnico().isDevolucao()
                && (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO_PARCIAL
                || (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO && temSaldoPendente))
                && tem("estoque:solicitacao:return")) {
            acoes.add(AcaoPermitidaSolicitacao.CONFIRMAR_DEVOLUCAO);
        }
    }

    private void adicionarComercial(SolicitacaoMaterial solicitacao, EnumSet<AcaoPermitidaSolicitacao> acoes) {
        // Aprovar e reprovar a pendente (com edição dos itens na aprovação) é de quem aprova;
        // rejeitar a aprovada que ainda não saiu do almoxarifado é do almoxarifado.
        if (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.PENDENTE
                && tem("estoque:solicitacao:approve")) {
            acoes.add(AcaoPermitidaSolicitacao.APROVAR);
            acoes.add(AcaoPermitidaSolicitacao.REJEITAR);
        }
        if (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                && tem("estoque:solicitacao:write")) {
            acoes.add(AcaoPermitidaSolicitacao.REJEITAR);
        }
        boolean possuiSaldoPendente = solicitacao.getItens() != null
                && solicitacao.getItens().stream().anyMatch(item -> item.getQuantidadePendente().signum() > 0);
        boolean possuiEntregaLiquida = solicitacao.getItens() != null
                && solicitacao.getItens().stream().anyMatch(item -> item.getQuantidadeEntregaLiquida().signum() > 0);
        // Saldo encerrado (ex.: parcial expirada em 72h) não aceita nova entrega: o que faltou não sai mais.
        if (tem("estoque:solicitacao:write") && possuiSaldoPendente && !solicitacao.estaEncerrada()
                && (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO)) {
            acoes.add(AcaoPermitidaSolicitacao.ENTREGAR);
        }
        if (tem("estoque:solicitacao:write") && possuiEntregaLiquida) {
            acoes.add(AcaoPermitidaSolicitacao.ESTORNAR);
        }
        if (tem("estoque:solicitacao:audit:read")) {
            acoes.add(AcaoPermitidaSolicitacao.VER_HISTORICO);
        }
    }

    private boolean tem(String autoridade) {
        return SecurityUtils.possuiAutoridade(autoridade);
    }
}
