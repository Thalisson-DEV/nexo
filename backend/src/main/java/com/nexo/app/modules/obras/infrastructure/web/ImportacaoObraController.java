package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.CarteiraImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.HistoricoImportacaoResponse;
import com.nexo.app.modules.obras.application.dto.OrcamentoImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import jakarta.validation.Valid;
import com.nexo.app.modules.obras.application.usecases.HistoricoImportacaoUseCases;
import com.nexo.app.modules.obras.application.usecases.ImportarCarteiraUseCase;
import com.nexo.app.modules.obras.doc.ImportacaoObraControllerDoc;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
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

import java.util.UUID;

@RestController
@RequestMapping("/v1/obras/importacoes")
@RequiredArgsConstructor
public class ImportacaoObraController implements ImportacaoObraControllerDoc {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final HistoricoImportacaoUseCases useCases;
    private final ImportarCarteiraUseCase importarCarteiraUseCase;

    @Override
    @GetMapping
    @PreAuthorize("hasAnyAuthority('obras:kit:import', 'obras:obra:import')")
    public ResponseEntity<PageResponse<HistoricoImportacaoResponse>> listar(@RequestParam ImportacaoObra.Tipo tipo,
                                                                            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(tipo, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('obras:kit:import', 'obras:obra:import')")
    public ResponseEntity<HistoricoImportacaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PostMapping(value = "/carteira/validacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> validarCarteira(
            @RequestParam UUID idContrato, @RequestPart MultipartFile arquivo) {
        return ResponseEntity.ok(importarCarteiraUseCase.validarCarteira(idContrato, arquivo));
    }

    @Override
    @PostMapping("/carteira/revalidacao")
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> revalidarCarteira(
            @Valid @RequestBody ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha> request) {
        return ResponseEntity.ok(importarCarteiraUseCase.revalidarCarteira(request));
    }

    @Override
    @PostMapping("/carteira/confirmacao")
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> confirmarCarteira(
            @Valid @RequestBody ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha> request) {
        return ResponseEntity.ok(importarCarteiraUseCase.confirmarCarteira(request));
    }

    @Override
    @GetMapping(value = "/carteira/modelo", produces = XLSX)
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<byte[]> baixarModeloCarteira() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-carteira.xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE, XLSX)
                .body(importarCarteiraUseCase.gerarModeloCarteira());
    }

    @Override
    @PostMapping(value = "/orcamento/validacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> validarOrcamento(
            @RequestPart MultipartFile arquivo) {
        return ResponseEntity.ok(importarCarteiraUseCase.validarOrcamento(arquivo));
    }

    @Override
    @PostMapping("/orcamento/revalidacao")
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> revalidarOrcamento(
            @Valid @RequestBody ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha> request) {
        return ResponseEntity.ok(importarCarteiraUseCase.revalidarOrcamento(request));
    }

    @Override
    @PostMapping("/orcamento/confirmacao")
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> confirmarOrcamento(
            @Valid @RequestBody ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha> request) {
        return ResponseEntity.ok(importarCarteiraUseCase.confirmarOrcamento(request));
    }

    @Override
    @GetMapping(value = "/orcamento/modelo", produces = XLSX)
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<byte[]> baixarModeloOrcamento() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-orcamento.xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE, XLSX)
                .body(importarCarteiraUseCase.gerarModeloOrcamento());
    }
}
