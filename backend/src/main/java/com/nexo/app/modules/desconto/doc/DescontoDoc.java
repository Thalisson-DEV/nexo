package com.nexo.app.modules.desconto.doc;

import com.nexo.app.modules.desconto.application.dto.DescontoFilter;
import com.nexo.app.modules.desconto.application.dto.DescontoItemRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoResponse;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Descontos", description = "Gerenciamento do ciclo de vida de descontos (Solicitação, Validação, Baixa)")
public interface DescontoDoc {

    @Operation(summary = "Solicitar novo desconto")
    @PostMapping
    ResponseEntity<DescontoResponse> solicitar(@RequestBody DescontoRequest request, @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Atualizar o termo de desconto",
            description = "O termo é totalmente alterável (inclusive o colaborador) enquanto estiver no status SOLICITADO, ou seja, antes da validação.")
    @PutMapping("/{id}")
    ResponseEntity<DescontoResponse> atualizar(@PathVariable UUID id, @RequestBody DescontoRequest request);

    @Operation(summary = "Adicionar item ao desconto")
    @PostMapping("/{id}/itens")
    ResponseEntity<Void> adicionarItem(@PathVariable UUID id, @RequestBody DescontoItemRequest request);

    @Operation(summary = "Atualizar item do desconto")
    @PutMapping("/{id}/itens/{itemId}")
    ResponseEntity<Void> atualizarItem(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody DescontoItemRequest request);

    @Operation(summary = "Remover item do desconto")
    @DeleteMapping("/{id}/itens/{itemId}")
    ResponseEntity<Void> removerItem(@PathVariable UUID id, @PathVariable UUID itemId);

    @Operation(summary = "Adicionar anexo ao desconto",
            description = "Adiciona anexo ao desconto. Tipos aceitos: imagens (JPEG, PNG, WebP, HEIC), documentos (PDF, DOC, DOCX), áudios (OGG, MP3, WAV, AAC, M4A, etc.) e vídeos (MP4, WebM, MOV, AVI, MKV, etc.), com limite máximo de 15 MB.")
    @PostMapping(value = "/{id}/anexos", consumes = "multipart/form-data")
    ResponseEntity<Void> adicionarAnexo(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo);

    @Operation(summary = "Remover anexo do desconto")
    @DeleteMapping("/{id}/anexos/{anexoId}")
    ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID anexoId);

    @Operation(summary = "Baixar anexo do desconto")
    @GetMapping("/{id}/anexos/{anexoId}/download")
    ResponseEntity<org.springframework.core.io.Resource> baixarAnexo(@PathVariable UUID id, @PathVariable UUID anexoId);

    @Operation(summary = "Validar desconto",
            description = "Confirma o desconto registrando o valor validado, o usuário validador e a data/hora da validação.")
    @PatchMapping("/{id}/validar")
    ResponseEntity<Void> validar(@PathVariable UUID id,
                                 @RequestBody com.nexo.app.modules.desconto.application.dto.DescontoValidacaoRequest request,
                                 @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Baixar desconto (efetivar)",
            description = "Efetiva o desconto registrando o valor baixado, o usuário responsável e a data/hora da baixa.")
    @PatchMapping("/{id}/baixar")
    ResponseEntity<Void> baixar(@PathVariable UUID id,
                                @RequestBody com.nexo.app.modules.desconto.application.dto.DescontoBaixaRequest request,
                                @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Rejeitar desconto")
    @PatchMapping("/{id}/rejeitar")
    ResponseEntity<Void> rejeitar(@PathVariable UUID id, @RequestBody com.nexo.app.modules.desconto.application.dto.DescontoRejeicaoRequest request, @Parameter(hidden = true) Usuario usuario);

    @Operation(summary = "Buscar desconto por ID")
    @GetMapping("/{id}")
    ResponseEntity<DescontoResponse> buscarPorId(@PathVariable UUID id);

    @Operation(summary = "Listar descontos")
    @GetMapping
    ResponseEntity<PageResponse<DescontoResponse>> listar(@ParameterObject DescontoFilter filter, @ParameterObject Pageable pageable);

    @Operation(summary = "Exportar descontos em CSV (Resumido)", description = "Gera um arquivo CSV contendo os dados gerais dos descontos, limitado a 10.000 registros.")
    ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportarResumo(@ParameterObject DescontoFilter filter);

    @Operation(summary = "Exportar descontos em CSV (Detalhado)", description = "Gera um arquivo CSV contendo os dados detalhados dos descontos, limitado a 10.000 registros.")
    ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportarDetalhado(@ParameterObject DescontoFilter filter);
}
