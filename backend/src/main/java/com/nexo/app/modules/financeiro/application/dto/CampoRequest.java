package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Acréscimo ou ajuste de um campo numa variante.
 *
 * <p>Só há o que a configuração de fato decide. Tipo do dado, chave, opções,
 * limites e condição de exibição são propriedades do campo do sistema escolhido e
 * não trafegam aqui — por isso não existe combinação inválida a recusar.</p>
 *
 * <p>{@code campoCanonico} identifica qual campo entra e é exigido apenas na
 * criação; ao editar, o campo já está definido e o valor é ignorado.</p>
 */
public record CampoRequest(

        @Schema(description = "Campo do sistema a utilizar", example = "VALOR")
        CampoCanonico campoCanonico,

        @Schema(description = "Rótulo exibido ao usuário; em branco usa o rótulo padrão do campo",
                example = "Valor da compra")
        @Size(max = 150, message = "O rótulo deve ter no máximo 150 caracteres.")
        String rotulo,

        @Schema(description = "Texto de apoio exibido abaixo do campo")
        @Size(max = 500, message = "O texto de ajuda deve ter no máximo 500 caracteres.")
        String ajuda,

        @Schema(description = "Exige preenchimento quando o campo estiver visível", example = "true")
        boolean obrigatorio
) {}
