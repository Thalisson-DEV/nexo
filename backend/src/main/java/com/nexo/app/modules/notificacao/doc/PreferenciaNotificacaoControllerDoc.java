package com.nexo.app.modules.notificacao.doc;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.notificacao.application.dto.PreferenciaNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.PreferenciasNotificacaoRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notificações - Preferências", description = """
        Controle do próprio usuário sobre o que deseja receber, por categoria e por canal.
        Notificações de prioridade CRÍTICA são entregues mesmo com a categoria silenciada.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface PreferenciaNotificacaoControllerDoc {

    @Operation(summary = "Listar as preferências do usuário autenticado",
            description = "Devolve todas as categorias; as nunca configuradas vêm com o padrão.")
    ResponseEntity<List<PreferenciaNotificacaoResponse>> listar();

    @Operation(summary = "Atualizar preferências",
            description = "Envio parcial: só as categorias presentes são alteradas.")
    ResponseEntity<List<PreferenciaNotificacaoResponse>> salvar(PreferenciasNotificacaoRequest request);
}
