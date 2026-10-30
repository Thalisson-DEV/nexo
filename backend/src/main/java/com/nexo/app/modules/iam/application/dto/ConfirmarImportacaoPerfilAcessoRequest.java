package com.nexo.app.modules.iam.application.dto;

import java.util.List;

public record ConfirmarImportacaoPerfilAcessoRequest(
    List<PerfilAcessoImportacaoDTO> perfis
) {
}
