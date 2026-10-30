package com.nexo.app.modules.estoque.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/** Tipos oficiais fechados do fluxo tecnico; o codigo ja identifica a operacao. */
@Schema(description = "Tipo derivado: ADICAO+COMUM=221, ADICAO+CONCRETO=921, DEVOLUCAO+COMUM=222 e DEVOLUCAO+CONCRETO=922")
public enum TipoSolicitacaoTecnica {
    ADICAO_COMUM("221", true),
    ADICAO_CONCRETO("921", true),
    DEVOLUCAO_COMUM("222", false),
    DEVOLUCAO_CONCRETO("922", false);

    private final String codigo;
    private final boolean adicao;

    TipoSolicitacaoTecnica(
            String codigo,
            boolean adicao
    ) {
        this.codigo = codigo;
        this.adicao = adicao;
    }

    public String getCodigo() {
        return codigo;
    }

    /** Compatibilidade de serialização dos contratos antigos; não é fonte de verdade. */
    public boolean isAdicao() {
        return adicao;
    }

    public boolean isDevolucao() {
        return !adicao;
    }

    public static TipoSolicitacaoTecnica porCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Tipo tecnico invalido: " + codigo);
        }
        String texto = codigo.trim();
        // 1. Código exato: "221", "921", "222", "922"
        for (TipoSolicitacaoTecnica tipo : values()) {
            if (tipo.codigo.equalsIgnoreCase(texto)) {
                return tipo;
            }
        }
        // 2. Nome do enum: "ADICAO_COMUM", etc.
        for (TipoSolicitacaoTecnica tipo : values()) {
            if (tipo.name().equalsIgnoreCase(texto)) {
                return tipo;
            }
        }
        // 3. Inicia com o código: "221 - Adição Comum", "221-Adição", etc.
        for (TipoSolicitacaoTecnica tipo : values()) {
            if (texto.startsWith(tipo.codigo)) {
                return tipo;
            }
        }
        // 4. Descrição textual com ou sem acentos
        String normalizado = java.text.Normalizer.normalize(texto.toUpperCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (normalizado.contains("ADICAO") && normalizado.contains("CONCRETO")) {
            return ADICAO_CONCRETO;
        }
        if (normalizado.contains("DEVOLUCAO") && normalizado.contains("CONCRETO")) {
            return DEVOLUCAO_CONCRETO;
        }
        if (normalizado.contains("ADICAO")) {
            return ADICAO_COMUM;
        }
        if (normalizado.contains("DEVOLUCAO")) {
            return DEVOLUCAO_COMUM;
        }
        throw new IllegalArgumentException("Tipo tecnico invalido: " + codigo);
    }
}
