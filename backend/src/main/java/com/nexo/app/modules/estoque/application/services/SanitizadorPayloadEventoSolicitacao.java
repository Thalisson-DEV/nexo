package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Remove segredos do evento antes de sua serialização, persistência e assinatura HMAC. */
public final class SanitizadorPayloadEventoSolicitacao {

    private static final List<String> MARCADORES_SENSIVEIS = List.of(
            "assinatura",
            "token",
            "segredo",
            "secret",
            "senha",
            "password",
            "passwd",
            "credencial",
            "credential",
            "authorization",
            "autorizacao",
            "apikey",
            "chaveapi",
            "idempotency",
            "idempotencia",
            "cifrado",
            "ciphertext",
            "encryptedcontent",
            "encryptedpayload",
            "encryptedvalue",
            "privatekey",
            "chaveprivada"
    );

    private SanitizadorPayloadEventoSolicitacao() {
    }

    public static PayloadEventoSolicitacao sanitizar(PayloadEventoSolicitacao payload) {
        List<AlteracaoEventoSolicitacao> alteracoes = payload.alteracoes().stream()
                .filter(alteracao -> !nomeSensivel(alteracao.caminho()))
                .map(alteracao -> new AlteracaoEventoSolicitacao(
                        alteracao.caminho(),
                        sanitizarValor(alteracao.valorAnterior()),
                        sanitizarValor(alteracao.valorNovo())))
                .toList();
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshots = (Map<String, Object>) sanitizarValor(payload.snapshots());
        return new PayloadEventoSolicitacao(payload.comando(), alteracoes, snapshots);
    }

    /** Compartilhado com a leitura para também proteger eventos legados já persistidos. */
    public static boolean nomeSensivel(String nome) {
        if (nome == null) {
            return false;
        }
        String normalizado = Normalizer.normalize(nome, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return MARCADORES_SENSIVEIS.stream().anyMatch(normalizado::contains);
    }

    private static Object sanitizarValor(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> seguro = new LinkedHashMap<>();
            mapa.forEach((chave, item) -> {
                String nome = String.valueOf(chave);
                if (!nomeSensivel(nome)) {
                    seguro.put(nome, sanitizarValor(item));
                }
            });
            return seguro;
        }
        if (valor instanceof Collection<?> colecao) {
            List<Object> seguro = new ArrayList<>(colecao.size());
            colecao.forEach(item -> seguro.add(sanitizarValor(item)));
            return seguro;
        }
        return valor;
    }
}
