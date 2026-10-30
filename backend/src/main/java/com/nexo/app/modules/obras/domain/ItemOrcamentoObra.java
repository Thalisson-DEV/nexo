package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/**
 * Serviço previsto no orçamento da obra, importado de planilha para comparação com o planejamento.
 * {@code idAcao} é preenchido quando o código bate com uma Ação do contrato.
 */
@Entity
@Table(name = "itens_orcamento_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemOrcamentoObra extends BaseEntity {

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "id_importacao")
    private UUID idImportacao;

    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @Column(name = "descricao", length = 300)
    private String descricao;

    @Column(name = "unidade", length = 20)
    private String unidade;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", precision = 15, scale = 4)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "id_acao")
    private UUID idAcao;

    /**
     * Sem valor total, usa quantidade × valor unitário; sem nenhum dos dois, o item é inválido.
     */
    public ItemOrcamentoObra(UUID idObra, UUID idImportacao, String codigo, String descricao, String unidade,
                             BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal, UUID idAcao) {
        if (idObra == null) {
            throw new InvarianteVioladaException("A obra do item de orçamento é obrigatória.");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new InvarianteVioladaException("O código do serviço previsto é obrigatório.");
        }
        if (quantidade == null || quantidade.signum() < 0) {
            throw new InvarianteVioladaException("A quantidade prevista deve ser informada e não pode ser negativa.");
        }
        if (valorTotal == null && valorUnitario == null) {
            throw new InvarianteVioladaException("Informe o valor unitário ou o valor total do serviço previsto.");
        }
        if ((valorTotal != null && valorTotal.signum() < 0) || (valorUnitario != null && valorUnitario.signum() < 0)) {
            throw new InvarianteVioladaException("Os valores do serviço previsto não podem ser negativos.");
        }
        this.idObra = idObra;
        this.idImportacao = idImportacao;
        this.codigo = codigo.trim().toUpperCase(Locale.ROOT);
        this.descricao = descricao != null && !descricao.isBlank() ? limitar(descricao.trim(), 300) : null;
        this.unidade = unidade != null && !unidade.isBlank() ? limitar(unidade.trim().toUpperCase(Locale.ROOT), 20) : null;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
        this.valorTotal = (valorTotal != null ? valorTotal : valorUnitario.multiply(quantidade))
                .setScale(2, RoundingMode.HALF_UP);
        this.idAcao = idAcao;
    }

    private static String limitar(String texto, int limite) {
        return texto.length() > limite ? texto.substring(0, limite) : texto;
    }
}
