package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.GrupoCampo;
import com.nexo.app.modules.financeiro.domain.TipoCampo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Definição de um campo, na forma em que o frontend a consome para renderizar o
 * controle de tela e validar o preenchimento.
 */
@Schema(description = "Definição de um campo do formulário")
public record CampoSchemaResponse(

        @Schema(description = "ID da definição — usado apenas na tela de configuração")
        UUID id,

        @Schema(description = "Chave usada no payload de envio", example = "valor")
        String chave,

        @Schema(description = "Rótulo a exibir", example = "Valor")
        String rotulo,

        @Schema(description = "Natureza do dado, define o componente de tela", example = "MOEDA")
        TipoCampo tipoCampo,

        @Schema(description = "Campo do sistema correspondente", example = "VALOR")
        CampoCanonico campoCanonico,

        @Schema(description = "Exige preenchimento quando o campo estiver visível", example = "true")
        boolean obrigatorio,

        @Schema(description = "Posição no formulário", example = "0")
        int ordem,

        @Schema(description = "Bloco sob o qual o campo é exibido", example = "VALORES")
        GrupoCampo grupo,

        @Schema(description = "Título do bloco, pronto para exibição", example = "Valores e condições")
        String grupoRotulo,

        @Schema(description = "Texto de apoio")
        String ajuda,

        @Schema(description = "Texto exibido no campo vazio")
        String placeholder,

        @Schema(description = "Limites de preenchimento; nulo quando não há nenhum")
        RestricoesCampoSchema restricoes,

        @Schema(description = "Opções selecionáveis; vazio nos campos que não são de escolha")
        List<OpcaoSchema> opcoes,

        @Schema(description = "Origem das opções em campos de referência; nulo nos demais")
        ReferenciaSchema referencia,

        @Schema(description = "Condição de visibilidade; nulo quando o campo é sempre visível")
        CondicaoSchema exibirQuando
) {}
