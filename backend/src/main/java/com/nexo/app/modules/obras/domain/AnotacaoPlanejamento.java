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
import java.util.UUID;

/**
 * Texto livre fixado numa posição da folha.
 */
@Entity
@Table(name = "anotacoes_planejamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnotacaoPlanejamento extends BaseEntity {

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "pagina", nullable = false)
    private Integer pagina;

    @Column(name = "x", nullable = false, precision = 8, scale = 6)
    private BigDecimal x;

    @Column(name = "y", nullable = false, precision = 8, scale = 6)
    private BigDecimal y;

    @Column(name = "texto", nullable = false, length = 1000)
    private String texto;

    public AnotacaoPlanejamento(UUID idObra, Integer pagina, BigDecimal x, BigDecimal y, String texto) {
        if (idObra == null) {
            throw new InvarianteVioladaException("A obra da anotação é obrigatória.");
        }
        PosicaoFolha.validar(pagina, x, y);
        this.idObra = idObra;
        this.pagina = pagina;
        this.x = x;
        this.y = y;
        editar(texto);
    }

    public void editar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new InvarianteVioladaException("Escreva o texto da anotação.");
        }
        if (texto.trim().length() > 1000) {
            throw new InvarianteVioladaException("A anotação deve ter até 1000 caracteres.");
        }
        this.texto = texto.trim();
    }
}
