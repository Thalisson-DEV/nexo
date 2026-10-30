package com.nexo.app.modules.obras.doc;

import com.nexo.app.modules.obras.application.dto.*;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Obras", description = "Gestão de obras, projetos relacionados, equipes alocadas e anexos técnicos")
@SecurityRequirement(name = "bearerAuth")
public interface ObraControllerDoc {

    @Operation(summary = "Cadastrar nova obra")
    ResponseEntity<ObraResponse> criar(ObraRequest request);

    @Operation(summary = "Atualizar obra existente")
    ResponseEntity<ObraResponse> atualizar(UUID id, ObraRequest request);

    @Operation(summary = "Alterar status da obra")
    ResponseEntity<ObraResponse> alterarStatus(UUID id, UUID idNovoStatus, LocalDate dataFimEfetiva);

    @Operation(summary = "Buscar obra detalhada por ID")
    ResponseEntity<ObraResponse> buscarPorId(UUID id);

    @Operation(summary = "Listar obras com paginação, ordenação e filtros dinâmicos")
    ResponseEntity<PageResponse<ObraResumoResponse>> listar(@ParameterObject ObraFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Listar projetos relacionados nos dois sentidos do vínculo")
    ResponseEntity<List<ProjetoRelacionadoResponse>> listarProjetosRelacionados(UUID idProjeto);

    @Operation(summary = "Relacionar dois projetos independentes como KIT ou MEDIDOR")
    ResponseEntity<ProjetoRelacionadoResponse> vincularProjeto(UUID idProjeto, VinculoProjetoRequest request);

    @Operation(summary = "Remover o vínculo entre dois projetos independentes")
    ResponseEntity<Void> desvincularProjeto(UUID idProjeto, UUID idProjetoRelacionado);

    @Operation(summary = "Vincular subprojeto (obra filha, ex: Medidor ou Kit) à obra principal")
    @Deprecated(forRemoval = true)
    ResponseEntity<ObraResponse> vincularSubprojeto(UUID idObraPai, SubprojetoVinculoRequest request);

    @Operation(summary = "Desvincular subprojeto da obra principal")
    @Deprecated(forRemoval = true)
    ResponseEntity<ObraResponse> desvincularSubprojeto(UUID idObraPai, UUID idFilho);

    @Operation(summary = "Desativar obra (Soft Delete)")
    ResponseEntity<Void> desativar(UUID id);

    @Operation(summary = "Fazer upload de anexo técnico/documento para a obra")
    ResponseEntity<AnexoObraResponse> anexar(UUID id, UUID idTipoAnexo, String observacoes, MultipartFile arquivo);

    @Operation(summary = "Listar anexos vinculados à obra")
    ResponseEntity<List<AnexoObraResponse>> listarAnexos(UUID id);

    @Operation(summary = "Baixar anexo de obra")
    ResponseEntity<StreamingResponseBody> baixarAnexo(UUID id, UUID idAnexo);

    @Operation(summary = "Remover anexo de obra")
    ResponseEntity<Void> removerAnexo(UUID id, UUID idAnexo);

    @Operation(summary = "Validar arquivo CSV para importação de obras em lote")
    ResponseEntity<ImportacaoObraResponse> validarImportacao(MultipartFile arquivo);

    @Operation(summary = "Confirmar importação de obras em lote")
    ResponseEntity<ImportacaoObraResponse> confirmarImportacao(ConfirmarImportacaoObraRequest request);

    @Operation(summary = "Exportar listagem resumida de obras em CSV")
    ResponseEntity<StreamingResponseBody> exportarResumo(@ParameterObject ObraFilter filter);

    @Operation(summary = "Exportar listagem completa detalhada de obras em CSV")
    ResponseEntity<StreamingResponseBody> exportarDetalhado(@ParameterObject ObraFilter filter);

    @Operation(summary = "Baixar modelo CSV para importação de obras", description = "Disponibiliza o arquivo CSV de exemplo com cabeçalho e formato exatos para importação de obras.")
    ResponseEntity<byte[]> baixarModeloCsv();
}
