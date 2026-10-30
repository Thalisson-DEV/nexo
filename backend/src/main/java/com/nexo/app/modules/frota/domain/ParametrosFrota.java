package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Limiares da frota que o gestor ajusta sem deploy. Existe uma única linha,
 * criada pela migração com o id {@link #ID_UNICO}.
 */
@Entity
@Table(name = "frota_parametros")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParametrosFrota extends BaseEntity {

    public static final UUID ID_UNICO = UUID.fromString("00000000-0000-0000-0000-00000000f001");

    @Column(name = "faixas_alerta_dias", nullable = false, length = 100)
    private String faixasAlertaDias;

    @Column(name = "limite_km_dia", nullable = false)
    private Integer limiteKmDia;

    /** Dias de antecedência, do maior para o menor e sem repetição (ex.: 30, 15, 7, 0). */
    public List<Integer> faixasAlerta() {
        return converterFaixas(faixasAlertaDias);
    }

    public void atualizar(List<Integer> faixas, Integer limiteKmDia) {
        if (limiteKmDia == null || limiteKmDia <= 0) {
            throw new InvarianteVioladaException("O limite de km por dia deve ser maior que zero.");
        }
        if (faixas == null || faixas.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos uma faixa de aviso de vencimento.");
        }
        if (faixas.stream().anyMatch(f -> f == null || f < 0 || f > 365)) {
            throw new InvarianteVioladaException("As faixas de aviso devem estar entre 0 e 365 dias.");
        }
        this.faixasAlertaDias = String.join(",", faixas.stream()
                .distinct().sorted((a, b) -> b - a).map(String::valueOf).toList());
        this.limiteKmDia = limiteKmDia;
    }

    static List<Integer> converterFaixas(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        return Arrays.stream(texto.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .distinct()
                .sorted((a, b) -> b - a)
                .toList();
    }
}
