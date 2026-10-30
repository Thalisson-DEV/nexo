package com.nexo.app.modules.assistente.doc;

import com.nexo.app.modules.assistente.application.dto.AutorizacaoIaPreviaResponse;
import com.nexo.app.modules.assistente.application.dto.AutorizacaoIaRedirecionamento;
import com.nexo.app.modules.assistente.application.dto.AutorizarIaRequest;
import com.nexo.app.modules.assistente.application.dto.ChaveIaCriadaResponse;
import com.nexo.app.modules.assistente.application.dto.ConexaoIaInfoResponse;
import com.nexo.app.modules.assistente.application.dto.ConexaoIaResponse;
import com.nexo.app.modules.assistente.application.dto.CriarChaveIaRequest;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Conexões de IA", description = "Conectar o Nexo ao Claude, ChatGPT e outras IAs pelo MCP: conexões, chaves de acesso e a tela de autorização do OAuth")
@SecurityRequirement(name = "bearerAuth")
public interface ConexaoIaControllerDoc {

    @Operation(summary = "Endereço do servidor MCP e o que a IA vai enxergar com o perfil da pessoa")
    ResponseEntity<ConexaoIaInfoResponse> info(HttpServletRequest request);

    @Operation(summary = "Conexões da pessoa logada", description = "Inclui as desconectadas e vencidas, para histórico.")
    ResponseEntity<List<ConexaoIaResponse>> listar();

    @Operation(summary = "Gerar chave de acesso", description = "A chave é mostrada só nesta resposta; o Nexo guarda apenas o hash.")
    ResponseEntity<ChaveIaCriadaResponse> criarChave(CriarChaveIaRequest request);

    @Operation(summary = "Desconectar", description = "Todas as credenciais da conexão param de valer na chamada seguinte. O administrador desconecta a de qualquer pessoa.")
    ResponseEntity<ConexaoIaResponse> desconectar(UUID id);

    @Operation(summary = "Conexões de todas as pessoas (administrador)")
    ResponseEntity<PageResponse<ConexaoIaResponse>> listarTodas(int pagina, int tamanho);

    @Operation(summary = "Tela de autorização: valida o pedido do aplicativo e diz quem está pedindo acesso")
    ResponseEntity<AutorizacaoIaPreviaResponse> previa(AutorizarIaRequest request);

    @Operation(summary = "Tela de autorização: a pessoa permitiu", description = "Emite o código de autorização (uso único, PKCE) e devolve o endereço de retorno do aplicativo.")
    ResponseEntity<AutorizacaoIaRedirecionamento> permitir(AutorizarIaRequest request, HttpServletRequest http);

    @Operation(summary = "Tela de autorização: a pessoa recusou", description = "Devolve o endereço de retorno com error=access_denied.")
    ResponseEntity<AutorizacaoIaRedirecionamento> recusar(AutorizarIaRequest request, HttpServletRequest http);
}
