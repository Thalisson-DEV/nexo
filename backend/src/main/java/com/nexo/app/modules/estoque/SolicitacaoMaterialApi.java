package com.nexo.app.modules.estoque;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import java.util.UUID;

/** Porta pública para criação idempotente/reprocessada de solicitações de campo. */
public interface SolicitacaoMaterialApi { UUID criar(SolicitacaoRequest request, UUID idRequisitante); }
