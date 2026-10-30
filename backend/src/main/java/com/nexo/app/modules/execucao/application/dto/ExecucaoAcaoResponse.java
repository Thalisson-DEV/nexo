package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.contrato.AcaoResponse;

import java.util.UUID;

public record ExecucaoAcaoResponse(
        UUID id,
        AcaoResponse acao
) {}
