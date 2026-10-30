package com.nexo.app.modules.desconto.doc;

import com.nexo.app.modules.desconto.application.dto.MotivoDescontoRequest;
import com.nexo.app.modules.desconto.application.dto.MotivoDescontoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Motivos de Desconto", description = "Gerenciamento de Motivos de Desconto")
public interface MotivoDescontoDoc {

    @Operation(summary = "Criar motivo de desconto")
    @PostMapping
    ResponseEntity<MotivoDescontoResponse> criar(@RequestBody MotivoDescontoRequest request);

    @Operation(summary = "Atualizar motivo de desconto")
    @PutMapping("/{id}")
    ResponseEntity<MotivoDescontoResponse> atualizar(@PathVariable UUID id, @RequestBody MotivoDescontoRequest request);

    @Operation(summary = "Desativar motivo de desconto")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Buscar motivo de desconto por ID")
    @GetMapping("/{id}")
    ResponseEntity<MotivoDescontoResponse> buscarPorId(@PathVariable UUID id);



    @Operation(summary = "Reativar motivo", description = "Reativa um motivo de desconto inativado logicamente")
    @ApiResponse(responseCode = "204", description = "Motivo reativado")
    @ApiResponse(responseCode = "404", description = "Motivo não encontrado")
    @PatchMapping("/{id}/ativar")
    ResponseEntity<Void> ativar(@PathVariable UUID id);

    @Operation(summary = "Listar motivos", description = "Busca todos os motivos com paginação e filtros")
    @ApiResponse(responseCode = "200", description = "Listagem retornada com sucesso")
    @GetMapping
    ResponseEntity<PageResponse<MotivoDescontoResponse>> listar(
        @ParameterObject com.nexo.app.modules.desconto.application.dto.MotivoDescontoFilter filter,
        @ParameterObject Pageable pageable);
}
