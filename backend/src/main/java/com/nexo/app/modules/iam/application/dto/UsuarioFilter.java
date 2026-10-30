package com.nexo.app.modules.iam.application.dto;

import com.nexo.app.modules.iam.domain.TipoUsuario;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Record para filtragem dinâmica de usuários.
 */
public record UsuarioFilter(
    String login,
    String email,
    UUID idPerfil,
    TipoUsuario tipo,
    Boolean ativo,
    LocalDateTime dataCriacaoInicio,
    LocalDateTime dataCriacaoFim,

    @Parameter(description = "Nome (ou parte do nome) do colaborador dono do acesso — permite procurar pela pessoa, sem saber o login dela")
    String nomeColaborador,

    @Parameter(description = "Colaborador específico vinculado ao acesso")
    UUID idColaborador,

    @Parameter(description = "true: apenas acessos de colaboradores; false: apenas acessos sem pessoa vinculada (contas de serviço, integrações e vínculos esquecidos)")
    Boolean vinculadoAColaborador,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por login, e-mail ou nome do colaborador")
    String buscaGlobal
) {
}
