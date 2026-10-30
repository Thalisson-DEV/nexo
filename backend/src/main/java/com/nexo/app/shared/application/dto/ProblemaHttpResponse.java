package com.nexo.app.shared.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Problema HTTP estavel baseado em RFC 9457, com erros opcionais por campo")
public record ProblemaHttpResponse(
        @Schema(description = "URI que identifica a categoria do problema")
        URI type,
        @Schema(description = "Titulo legivel da categoria do problema")
        String title,
        @Schema(description = "Status HTTP da resposta", example = "422")
        int status,
        @Schema(description = "Detalhe legivel para o usuario")
        String detail,
        @Schema(description = "URI da requisicao que produziu o problema")
        URI instance,
        @Schema(description = "Codigo estavel para tratamento programatico", example = "OCORRENCIA_OBRIGATORIA")
        String code,
        @Schema(description = "Recurso afetado quando houver conflito otimista", example = "SOLICITACAO")
        String recurso,
        @Schema(description = "Versao enviada pelo cliente em conflito otimista", example = "3")
        Long versaoEsperada,
        @Schema(description = "Versao vigente que deve ser recarregada", example = "4")
        Long versaoAtual,
        @Schema(description = "Instante de criacao do problema")
        Instant timestamp,
        @Schema(description = "Mapa legado campo para mensagem, presente em erros de preenchimento")
        Map<String, String> erros,
        @Schema(description = "Mapa campo para codigo e mensagem estruturados")
        Map<String, ErroCampo> fieldErrors
) {
    @Schema(description = "Erro estruturado associado a um campo do payload")
    public record ErroCampo(
            @Schema(description = "Codigo estavel do erro", example = "OCORRENCIA_OBRIGATORIA")
            String code,
            @Schema(description = "Mensagem legivel do campo")
            String message
    ) {
    }
}
