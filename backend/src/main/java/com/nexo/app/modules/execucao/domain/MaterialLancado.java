package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.application.dto.OperacaoMaterial;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Um material como aparece na nota: o que foi, quanto, se aplicado ou retirado, a série
 * e de onde veio. Na alteração, é também a diferença que entrou ou saiu da nota.
 */
public record MaterialLancado(
        UUID idMaterial,
        BigDecimal quantidade,
        OperacaoMaterial operacao,
        String numSerie,
        OrigemMaterial origem
) {
    public MaterialLancado {
        Objects.requireNonNull(idMaterial, "ID do material é obrigatório");
        Objects.requireNonNull(quantidade, "Quantidade é obrigatória");
        Objects.requireNonNull(operacao, "Operação do material é obrigatória");
        numSerie = numSerie == null || numSerie.isBlank() ? null : numSerie.trim();
        origem = origem == null ? OrigemMaterial.EQUIPE : origem;
    }

    /** Mesmo material, operação, série e origem: é a mesma linha da nota, só muda a quantidade. */
    Chave chave() {
        return new Chave(idMaterial, operacao, numSerie == null ? null : numSerie.toUpperCase(Locale.ROOT), origem);
    }

    MaterialLancado comQuantidade(BigDecimal novaQuantidade) {
        return new MaterialLancado(idMaterial, novaQuantidade, operacao, numSerie, origem);
    }

    record Chave(UUID idMaterial, OperacaoMaterial operacao, String numSerie, OrigemMaterial origem) {
    }
}
