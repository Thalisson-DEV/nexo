package com.nexo.app.modules.iam.application.dto;

import com.nexo.app.modules.iam.domain.TipoUsuario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record UsuarioResponse(
    UUID id,
    UUID idColaborador,
    String login,
    String email,
    String telefone,
    TipoUsuario tipo,
    PerfilAcessoResponse perfil,
    String tema,
    String corPrimaria,
    boolean animacoesAtivadas,
    boolean ativo,

    @Schema(description = "Dados do colaborador dono deste acesso, resolvidos a partir de idColaborador")
    ColaboradorVinculadoResponse colaborador
) {

    /**
     * Devolve a mesma resposta com o bloco do colaborador preenchido.
     *
     * <p>O vínculo é apenas um {@code UUID} na entidade — o cadastro da pessoa vive em
     * outro módulo e chega depois, em lote, para a página inteira. Este método é o ponto
     * onde as duas metades se juntam, sem que o mapper precise conhecer o RH.</p>
     */
    public UsuarioResponse comColaborador(ColaboradorVinculadoResponse colaborador) {
        return new UsuarioResponse(id, idColaborador, login, email, telefone, tipo, perfil,
                tema, corPrimaria, animacoesAtivadas, ativo, colaborador);
    }
}
