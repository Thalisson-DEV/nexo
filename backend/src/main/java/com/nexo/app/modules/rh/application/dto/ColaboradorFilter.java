package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.rh.domain.EquipeMembro;
import io.swagger.v3.oas.annotations.Parameter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record para captura de parâmetros de filtro dinâmico de colaboradores.
 * Inclui suporte a busca por CPF, Nome Completo e Função.
 */
public record ColaboradorFilter(
        Boolean ativo,
        LocalDate dataContratacaoInicio,
        LocalDate dataContratacaoFim,
        Integer mesContratacao,
        Integer anoContratacao,
        UUID idBaseOperacional,
        UUID idProcesso,
        UUID idFuncao,
        String cpf,
        String nomeCompleto,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por nome do colaborador")
        String buscaGlobal,

        EquipeMembro.FuncaoMembro funcaoMembroEquipe
) {}
