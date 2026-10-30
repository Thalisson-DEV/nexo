package com.nexo.app.modules.estoque.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Projecao canonica da etapa operacional de uma solicitacao tecnica.
 *
 * <p>A etapa e derivada dos mesmos fatos que alimentam as filas. Nao substitui
 * o status legado da solicitacao nem o status especifico de separacao.</p>
 */
@Schema(description = "Etapa operacional calculada da solicitacao tecnica")
public enum EtapaOperacionalSolicitacao {
    AGUARDANDO_PLANEJAMENTO,
    PRONTO_PARA_SEPARACAO,
    EM_SEPARACAO,
    PENDENTE_ENTREGA,
    PENDENTE_DEVOLUCAO,
    CONCLUIDA;

    public static EtapaOperacionalSolicitacao calcular(SolicitacaoMaterial solicitacao) {
        if (solicitacao == null || solicitacao.getContexto() != ContextoSolicitacaoMaterial.TECNICO) {
            return null;
        }

        if (solicitacao.estaEncerrada() || statusConcluido(solicitacao)) {
            return CONCLUIDA;
        }

        TipoSolicitacaoTecnica tipo = solicitacao.getTipoTecnico();
        if (tipo == null) {
            return AGUARDANDO_PLANEJAMENTO;
        }

        if (tipo.isAdicao()) {
            if (solicitacao.getStatusSeparacao() == StatusSeparacao.EM_SEPARACAO) {
                return EM_SEPARACAO;
            }
            if (solicitacao.getStatusSeparacao() == StatusSeparacao.SEPARADO
                    && (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                    || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL
                    || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO)) {
                return possuiQuantidadePendente(solicitacao)
                        ? PENDENTE_ENTREGA : CONCLUIDA;
            }
            if (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                    && possuiPendenciaDePlanejamento(solicitacao)) {
                return AGUARDANDO_PLANEJAMENTO;
            }
            if (solicitacao.getStatusSeparacao() == StatusSeparacao.PRONTO_PARA_SEPARACAO
                    || solicitacao.getDataPrevistaRetirada() != null) {
                return PRONTO_PARA_SEPARACAO;
            }
            return AGUARDANDO_PLANEJAMENTO;
        }

        if (solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                && possuiPendenciaDePlanejamento(solicitacao)) {
            return AGUARDANDO_PLANEJAMENTO;
        }

        if ((solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO_PARCIAL
                || solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO)
                && possuiQuantidadePendente(solicitacao)) {
            return PENDENTE_DEVOLUCAO;
        }
        return CONCLUIDA;
    }

    public static boolean possuiPendenciaDePlanejamento(SolicitacaoMaterial solicitacao) {
        if (solicitacao.getTipoTecnico() != null && solicitacao.getTipoTecnico().isDevolucao()) {
            return false;
        }
        return solicitacao.getDataPrevistaRetirada() == null;
    }

    private static boolean statusConcluido(SolicitacaoMaterial solicitacao) {
        SolicitacaoMaterial.StatusSolicitacao status = solicitacao.getStatus();
        if (status == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE
                || status == SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO
                || status == SolicitacaoMaterial.StatusSolicitacao.CANCELADO
                || status == SolicitacaoMaterial.StatusSolicitacao.REJEITADO) {
            return true;
        }
        if (status == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO) {
            return !possuiQuantidadePendente(solicitacao);
        }
        return false;
    }

    private static boolean possuiQuantidadePendente(SolicitacaoMaterial solicitacao) {
        return solicitacao.getItens() != null
                && solicitacao.getItens().stream()
                .anyMatch(item -> item.getQuantidadePendente().signum() > 0);
    }
}
