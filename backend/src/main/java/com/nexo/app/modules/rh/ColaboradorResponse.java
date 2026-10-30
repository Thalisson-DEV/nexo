package com.nexo.app.modules.rh;

import java.time.LocalDate;
import java.util.UUID;

public record ColaboradorResponse(
        UUID id,
        UUID idUsuario,
        String primeiroNome,
        String ultimoNome,
        String nomeCompleto,
        String cpf,
        ProcessoNegocioResponse processo,
        BaseOperacionalResponse baseOperacional,
        FuncaoResponse funcao, // 🔍 Usando DTO de resposta da nova Entidade
        LocalDate dataContratacao,
        boolean ativo
) {}
