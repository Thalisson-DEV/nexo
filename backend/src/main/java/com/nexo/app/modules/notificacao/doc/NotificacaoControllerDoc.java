package com.nexo.app.modules.notificacao.doc;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoFilter;
import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.ResumoNotificacoesResponse;
import com.nexo.app.shared.application.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notificações", description = """
        Caixa de entrada do usuário autenticado. Todas as operações agem apenas sobre as
        próprias notificações — não há endpoint que leia ou altere a caixa de terceiros.

        Além destes endpoints, as notificações chegam em tempo real pelo WebSocket STOMP:
        conecte em `/ws` enviando o cabeçalho `Authorization: Bearer <token>` no frame
        CONNECT e assine `/user/queue/notificacoes`. Cada mensagem tem o mesmo formato do
        `NotificacaoResponse` devolvido aqui. Se a conexão cair, `GET /v1/notificacoes/resumo`
        recompõe o contador.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface NotificacaoControllerDoc {

    @Operation(summary = "Listar as notificações do usuário autenticado",
            description = "Por padrão exclui as arquivadas e as expiradas.")
    ResponseEntity<PageResponse<NotificacaoResponse>> listar(
            @ParameterObject NotificacaoFilter filtro, @ParameterObject Pageable pageable);

    @Operation(summary = "Resumo do sininho",
            description = "Total de não lidas e a quebra por categoria.")
    ResponseEntity<ResumoNotificacoesResponse> resumo();

    @Operation(summary = "Marcar uma notificação como lida")
    ResponseEntity<NotificacaoResponse> marcarLida(UUID id);

    @Operation(summary = "Marcar uma notificação como não lida")
    ResponseEntity<NotificacaoResponse> marcarNaoLida(UUID id);

    @Operation(summary = "Marcar todas as notificações como lidas",
            description = "Devolve o resumo já atualizado.")
    ResponseEntity<ResumoNotificacoesResponse> marcarTodasLidas();

    @Operation(summary = "Arquivar uma notificação",
            description = "Retira da caixa sem apagar o registro. Arquivar implica marcar como lida.")
    ResponseEntity<Void> arquivar(UUID id);

    @Operation(summary = "Arquivar todas as notificações já lidas",
            description = "Devolve o resumo já atualizado.")
    ResponseEntity<ResumoNotificacoesResponse> arquivarLidas();
}
