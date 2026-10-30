package com.nexo.app.modules.chamado.doc;

import com.nexo.app.modules.chamado.application.dto.AbrirChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.AnexoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.AtualizarAtendimentoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.ChamadoFilter;
import com.nexo.app.modules.chamado.application.dto.ChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.ReabrirChamadoRequest;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Chamados", description = "Autosserviço e central de atendimento de chamados")
@SecurityRequirement(name = "bearerAuth")
public interface ChamadoControllerDoc {

    @Operation(summary = "Abrir um chamado",
            description = "Cria o protocolo. Evidências opcionais podem ser enviadas em seguida pelo endpoint de anexos.")
    ResponseEntity<ChamadoResponse> abrir(AbrirChamadoRequest request);

    @Operation(summary = "Listar meus chamados",
            description = "Sempre restringe o resultado ao usuário autenticado, independentemente dos filtros recebidos.")
    ResponseEntity<PageResponse<ChamadoResponse>> listarMeus(
            @ParameterObject ChamadoFilter filtro, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar todos os chamados da central",
            description = "Exclusivo para quem possui a permissão chamado:manage.")
    ResponseEntity<PageResponse<ChamadoResponse>> listarTodos(
            @ParameterObject ChamadoFilter filtro, @ParameterObject Pageable pageable);

    @Operation(summary = "Consultar um chamado",
            description = "O solicitante acessa somente o próprio chamado; a central acessa todos.")
    ResponseEntity<ChamadoResponse> buscarPorId(UUID id);

    @Operation(summary = "Cancelar um chamado próprio ainda elegível")
    ResponseEntity<ChamadoResponse> cancelarMeuChamado(UUID id);

    @Operation(summary = "Responder um chamado próprio")
    ResponseEntity<InteracaoChamadoResponse> responderMeuChamado(
            UUID id, InteracaoChamadoRequest request);

    @Operation(summary = "Reabrir um chamado resolvido dentro do prazo do tópico")
    ResponseEntity<ChamadoResponse> reabrirMeuChamado(UUID id, ReabrirChamadoRequest request);

    @Operation(summary = "Adicionar mensagem pública ou comentário interno pela central")
    ResponseEntity<InteracaoChamadoResponse> adicionarInteracaoAtendimento(
            UUID id, InteracaoChamadoRequest request);

    @Operation(summary = "Consultar a linha do tempo",
            description = "Comentários internos são retornados somente para a central de atendimento.")
    ResponseEntity<List<InteracaoChamadoResponse>> listarInteracoes(UUID id);

    @Operation(summary = "Atualizar triagem e ciclo de atendimento",
            description = "Permite atribuir responsável e avançar o status; uma solução é obrigatória ao resolver.")
    ResponseEntity<ChamadoResponse> atualizarAtendimento(UUID id,
                                                         AtualizarAtendimentoChamadoRequest request);

    @Operation(summary = "Anexar evidência",
            description = "Aceita até 10 arquivos por chamado, com limite de 15 MB por arquivo.")
    ResponseEntity<AnexoChamadoResponse> anexar(UUID id,
                                                @Parameter(description = "Imagem, PDF, TXT, Word ou Excel")
                                                MultipartFile arquivo);

    @Operation(summary = "Baixar evidência do chamado")
    ResponseEntity<Resource> baixarAnexo(UUID id, UUID idAnexo);
}
