package com.nexo.app.modules.iam.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record UsuarioImportacaoDTO(
        Integer linha,
        String login,
        String email,
        String telefone,
        String senha,
        String nomePerfil,
        String tipo,
        String nomeColaborador,
        ImportacaoStatus status,
        String mensagemErro
) {
    public UsuarioImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new UsuarioImportacaoDTO(
                linha, login, email, telefone, senha, nomePerfil, tipo, nomeColaborador,
                novoStatus, novaMensagem
        );
    }
}
