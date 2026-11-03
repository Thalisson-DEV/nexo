package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.DocumentacaoTecnicaRequests;
import com.nexo.app.modules.obras.application.dto.DocumentacaoTecnicaResponses;
import com.nexo.app.modules.obras.application.dto.DownloadEvidenciaDTO;
import com.nexo.app.modules.obras.application.usecases.DocumentacaoTecnicaUseCases;
import com.nexo.app.modules.obras.application.usecases.EvidenciaPontoUseCases;
import com.nexo.app.modules.obras.doc.DocumentacaoTecnicaControllerDoc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras/{id}/documentacao")
@RequiredArgsConstructor
public class DocumentacaoTecnicaController implements DocumentacaoTecnicaControllerDoc {

    private static final String LER = "hasAuthority('obras:documentacao:read')";
    private static final String ESCREVER = "hasAuthority('obras:documentacao:write')";

    private final DocumentacaoTecnicaUseCases useCases;
    private final EvidenciaPontoUseCases evidenciaUseCases;

    @Override
    @GetMapping
    @PreAuthorize(LER)
    public ResponseEntity<DocumentacaoTecnicaResponses.Documentacao> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.consultar(id));
    }

    @Override
    @PostMapping("/visitas")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<DocumentacaoTecnicaResponses.Visita> registrarVisita(@PathVariable UUID id, @Valid @RequestBody DocumentacaoTecnicaRequests.Visita request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.registrarVisita(id, request));
    }

    @Override
    @PutMapping("/visitas/{idVisita}")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<DocumentacaoTecnicaResponses.Visita> alterarVisita(@PathVariable UUID id, @PathVariable UUID idVisita, @Valid @RequestBody DocumentacaoTecnicaRequests.Visita request) {
        return ResponseEntity.ok(useCases.alterarVisita(id, idVisita, request));
    }

    @Override
    @DeleteMapping("/visitas/{idVisita}")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<Void> removerVisita(@PathVariable UUID id, @PathVariable UUID idVisita) {
        useCases.removerVisita(id, idVisita);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/visitas/{idVisita}/pdf")
    @PreAuthorize(LER)
    public ResponseEntity<byte[]> pdfDaVisita(@PathVariable UUID id, @PathVariable UUID idVisita) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"visita-previa.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(useCases.pdfDaVisita(id, idVisita));
    }

    @Override
    @PutMapping("/fichas/{idPonto}")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<Void> preencherFicha(@PathVariable UUID id, @PathVariable UUID idPonto, @Valid @RequestBody DocumentacaoTecnicaRequests.Ficha request) {
        useCases.preencherFicha(id, idPonto, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/as-built")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<DocumentacaoTecnicaResponses.Documento> gerarAsBuilt(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(useCases.gerarAsBuilt(id));
    }

    @Override
    @PostMapping(value = "/pontos/{idPonto}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ESCREVER)
    public ResponseEntity<UUID> registrarEvidencia(@PathVariable UUID id, @PathVariable UUID idPonto,
                                                   @RequestParam(required = false) UUID idModelo,
                                                   @RequestParam(required = false) String idItem,
                                                   @RequestParam(required = false) String legenda,
                                                   @RequestParam MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evidenciaUseCases.registrar(id, idPonto, idModelo, idItem, legenda, arquivo));
    }

    @Override
    @PutMapping("/evidencias/{idEvidencia}/legenda")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<Void> alterarLegendaDaEvidencia(@PathVariable UUID id, @PathVariable UUID idEvidencia, @Valid @RequestBody DocumentacaoTecnicaRequests.LegendaEvidencia request) {
        evidenciaUseCases.alterarLegenda(id, idEvidencia, request.legenda());
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/evidencias/{idEvidencia}")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<Void> removerEvidencia(@PathVariable UUID id, @PathVariable UUID idEvidencia) {
        evidenciaUseCases.remover(id, idEvidencia);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/evidencias/{idEvidencia}/arquivo")
    @PreAuthorize(LER)
    public ResponseEntity<StreamingResponseBody> baixarEvidencia(@PathVariable UUID id, @PathVariable UUID idEvidencia) {
        return imagem(evidenciaUseCases.baixar(id, idEvidencia));
    }

    @Override
    @GetMapping("/evidencias/{idEvidencia}/miniatura")
    @PreAuthorize(LER)
    public ResponseEntity<StreamingResponseBody> baixarMiniaturaDaEvidencia(@PathVariable UUID id, @PathVariable UUID idEvidencia) {
        return imagem(evidenciaUseCases.baixarMiniatura(id, idEvidencia));
    }

    /** O arquivo de uma foto não muda, então o navegador pode guardá-lo; "private" porque a resposta exige login. */
    private static ResponseEntity<StreamingResponseBody> imagem(DownloadEvidenciaDTO dto) {
        StreamingResponseBody corpo = saida -> {
            try (InputStream stream = dto.stream()) {
                stream.transferTo(saida);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + dto.nomeArquivo() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .contentType(MediaType.parseMediaType(dto.contentType()))
                .contentLength(dto.tamanhoBytes())
                .body(corpo);
    }

    @Override
    @PutMapping("/equipamentos/{idPonto}")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<Void> definirEquipamento(@PathVariable UUID id, @PathVariable UUID idPonto, @Valid @RequestBody DocumentacaoTecnicaRequests.Equipamento request) {
        useCases.definirEquipamento(id, idPonto, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/equipamentos/{idPonto}")
    @PreAuthorize(ESCREVER)
    public ResponseEntity<Void> removerEquipamento(@PathVariable UUID id, @PathVariable UUID idPonto) {
        useCases.removerEquipamento(id, idPonto);
        return ResponseEntity.noContent().build();
    }
}
