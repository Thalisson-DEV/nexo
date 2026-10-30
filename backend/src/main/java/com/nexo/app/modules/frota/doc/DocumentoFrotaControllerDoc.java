package com.nexo.app.modules.frota.doc;

import com.nexo.app.modules.frota.application.dto.DocumentoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaRequest;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Frota — Documentos", description = "Documentos de veículos e implementos com vencimento e anexo")
public interface DocumentoFrotaControllerDoc {

    @Operation(summary = "Listar documentos", description = "Filtre por situacao=VENCIDO/A_VENCER para a tela de vencimentos.")
    ResponseEntity<PageResponse<DocumentoFrotaResponse>> listar(DocumentoFrotaFilter filtro, Pageable pageable);

    @Operation(summary = "Buscar documento")
    ResponseEntity<DocumentoFrotaResponse> buscar(UUID id);

    @Operation(summary = "Cadastrar documento", description = "Multipart: parte 'dados' (JSON) e parte opcional 'arquivo' (PDF ou imagem, até 15 MB).")
    ResponseEntity<DocumentoFrotaResponse> criar(DocumentoFrotaRequest dados, MultipartFile arquivo);

    @Operation(summary = "Atualizar metadados do documento")
    ResponseEntity<DocumentoFrotaResponse> atualizar(UUID id, DocumentoFrotaRequest request);

    @Operation(summary = "Anexar ou substituir arquivo")
    ResponseEntity<DocumentoFrotaResponse> anexar(UUID id, MultipartFile arquivo);

    @Operation(summary = "Baixar arquivo do documento")
    ResponseEntity<Resource> baixar(UUID id);

    @Operation(summary = "Excluir documento", description = "Exclusão lógica: sai das listas e dos avisos.")
    ResponseEntity<Void> excluir(UUID id);
}
