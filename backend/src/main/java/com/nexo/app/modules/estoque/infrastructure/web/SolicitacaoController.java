package com.nexo.app.modules.estoque.infrastructure.web;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.nexo.app.modules.estoque.application.dto.AprovarSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorDetalheResponse;
import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.DefinirSeriesReservadasRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoComercialRequest;
import com.nexo.app.modules.estoque.application.dto.EntregaSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.EstornoParcialRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoComercialResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoSummaryResponse;
import com.nexo.app.modules.estoque.application.services.OperacaoSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.services.AssinaturaSolicitacaoMaterialService;
import com.nexo.app.modules.estoque.application.usecases.ExportarSolicitacoesMaterialUseCase;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoMaterialUseCases;
import com.nexo.app.modules.estoque.doc.SolicitacaoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/solicitacoes-material")
@RequiredArgsConstructor
public class SolicitacaoController implements SolicitacaoControllerDoc {

    private final SolicitacaoMaterialUseCases useCases;
    private final com.nexo.app.modules.estoque.application.usecases.PendenciaSolicitacaoUseCases pendencias;
    private final OperacaoSolicitacaoComercialService operacaoComercial;
    private final AssinaturaSolicitacaoMaterialService assinaturaService;
    private final ExportarSolicitacoesMaterialUseCase exportarUseCase;
    private final com.nexo.app.modules.estoque.application.usecases.ImportarSolicitacaoComercialUseCase importarComercialUseCase;
    private final com.nexo.app.modules.estoque.application.usecases.SeparacaoSolicitacaoTecnicaUseCases separacao;
    private final com.nexo.app.modules.estoque.application.usecases.KanbanSolicitacaoTecnicaUseCases kanban;
    private final com.nexo.app.modules.estoque.application.usecases.ConsumoAnteriorSolicitacaoUseCase consumoAnterior;
    private final com.nexo.app.modules.estoque.application.usecases.ReservaSerieSolicitacaoUseCase reservaSeries;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('estoque:solicitacao:write')")
    public ResponseEntity<UUID> criar(@jakarta.validation.Valid @RequestBody SolicitacaoRequest request) {
        UUID idRequisitante = SecurityUtils.getUsuarioLogadoId();
        UUID id = useCases.criar(request, idRequisitante);
        return ResponseEntity.created(URI.create("/v1/solicitacoes-material/" + id)).body(id);
    }

