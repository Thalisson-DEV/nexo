package com.nexo.app.modules.estoque;

import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import java.util.UUID;

/** Porta pública para transferências originadas por uma operação de campo. */
public interface MovimentacaoEstoqueApi { UUID transferenciaLote(MovimentacaoBulkRequest request, UUID idOperador); }
