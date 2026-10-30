package com.nexo.app.modules.desconto.doc;

import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoRequest;
import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoResponse;
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

@Tag(name = "Tipos de Equipamento", description = "Gerenciamento de Tipos de Equipamento para descontos")
public interface TipoEquipamentoDoc {

    @Operation(summary = "Criar tipo de equipamento")
    @PostMapping
    ResponseEntity<TipoEquipamentoResponse> criar(@RequestBody TipoEquipamentoRequest request);

    @Operation(summary = "Atualizar tipo de equipamento")
    @PutMapping("/{id}")
    ResponseEntity<TipoEquipamentoResponse> atualizar(@PathVariable UUID id, @RequestBody TipoEquipamentoRequest request);

    @Operation(summary = "Desativar tipo de equipamento")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> desativar(@PathVariable UUID id);

    @Operation(summary = "Buscar tipo de equipamento por ID")
    @GetMapping("/{id}")
    ResponseEntity<TipoEquipamentoResponse> buscarPorId(@PathVariable UUID id);



    @Operation(summary = "Reativar tipo de equipamento", description = "Reativa um tipo de equipamento inativado logicamente")
    @ApiResponse(responseCode = "204", description = "Tipo de equipamento reativado")
    @ApiResponse(responseCode = "404", description = "Tipo de equipamento não encontrado")
    @PatchMapping("/{id}/ativar")
    ResponseEntity<Void> ativar(@PathVariable UUID id);

    @Operation(summary = "Listar tipos de equipamento", description = "Busca todos os tipos de equipamento com paginação e filtros")
    @ApiResponse(responseCode = "200", description = "Listagem retornada com sucesso")
    @GetMapping
    ResponseEntity<PageResponse<TipoEquipamentoResponse>> listar(
        @ParameterObject com.nexo.app.modules.desconto.application.dto.TipoEquipamentoFilter filter,
        @ParameterObject Pageable pageable);
}
