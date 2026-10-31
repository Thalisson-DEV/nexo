package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Poste, transformador ou estrutura marcado na folha do projeto.
 */
@Entity
@Table(name = "pontos_planejamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PontoPlanejamento extends BaseEntity {

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "pagina", nullable = false)
    private Integer pagina;

    @Column(name = "x", nullable = false, precision = 8, scale = 6)
    private BigDecimal x;

    @Column(name = "y", nullable = false, precision = 8, scale = 6)
    private BigDecimal y;

    @Column(name = "barramento", length = 60)
    private String barramento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPontoPlanejamento tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_execucao", nullable = false, length = 20)
    private TipoExecucaoPonto tipoExecucao;

    @Column(name = "tem_poda", nullable = false)
    private boolean temPoda;

    @Column(name = "tem_aterramento", nullable = false)
    private boolean temAterramento;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemPlanejamento origem;

    public PontoPlanejamento(UUID idObra, String numero, Integer pagina, BigDecimal x, BigDecimal y,
                             OrigemPlanejamento origem) {
        if (idObra == null) {
            throw new InvarianteVioladaException("A obra do ponto é obrigatória.");
        }
        this.idObra = idObra;
        this.origem = origem != null ? origem : OrigemPlanejamento.MANUAL;
        this.tipo = TipoPontoPlanejamento.POSTE;
        this.tipoExecucao = TipoExecucaoPonto.NAO_DEFINIDO;
        this.numero = normalizarNumero(numero);
        mover(pagina, x, y);
    }

    public static String normalizarNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            throw new InvarianteVioladaException("O número do ponto é obrigatório.");
        }
        String normalizado = numero.trim().toUpperCase(Locale.ROOT);
        if (normalizado.length() > 20) {
            throw new InvarianteVioladaException("O número do ponto deve ter até 20 caracteres.");
        }
        return normalizado;
    }

    public void mover(Integer pagina, BigDecimal x, BigDecimal y) {
        PosicaoFolha.validar(pagina, x, y);
        this.pagina = pagina;
        this.x = x;
        this.y = y;
    }

    public void editar(String numero, String barramento, TipoPontoPlanejamento tipo, TipoExecucaoPonto tipoExecucao,
                       boolean temPoda, boolean temAterramento) {
        this.numero = normalizarNumero(numero);
        this.barramento = barramento != null && !barramento.isBlank() ? barramento.trim() : null;
        this.tipo = tipo != null ? tipo : TipoPontoPlanejamento.POSTE;
        this.tipoExecucao = tipoExecucao != null ? tipoExecucao : TipoExecucaoPonto.NAO_DEFINIDO;
        this.temPoda = temPoda;
        this.temAterramento = temAterramento;
    }
}
