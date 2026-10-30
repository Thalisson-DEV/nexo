package com.nexo.app.modules.automacao.application.motor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * O que um executor pode usar. O dono e suas permissões vêm junto porque as consultas
 * e o Copiloto enxergam exatamente o que o dono da automação enxerga.
 */
public record ContextoAcao(
        UUID automacaoId,
        String automacaoNome,
        UUID execucaoId,
        String modo,
        String noId,
        String iteracao,
        int tentativa,
        OffsetDateTime agora,
        Map<String, Object> escopo,
        BiFunction<Object, Map<String, Object>, Object> resolvedor,
        Integer limiteSegundos,
        UUID donoId,
        Set<String> donoPermissoes,
        String emailTestador,
        boolean simulacao
) {

    public boolean teste() {
        return "TESTE".equals(modo);
    }

    /** Resolve um modelo no escopo deste passo, com valores extras (ex.: {@code item}). */
    public Object resolver(Object valor, Map<String, Object> extra) {
        return resolvedor.apply(valor, extra);
    }

    public Object resolver(Object valor) {
        return resolvedor.apply(valor, null);
    }

    public UUID exigirDono() {
        if (donoId == null) throw new ErroAcao("Este passo precisa de um responsável ativo pela automação.");
        return donoId;
    }
}
