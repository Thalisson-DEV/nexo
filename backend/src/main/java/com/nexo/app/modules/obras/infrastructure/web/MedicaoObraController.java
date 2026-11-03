package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.MedicaoFilter;
import com.nexo.app.modules.obras.application.dto.MedicaoRequests;
import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.application.usecases.FechamentoObraUseCases;
import com.nexo.app.modules.obras.application.usecases.MedicaoObraUseCases;
import com.nexo.app.modules.obras.application.usecases.PreFechamentoObraUseCases;
import com.nexo.app.modules.obras.application.usecases.RelatorioMedicaoDetalhadoUseCases;
import com.nexo.app.modules.obras.doc.MedicaoObraControllerDoc;
import com.nexo.app.modules.obras.domain.StatusMedicao;
import com.nexo.app.modules.obras.domain.TipoMedicao;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras")
@RequiredArgsConstructor
public class MedicaoObraController implements MedicaoObraControllerDoc {

    private static final String LER = "hasAuthority('obras:medicao:read')";
    private static final String GRAVAR = "hasAuthority('obras:medicao:write')";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final MedicaoObraUseCases useCases;
    private final PreFechamentoObraUseCases preFechamento;
    private final FechamentoObraUseCases fechamento;
    private final RelatorioMedicaoDetalhadoUseCases relatorioDetalhado;

    @Override
    @GetMapping("/pre-fechamento")
    @PreAuthorize("hasAnyAuthority('obras:medicao:read', 'obras:planejamento:read')")
    public ResponseEntity<MedicaoResponses.PreFechamento> preFechamento(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID idBase,
            @RequestParam(defaultValue = "true") boolean apenasPendentes) {
        return ResponseEntity.ok(preFechamento.listar(q, idBase, apenasPendentes));
    }

    @Override
    @PostMapping("/{idObra}/levantamento/conclusao")
    @PreAuthorize("hasAuthority('obras:planejamento:write')")
    public ResponseEntity<MedicaoResponses.LinhaPreFechamento> concluirLevantamento(@PathVariable UUID idObra) {
        return ResponseEntity.ok(preFechamento.concluir(idObra));
    }

    @Override
    @PostMapping("/{idObra}/levantamento/reabertura")
    @PreAuthorize("hasAuthority('obras:planejamento:write')")
    public ResponseEntity<MedicaoResponses.LinhaPreFechamento> reabrirLevantamento(@PathVariable UUID idObra) {
        return ResponseEntity.ok(preFechamento.reabrir(idObra));
    }

    @Override
    @GetMapping("/medicoes/a-medir")
    @PreAuthorize(LER)
    public ResponseEntity<List<MedicaoResponses.ObraAMedir>> obrasAMedir() {
        return ResponseEntity.ok(useCases.obrasAMedir());
    }

    @Override
    @GetMapping("/{idObra}/medicao")
    @PreAuthorize(LER)
    public ResponseEntity<MedicaoResponses.MedicaoDaObra> daObra(@PathVariable UUID idObra) {
        return ResponseEntity.ok(useCases.daObra(idObra));
    }

    @Override
    @GetMapping("/{idObra}/medicao/ciclo")
    @PreAuthorize(LER)
    public ResponseEntity<MedicaoResponses.Ciclo> ciclo(
            @PathVariable UUID idObra,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth competencia) {
        return ResponseEntity.ok(useCases.ciclo(idObra, competencia));
    }

    @Override
    @PostMapping("/{idObra}/medicoes")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<MedicaoResponses.MedicaoDetalhe> gerar(
            @PathVariable UUID idObra, @Valid @RequestBody MedicaoRequests.Gerar request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.gerar(idObra, request));
    }

    @Override
    @GetMapping("/medicoes")
    @PreAuthorize(LER)
    public ResponseEntity<PageResponse<MedicaoResponses.MedicaoResumo>> listar(
            @RequestParam(required = false) UUID idObra,
            @RequestParam(required = false) List<TipoMedicao> tipos,
            @RequestParam(required = false) List<StatusMedicao> status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth competenciaDe,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth competenciaAte,
            @RequestParam(required = false) List<UUID> idsBases,
            @RequestParam(required = false) List<UUID> idsMunicipios,
            @RequestParam(required = false) List<UUID> idsSupervisores,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate geradaDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate geradaAte,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(new MedicaoFilter(idObra, tipos, status, competenciaDe, competenciaAte,
                idsBases, idsMunicipios, idsSupervisores, usuario, geradaDe, geradaAte, q), pageable));
    }

    @Override
    @GetMapping("/medicoes/opcoes")
    @PreAuthorize(LER)
    public ResponseEntity<MedicaoResponses.OpcoesMedicoes> opcoes() {
        return ResponseEntity.ok(useCases.opcoes());
    }

    @Override
    @GetMapping("/medicoes/{id}")
    @PreAuthorize(LER)
    public ResponseEntity<MedicaoResponses.MedicaoDetalhe> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PostMapping("/medicoes/{id}/estorno")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<MedicaoResponses.MedicaoDetalhe> estornar(
            @PathVariable UUID id, @Valid @RequestBody MedicaoRequests.Estornar request) {
        return ResponseEntity.ok(useCases.estornar(id, request));
    }

    @Override
    @PostMapping("/fechamento")
    @PreAuthorize(LER)
    public ResponseEntity<MedicaoResponses.Fechamento> fechamento(@Valid @RequestBody MedicaoRequests.Fechamento request) {
        return ResponseEntity.ok(fechamento.gerar(request.codigos()));
    }

    @Override
    @PostMapping("/fechamento/exportacao")
    @PreAuthorize(LER)
    public ResponseEntity<byte[]> exportarFechamento(@Valid @RequestBody MedicaoRequests.Fechamento request) {
        String nome = "fechamento_obras_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType(XLSX))
                .body(fechamento.exportar(request.codigos()));
    }

    @Override
    @GetMapping(value = "/medicoes/relatorio-detalhado/modelo", produces = XLSX)
    @PreAuthorize(LER)
    public ResponseEntity<byte[]> modeloRelatorioDetalhado() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("modelo-orcado-faturado.xlsx").build().toString())
                .contentType(MediaType.parseMediaType(XLSX))
                .body(relatorioDetalhado.modelo());
    }

    @Override
    @PostMapping(value = "/medicoes/relatorio-detalhado/analise", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(LER)
    public ResponseEntity<MedicaoResponses.AnaliseOrcadoFaturado> analisarRelatorioDetalhado(@RequestPart MultipartFile arquivo) {
        return ResponseEntity.ok(relatorioDetalhado.analisar(arquivo));
    }

    @Override
    @PostMapping(value = "/medicoes/relatorio-detalhado", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(LER)
    public ResponseEntity<byte[]> gerarRelatorioDetalhado(
            @RequestPart MultipartFile arquivo,
            @RequestParam List<UUID> idsObras,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataCorte,
            @RequestParam(required = false) String fiscal,
            @RequestParam(required = false) String responsavel,
            @RequestParam(defaultValue = "true") boolean incluirFotos) {
        String nome = "relatorio_medicao_detalhado_" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(relatorioDetalhado.gerar(arquivo, idsObras, dataCorte, fiscal, responsavel, incluirFotos));
    }
}
