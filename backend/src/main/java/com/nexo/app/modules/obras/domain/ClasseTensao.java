package com.nexo.app.modules.obras.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Classe de tensão da rede onde a obra é executada; filtra os kits aplicáveis no planejamento.
 */
public enum ClasseTensao {
    BT("BT"),
    MT_15("15 kV"),
    MT_25("25 kV"),
    MT_36("36,2 kV"),
    AT_72("72,5 kV"),
    AT_138("138 kV");

    private final String rotulo;

    ClasseTensao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /**
     * Aceita o nome da constante ou o valor escrito em planilhas ("15 kV", "15KV"; "13,8 kV" cai em 15 kV).
     */
    public static Optional<ClasseTensao> interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String limpo = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        for (ClasseTensao classe : values()) {
            if (classe.name().equals(limpo)) {
                return Optional.of(classe);
            }
        }
        String numero = limpo.replace("KV", "").replace(" ", "").replace(',', '.');
        if (numero.equals("BT") || numero.equals("BAIXATENSAO")) {
            return Optional.of(BT);
        }
        try {
            double kv = Double.parseDouble(numero);
            if (kv <= 1) return Optional.of(BT);
            if (kv <= 15) return Optional.of(MT_15);
            if (kv <= 25) return Optional.of(MT_25);
            if (kv <= 36.2) return Optional.of(MT_36);
            if (kv <= 72.5) return Optional.of(AT_72);
            if (kv <= 138) return Optional.of(AT_138);
        } catch (NumberFormatException ignorado) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