    @Override
    @PostMapping("/direta")
    @PreAuthorize("hasAuthority('estoque:solicitacao:write')")
    public ResponseEntity<UUID> criarDireta(@jakarta.validation.Valid @RequestBody SolicitacaoRequest request) {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();
        UUID id = useCases.criarDireta(request, idUsuario, idUsuario);
        return ResponseEntity.created(URI.create("/v1/solicitacoes-material/" + id)).body(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<PageResponse<SolicitacaoSummaryResponse>> listar(@ParameterObject SolicitacaoFilter filter, @ParameterObject Pageable pageable) {
        PageResponse<SolicitacaoSummaryResponse> response = useCases.listar(filter, pageable);
        
        if (response.getContent() != null) {
            response.getContent().forEach(summary -> 
                summary.add(linkTo(methodOn(SolicitacaoController.class).buscarPorId(summary.getId())).withSelfRel())
            );
        }
        
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<SolicitacaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorIdCompleto(id));
    }

    @Override
    @GetMapping("/{id}/consumo-anterior")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<ConsumoAnteriorSolicitacaoResponse> consumoAnterior(@PathVariable UUID id) {
        return ResponseEntity.ok(consumoAnterior.consultar(id));
    }

    @Override
    @GetMapping("/{id}/consumo-anterior/{idMaterial}")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<ConsumoAnteriorDetalheResponse> consumoAnteriorDoMaterial(@PathVariable UUID id, @PathVariable UUID idMaterial) {
        return ResponseEntity.ok(consumoAnterior.detalhar(id, idMaterial));
    }

    @Override
    @GetMapping("/{id}/assinaturas/{parte}")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<Resource> baixarAssinatura(
            @PathVariable UUID id,
            @PathVariable AssinaturaSolicitacaoMaterialService.Parte parte) {
        return assinaturaService.baixar(id, parte)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(arquivo.contentType()))
                        .contentLength(arquivo.tamanhoBytes())
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"assinatura.png\"")
                        .body((Resource) new InputStreamResource(arquivo.conteudo())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    @PostMapping("/{id}/aprovar")
    @PreAuthorize("hasAuthority('estoque:solicitacao:approve')")
    public ResponseEntity<Void> aprovar(@PathVariable UUID id, @RequestBody(required = false) AprovarSolicitacaoRequest request) {
        useCases.aprovar(id, SecurityUtils.getUsuarioLogadoId(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/rejeitar")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:write', 'estoque:solicitacao:plan', 'estoque:solicitacao:approve')")
    public ResponseEntity<Void> rejeitar(@PathVariable UUID id, @RequestBody @jakarta.validation.Valid com.nexo.app.modules.estoque.application.dto.RejeitarSolicitacaoRequest request) {
        useCases.rejeitar(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pendencia")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:write', 'estoque:solicitacao:plan')")
    public ResponseEntity<Void> registrarPendencia(@PathVariable UUID id,
            @RequestBody @jakarta.validation.Valid com.nexo.app.modules.estoque.application.dto.RegistrarPendenciaRequest request) {
        pendencias.registrar(id, request);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/pendencia")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:write', 'estoque:solicitacao:plan')")
    public ResponseEntity<Void> removerPendencia(@PathVariable UUID id, @RequestParam(required = false) UUID idItem) {
        pendencias.remover(id, idItem);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/itens/{idItem}/series-reservadas")
    @PreAuthorize("hasAuthority('estoque:solicitacao:write')")
    public ResponseEntity<List<String>> definirSeriesReservadas(
            @PathVariable UUID id, @PathVariable UUID idItem,
            @jakarta.validation.Valid @RequestBody DefinirSeriesReservadasRequest request) {
        return ResponseEntity.ok(reservaSeries.definir(id, idItem, request.series(), SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PostMapping("/{id}/entregar")
    @PreAuthorize("hasAuthority('estoque:solicitacao:write')")
    public ResponseEntity<UUID> registrarEntrega(@PathVariable UUID id, @RequestBody EntregaSolicitacaoRequest request) {
        UUID idOperacao = operacaoComercial.registrarEntrega(id, SecurityUtils.getUsuarioLogadoId(), request);
        return ResponseEntity.ok(idOperacao);
    }

    @Override
    @PostMapping("/{id}/estornar")
    @PreAuthorize("hasAuthority('estoque:solicitacao:write')")
    public ResponseEntity<UUID> estornar(@PathVariable UUID id) {
        UUID idOperacao = operacaoComercial.estornar(id, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.ok(idOperacao);
    }

    @Override
    @PatchMapping("/{id}/estorno-parcial")
    @PreAuthorize("hasAuthority('estoque:solicitacao:write')")
    public ResponseEntity<UUID> estornarParcial(@PathVariable UUID id, @RequestBody @jakarta.validation.Valid EstornoParcialRequest request) {
        UUID idOperacao = operacaoComercial.registrarEstornoParcial(id, SecurityUtils.getUsuarioLogadoId(), request);
        return ResponseEntity.ok(idOperacao);
    }

    @Override
    @GetMapping("/exportar/resumo")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<StreamingResponseBody> exportarResumo(
            @ParameterObject SolicitacaoFilter filter,
            @RequestParam(required = false, defaultValue = "false") Boolean apenasMinhas,
            @RequestParam(required = false, defaultValue = "false") Boolean pacoteZip
    ) {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();

        if (Boolean.TRUE.equals(pacoteZip)) {
            StreamingResponseBody responseBody = outputStream -> {
                exportarUseCase.exportarZipResumo(filter, idUsuario, apenasMinhas, outputStream);
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"solicitacoes_material_resumo.zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(responseBody);
        }

        StreamingResponseBody responseBody = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF'); // BOM para compatibilidade com Microsoft Excel
                exportarUseCase.exportarResumo(filter, idUsuario, apenasMinhas, writer);
            }
        };

        String filename = determinarNomeArquivo("resumo", filter != null ? filter.contexto() : null);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(responseBody);
    }

    @Override
    @GetMapping("/exportar/detalhado")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<StreamingResponseBody> exportarDetalhado(
            @ParameterObject SolicitacaoFilter filter,
            @RequestParam(required = false, defaultValue = "false") Boolean apenasMinhas,
            @RequestParam(required = false, defaultValue = "false") Boolean pacoteZip
    ) {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();

        if (Boolean.TRUE.equals(pacoteZip)) {
            StreamingResponseBody responseBody = outputStream -> {
                exportarUseCase.exportarZipDetalhado(filter, idUsuario, apenasMinhas, outputStream);
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"solicitacoes_material_detalhado.zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(responseBody);
        }

        StreamingResponseBody responseBody = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF'); // BOM para compatibilidade com Microsoft Excel
                exportarUseCase.exportarDetalhado(filter, idUsuario, apenasMinhas, writer);
            }
        };

        String filename = determinarNomeArquivo("detalhado", filter != null ? filter.contexto() : null);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(responseBody);
    }

    private String determinarNomeArquivo(String formato, com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial contexto) {
        if (contexto == com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL) {
            return "solicitacoes_comerciais_" + formato + ".csv";
        }
        if (contexto == com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.TECNICO) {
            return "solicitacoes_tecnicas_" + formato + ".csv";
        }
        return "solicitacoes_consolidado_" + formato + ".csv";
    }

    @Override
    @PostMapping(value = "/comerciais/importar/validar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:import', 'estoque:solicitacao:write')")
    public ResponseEntity<ImportacaoSolicitacaoComercialResponse> validarImportacaoComercial(
            @RequestPart("arquivo") org.springframework.web.multipart.MultipartFile arquivo) {
        return ResponseEntity.ok(importarComercialUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/comerciais/importar/confirmar")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:import', 'estoque:solicitacao:write')")
    public ResponseEntity<ImportacaoSolicitacaoComercialResponse> confirmarImportacaoComercial(
            @RequestBody @jakarta.validation.Valid ConfirmarImportacaoSolicitacaoComercialRequest request) {
        return ResponseEntity.ok(importarComercialUseCase.confirmarImportacao(request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping(value = {"/comerciais/importar/modelo", "/importar/modelo", "/modelo-csv", "/comerciais/modelo"}, produces = "text/csv")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:import', 'estoque:solicitacao:write', 'estoque:solicitacao:read')")
    public ResponseEntity<byte[]> baixarModeloComercialCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-solicitacoes-comerciais.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarComercialUseCase.obterModeloCsv());
    }

    @Override
    @GetMapping("/kanban")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<com.nexo.app.modules.estoque.application.dto.KanbanSolicitacaoTecnicaResponse> obterKanban(
            @ParameterObject SolicitacaoFilter filter
    ) {
        SolicitacaoFilter filtroComercial = filter != null
                ? filter.comContexto(com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL)
                : new SolicitacaoFilter(null, null, null, null, null, null, null,
                        com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        return ResponseEntity.ok(kanban.obter(filtroComercial));
    }

    @Override
    @PostMapping("/{id}/separacao/iniciar")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:separate', 'estoque:solicitacao:write')")
    public ResponseEntity<com.nexo.app.modules.estoque.domain.StatusSeparacao> iniciarSeparacao(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveIdempotencia
    ) {
        String chave = (chaveIdempotencia != null && !chaveIdempotencia.isBlank())
                ? chaveIdempotencia
                : "COMERCIAL_INICIAR_" + id + "_" + request.versaoEsperada();
        return ResponseEntity.ok(separacao.iniciar(
                id, request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId(), chave));
    }

    @Override
    @PostMapping("/{id}/separacao/concluir")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:separate', 'estoque:solicitacao:write')")
    public ResponseEntity<com.nexo.app.modules.estoque.domain.StatusSeparacao> concluirSeparacao(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveIdempotencia
    ) {
        String chave = (chaveIdempotencia != null && !chaveIdempotencia.isBlank())
                ? chaveIdempotencia
                : "COMERCIAL_CONCLUIR_" + id + "_" + request.versaoEsperada();
        return ResponseEntity.ok(separacao.concluir(
                id, request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId(), chave));
    }

    @Override
    @PostMapping("/{id}/separacao/pular")
    @PreAuthorize("hasAnyAuthority('estoque:solicitacao:separate', 'estoque:solicitacao:write')")
    public ResponseEntity<com.nexo.app.modules.estoque.domain.StatusSeparacao> pularSeparacao(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.nexo.app.modules.estoque.application.dto.AlterarSeparacaoTecnicaRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveIdempotencia
    ) {
        String chave = (chaveIdempotencia != null && !chaveIdempotencia.isBlank())
                ? chaveIdempotencia
                : "COMERCIAL_PULAR_" + id + "_" + request.versaoEsperada();
        return ResponseEntity.ok(separacao.pular(
                id, request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId(), chave));
    }
}
