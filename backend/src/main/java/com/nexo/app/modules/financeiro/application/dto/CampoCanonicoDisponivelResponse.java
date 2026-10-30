package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.GrupoCampo;
import com.nexo.app.modules.financeiro.domain.TipoCampo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Campo do sistema que o administrador pode acrescentar a uma variante.
 *
 * <p>Alimenta a tela de configuração com o catálogo real em vez de uma lista
 * fixa no frontend, e já sinaliza os que estão em uso — cada canônico só pode
 * aparecer uma vez por formulário, porque grava numa coluna própria.</p>
 */
@Schema(description = "Campo do sistema disponível para uso numa variante")
public record CampoCanonicoDisponivelResponse(

        CampoCanonico campoCanonico,

        @Schema(description = "Chave que o campo terá no payload", example = "valor")
        String chave,

        @Schema(description = "Rótulo sugerido, sobrescrevível na configuração", example = "Valor")
        String rotuloPadrao,

        TipoCampo tipoCampo,

        @Schema(description = "Bloco em que o campo entra ao ser acrescentado", example = "VALORES")
        GrupoCampo grupo,

        @Schema(description = "Título do bloco, pronto para exibição", example = "Valores e condições")
        String grupoRotulo,

        @Schema(description = "Cadastro que alimenta o campo, quando for de referência")
        FonteReferencia fonteReferencia,

        @Schema(description = "Indica que o campo já foi acrescentado a esta variante")
        boolean jaUtilizado
) {}
