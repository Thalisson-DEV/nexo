package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Pedido de autorização para efetuar um pagamento.
 *
 * <p>Pode nascer de uma {@link SolicitacaoCompra} já aprovada — caso em que os
 * dados comuns são copiados dela no preenchimento — ou existir por conta própria,
 * para as despesas que não passam por compra (combustível em fornecedor
 * cadastrado, contas recorrentes).</p>
 *
 * <p>O vínculo com a compra é uma <b>referência</b>, guardada em
 * {@code nucleo.solicitacaoCompraId}: serve para rastrear a origem e para herdar o
 * preenchimento, e não amarra os valores dos dois documentos. Quem autoriza o
 * pagamento vê de qual compra ele veio e decide.</p>
 *
 * <p>Os arquivos ficam no repositório documental ({@link DocumentoFinanceiro}) e
 * apontam para cá — ver a nota em {@link SolicitacaoCompra}.</p>
 */
@Entity
@Table(name = "financeiro_solicitacao_pagamento",
        indexes = {
                @Index(name = "idx_pagamento_status", columnList = "status"),
                @Index(name = "idx_pagamento_numero", columnList = "numero", unique = true),
                @Index(name = "idx_pagamento_compra", columnList = "solicitacao_compra_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitacaoPagamento extends SolicitacaoBase {

    public SolicitacaoPagamento(FormularioSolicitacao formulario, NucleoSolicitacao nucleo,
                                UUID usuarioSolicitanteId) {
        super(formulario, nucleo, usuarioSolicitanteId);
        exigirFormularioDePagamento(formulario);
    }

    @Override
    public TipoSolicitacao getTipoSolicitacao() {
        return TipoSolicitacao.PAGAMENTO;
    }

    public void atualizarConteudo(NucleoSolicitacao novoNucleo) {
        substituirNucleo(novoNucleo);
    }

    /** Compra que originou este pagamento, quando houver. */
    public UUID getSolicitacaoCompraId() {
        return getNucleo().getSolicitacaoCompraId();
    }

    private static void exigirFormularioDePagamento(FormularioSolicitacao formulario) {
        if (formulario.getTipoSolicitacao() != TipoSolicitacao.PAGAMENTO) {
            throw new InvarianteVioladaException(
                    "O formulário '" + formulario.getCodigo() + "' não é um formulário de pagamento.");
        }
    }
}
