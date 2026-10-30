package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Contrato completo de um formulário: tudo o que o frontend precisa para
 * renderizar a tela, validar o preenchimento e montar o payload — sem nenhuma
 * regra replicada em código de tela.
 *
 * <p>Serve tanto ao solicitante (preenchimento) quanto ao administrador
 * (configuração), que aproveita os IDs dos campos.</p>
 */
@Schema(description = "Schema completo de uma variante de formulário")
public record FormularioSchemaResponse(
        UUID id,
        TipoSolicitacao tipoSolicitacao,
        String codigo,
        String titulo,
        String descricao,
        boolean padrao,
        boolean ativo,

        @Schema(description = "Campos na ordem de exibição")
        List<CampoSchemaResponse> campos
) {}
