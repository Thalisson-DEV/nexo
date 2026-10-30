package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.application.usecases.MovimentacaoEstoqueUseCases;
import com.nexo.app.modules.estoque.doc.MovimentacaoEstoqueControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.nexo.app.modules.estoque.application.services.AssinaturaTransferenciaService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/v1/movimentacoes-estoque")
@RequiredArgsConstructor
public class MovimentacaoEstoqueController implements MovimentacaoEstoqueControllerDoc {

    private final MovimentacaoEstoqueUseCases useCases;
    private final AssinaturaTransferenciaService assinaturaTransferenciaService;
    private final com.nexo.app.modules.estoque.application.usecases.ImportarMovimentacaoEstoqueUseCase importarMovimentacaoEstoqueUseCase;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:movimentacao:read')")
    public ResponseEntity<PageResponse<MovimentacaoOperacaoSummaryResponse>> listar(@ParameterObject MovimentacaoEstoqueFilter filter, @ParameterObject Pageable pageable) {
        PageResponse<MovimentacaoOperacaoSummaryResponse> response = useCases.listar(filter, pageable);
        
        if (response.getContent() != null) {
            response.getContent().forEach(m -> 
                m.add(linkTo(methodOn(MovimentacaoEstoqueController.class).buscarOperacaoPorIdReferencia(m.getIdReferenciaOperacao())).withSelfRel())
            );
        }
        
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/operacao/{idReferencia}")
    @PreAuthorize("hasAuthority('estoque:movimentacao:read')")
    public ResponseEntity<MovimentacaoOperacaoResponse> buscarOperacaoPorIdReferencia(@PathVariable UUID idReferencia) {
        return ResponseEntity.ok(useCases.buscarOperacaoPorIdReferencia(idReferencia));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:movimentacao:read')")
    public ResponseEntity<MovimentacaoEstoqueResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PostMapping("/entrada")
    @PreAuthorize("hasAuthority('estoque:movimentacao:write')")
    public ResponseEntity<Void> entradaLote(@RequestBody MovimentacaoBulkRequest request) {
        useCases.entradaLote(request, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/baixa")
    @PreAuthorize("hasAuthority('estoque:movimentacao:write')")
    public ResponseEntity<Void> baixaLote(@RequestBody MovimentacaoBulkRequest request) {
        useCases.baixaLote(request, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/transferencia")
    @PreAuthorize("hasAuthority('estoque:movimentacao:write')")
    public ResponseEntity<UUID> transferenciaLote(@RequestBody MovimentacaoBulkRequest request) {
        UUID idReferencia = useCases.transferenciaLote(request, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.ok(idReferencia);
    }

    @Override
    @GetMapping("/operacao/{idReferencia}/assinaturas/{parte}")
    @PreAuthorize("hasAuthority('estoque:movimentacao:read')")
    public ResponseEntity<Resource> baixarAssinatura(
            @PathVariable UUID idReferencia,
            @PathVariable AssinaturaTransferenciaService.ParteTransferencia parte) {
        return assinaturaTransferenciaService.baixar(idReferencia, parte)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(arquivo.contentType()))
                        .contentLength(arquivo.tamanhoBytes())
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"assinatura.png\"")
                        .body((Resource) new InputStreamResource(arquivo.conteudo())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    @PostMapping("/devolucao-comercial")
    @PreAuthorize("hasAuthority('estoque:movimentacao:write')")
    public ResponseEntity<UUID> devolucaoComercial(@jakarta.validation.Valid @RequestBody DevolucaoComercialRequest request) {
        UUID idReferencia = useCases.devolucaoComercial(request, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.ok(idReferencia);
    }

    @Override
    @PostMapping(value = {"/importar", "/importar/validar"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('estoque:movimentacao:write')")
    public ResponseEntity<ImportacaoMovimentacaoResponse> validarImportacao(
            @RequestPart("arquivo") org.springframework.web.multipart.MultipartFile arquivo) {
        return ResponseEntity.ok(importarMovimentacaoEstoqueUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping(value = {"/confirmar-importacao", "/importar/confirmar"})
    @PreAuthorize("hasAuthority('estoque:movimentacao:write')")
    public ResponseEntity<ImportacaoMovimentacaoResponse> confirmarImportacao(
            @RequestBody @jakarta.validation.Valid ConfirmarImportacaoMovimentacaoRequest request) {
        return ResponseEntity.ok(importarMovimentacaoEstoqueUseCase.confirmarImportacao(request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping(value = {"/importar/modelo", "/modelo-csv"}, produces = "text/csv")
    @PreAuthorize("hasAnyAuthority('estoque:movimentacao:write', 'estoque:movimentacao:read')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-movimentacoes-estoque.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarMovimentacaoEstoqueUseCase.obterModeloCsv());
    }
}
