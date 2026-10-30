package com.nexo.app.modules.estoque.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Sub-etapas operacionais das solicitações técnicas agrupadas dentro das 4 macro-etapas.
 */
@Schema(description = "Sub-etapa operacional do Kanban de solicitações técnicas")
@Getter
@RequiredArgsConstructor
public enum SubEtapaKanban {
    // Planejamento
    PENDENTE_APROVACAO("Pendente de Aprovação", MacroEtapaKanban.PLANEJAMENTO, "amber"),
    PENDENTE_PLANEJAMENTO("Pendente de Planejamento", MacroEtapaKanban.PLANEJAMENTO, "amber"),
    PLANEJADO("Planejado", MacroEtapaKanban.PLANEJAMENTO, "sky"),

    // Separação
    PRONTO_PARA_SEPARACAO("Pronto para Separação", MacroEtapaKanban.SEPARACAO, "orange"),
    EM_SEPARACAO("Em Separação", MacroEtapaKanban.SEPARACAO, "purple"),
    SEPARADO("Separado", MacroEtapaKanban.SEPARACAO, "emerald"),

    // Entrega & Campo
    PENDENTE_RETIRADA("Pendente de Retirada", MacroEtapaKanban.ENTREGA, "cyan"),
    PENDENTE_DEVOLUCAO("Pendente de Devolução", MacroEtapaKanban.ENTREGA, "violet"),
    CONCLUIDO("Concluído", MacroEtapaKanban.ENTREGA, "green"),

    // Recusado
    REJEITADO("Rejeitado", MacroEtapaKanban.RECUSADO, "rose"),
    CANCELADO("Cancelado", MacroEtapaKanban.RECUSADO, "slate");

    private final String label;
    private final MacroEtapaKanban macroEtapa;
    private final String corPadrao;

    /**
     * Fatos da solicitação que decidem a sub-etapa. Permite calcular a etapa a partir de uma projeção
     * (painel analítico) sem carregar a entidade, com a mesma regra do Kanban.
     */
    public record Fatos(
            SolicitacaoMaterial.StatusSolicitacao status,
            ContextoSolicitacaoMaterial contexto,
            boolean encerrada,
            boolean temSaldoPendente,
            TipoSolicitacaoTecnica tipoTecnico,
            StatusSeparacao statusSeparacao,
            boolean temDataPrevista,
            boolean saqueForaEquipe,
            boolean temEquipeSaque,
            boolean excecaoReserva,
            boolean temReserva
    ) {
    }

    public static SubEtapaKanban calcular(SolicitacaoMaterial solicitacao) {
        if (solicitacao == null) {
            return null;
        }
        SolicitacaoMaterial.StatusSolicitacao status = solicitacao.getStatus();
        // Itens só são lidos quando a regra precisa deles (estorno), como antes.
        boolean temSaldoPendente = status == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO
                && solicitacao.getItens() != null
                && solicitacao.getItens().stream().anyMatch(i -> i.getQuantidadePendente().signum() > 0);
        return calcular(new Fatos(
                status,
                solicitacao.getContexto(),
                solicitacao.estaEncerrada(),
                temSaldoPendente,
                solicitacao.getTipoTecnico(),
                solicitacao.getStatusSeparacao(),
                solicitacao.getDataPrevistaRetirada() != null,
                solicitacao.isSaqueForaEquipe(),
                solicitacao.getIdEquipeSaque() != null,
                solicitacao.isExcecaoReserva(),
                solicitacao.getReserva() != null
        ));
    }

    public static SubEtapaKanban calcular(Fatos f) {
        if (f == null || f.status() == null) {
            return null;
        }

        // 1. Recusados / Cancelados
        if (f.status() == SolicitacaoMaterial.StatusSolicitacao.REJEITADO) {
            return REJEITADO;
        }
        if (f.status() == SolicitacaoMaterial.StatusSolicitacao.CANCELADO) {
            return CANCELADO;
        }

        // 2. Concluídos (encerradas ou com status terminal de sucesso)
        if (f.encerrada()
                || f.status() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE
                || f.status() == SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO) {
            return CONCLUIDO;
        }
        if (f.status() == SolicitacaoMaterial.StatusSolicitacao.ESTORNADO && !f.temSaldoPendente()) {
            return CONCLUIDO;
        }

        // 3. Contexto Comercial
        if (f.contexto() == ContextoSolicitacaoMaterial.COMERCIAL) {
            if (f.status() == SolicitacaoMaterial.StatusSolicitacao.PENDENTE) {
                return PENDENTE_APROVACAO;
            }
            if (f.status() == SolicitacaoMaterial.StatusSolicitacao.APROVADO
                    || f.status() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL) {
                return PENDENTE_RETIRADA;
            }
            return PENDENTE_APROVACAO;
        }

        // 4. Devoluções técnicas (222/922)
        TipoSolicitacaoTecnica tipo = f.tipoTecnico();
        if (tipo != null && tipo.isDevolucao()) {
            return PENDENTE_DEVOLUCAO;
        }

        // 5. Separação de materiais técnicos
        if (f.statusSeparacao() == StatusSeparacao.EM_SEPARACAO) {
            return EM_SEPARACAO;
        }
        if (f.statusSeparacao() == StatusSeparacao.PRONTO_PARA_SEPARACAO) {
            return PRONTO_PARA_SEPARACAO;
        }
        if (f.statusSeparacao() == StatusSeparacao.SEPARADO) {
            if (f.status() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL) {
                return PENDENTE_RETIRADA;
            }
            return SEPARADO;
        }

        // 6. Planejamento
        if (!f.temDataPrevista()
                || (!f.saqueForaEquipe() && !f.temEquipeSaque())
                || (!f.excecaoReserva() && !f.temReserva())) {
            return PENDENTE_PLANEJAMENTO;
        }

        if (f.statusSeparacao() == StatusSeparacao.AGUARDANDO_AGENDAMENTO
                || f.statusSeparacao() == null) {
            return PLANEJADO;
        }

        if (f.status() == SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL) {
            return PENDENTE_RETIRADA;
        }

        return PENDENTE_PLANEJAMENTO;
    }
}
