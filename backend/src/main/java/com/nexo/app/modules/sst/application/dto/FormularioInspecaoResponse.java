package com.nexo.app.modules.sst.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Resposta detalhada de um formulário de inspeção, com toda a árvore de grupos,
 * perguntas e opções. O campo {@code emUso} indica se o formulário já foi usado
 * em alguma inspeção (e portanto teve a estrutura travada para edição).
 */
public record FormularioInspecaoResponse(
        UUID id,
        String titulo,
        String descricao,
        boolean ativo,
        boolean emUso,
        List<GrupoPerguntaResponse> grupos
) {}
