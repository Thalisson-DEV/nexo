package com.nexo.app.modules.assistente.doc;

import com.nexo.app.modules.assistente.application.artifacts.ArtifactService;
import com.nexo.app.modules.assistente.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Copiloto", description = "Workspace agentic do Copiloto: conversas, streaming NDJSON, arquivos, artefatos e resultados")
@SecurityRequirement(name = "bearerAuth")
public interface AssistenteControllerDoc {

    @Operation(summary = "Listar conversas do usuario", description = "Ordenadas pela ultima atividade; 'busca' procura no titulo e nas perguntas; 'painel' restringe as conversas iniciadas naquele dashboard.")
    ResponseEntity<PageResponse<ConversaResumoResponse>> listar(int pagina, int tamanho, String busca, String painel);

    @Operation(summary = "Criar conversa", description = "Aceita contexto de origem (pagina, entidade, filtros e, em dashboards, o contexto estruturado do painel). Com painel, a conversa fica associada a origem DASHBOARD.")
    ResponseEntity<ConversaResumoResponse> criar(CriarConversaRequest request);

    @Operation(summary = "Consultar conversa com mensagens, timeline, artefatos e arquivos")
    ResponseEntity<ConversaDetalheResponse> buscar(UUID conversaId);

    @Operation(summary = "Renomear conversa")
    ResponseEntity<ConversaResumoResponse> renomear(UUID conversaId, RenomearConversaRequest request);

    @Operation(summary = "Excluir conversa", description = "Exclusao logica; interrompe execucao em andamento.")
    ResponseEntity<Void> excluir(UUID conversaId);

    @Operation(summary = "Enviar mensagem com resposta em streaming",
            description = "Resposta application/x-ndjson: uma linha JSON {placement:{turnIndex,tabIndex}, obj:{type,...}} por pacote "
                    + "(run_info, message_start, message_delta, tool_start, tool_output, tool_result, artifact, file, navigation, "
                    + "title, error, stop, heartbeat). clientMessageId torna o envio idempotente. A execucao continua se a "
                    + "conexao cair; retome com GET /stream.")
    void enviar(UUID conversaId, EnviarMensagemRequest request, HttpServletResponse response) throws IOException;

    @Operation(summary = "Retomar o stream da execucao da conversa a partir de um cursor (numero de pacotes ja recebidos)")
    void retomar(UUID conversaId, @Parameter(description = "Pacotes ja recebidos") int cursor, HttpServletResponse response)
            throws IOException;

    @Operation(summary = "Interromper a geracao em andamento", description = "O conteudo parcial e preservado.")
    ResponseEntity<Map<String, Object>> parar(UUID conversaId);

    @Operation(summary = "Consultar uma mensagem")
    ResponseEntity<MensagemResponse> mensagem(UUID conversaId, UUID mensagemId);

    @Operation(summary = "Listar artefatos da conversa")
    ResponseEntity<List<ArtifactService.ArtefatoView>> artefatos(UUID conversaId);

    @Operation(summary = "Consultar artefato pelo codigo (a1, a2...)")
    ResponseEntity<ArtifactService.ArtefatoView> artefato(UUID conversaId, String codigo);

    @Operation(summary = "Consultar resultado tabular pelo codigo (r1, r2...)", description = "Dados usados nas respostas, com procedencia.")
    ResponseEntity<Map<String, Object>> resultado(UUID conversaId, String codigo, int limite);

    @Operation(summary = "Enviar anexo", description = "PDF, XLSX, XLS, CSV, JSON, TXT, DOCX e imagens; o conteudo e extraido no envio.")
    ResponseEntity<ArquivoAssistenteResponse> enviarArquivo(MultipartFile arquivo) throws IOException;

    @Operation(summary = "Baixar arquivo (anexo ou gerado)")
    ResponseEntity<byte[]> baixarArquivo(UUID arquivoId, boolean download);

    @Operation(summary = "Remover anexo")
    ResponseEntity<Void> removerArquivo(UUID arquivoId);

    @Operation(summary = "Capacidades do Copiloto para o usuario", description = "Ferramentas operacionais, competencias e sugestoes.")
    ResponseEntity<CapacidadesResponse> capacidades();
}
