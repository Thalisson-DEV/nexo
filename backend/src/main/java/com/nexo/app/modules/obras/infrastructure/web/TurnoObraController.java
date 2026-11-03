package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.DownloadAnexoObraDTO;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.application.dto.TurnoObraFilter;
import com.nexo.app.modules.obras.application.dto.TurnoObraRequests;
import com.nexo.app.modules.obras.application.dto.TurnoObraResponses;
import com.nexo.app.modules.obras.application.usecases.ApontamentoExecucaoUseCases;
import com.nexo.app.modules.obras.application.usecases.ConsultarTurnosObra;
import com.nexo.app.modules.obras.application.usecases.TurnoObraUseCases;
import com.nexo.app.modules.obras.doc.TurnoObraControllerDoc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras")
@RequiredArgsConstructor
public class TurnoObraController implements TurnoObraControllerDoc {

    private static final String LER = "hasAuthority('obras:turno:read')";
    private static final String GRAVAR = "hasAuthority('obras:turno:write')";
    private static final String APONTAR = "hasAuthority('obras:execucao:write')";

    private final ConsultarTurnosObra consultas;
    private final TurnoObraUseCases useCases;
    private final ApontamentoExecucaoUseCases apontamentos;

    @Override
    @GetMapping("/turnos")
    @PreAuthorize(LER)
    public ResponseEntity<TurnoObraResponses.Lista> listar(
            @ParameterObject TurnoObraFilter filtro,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(consultas.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/turnos/boletim")
    @PreAuthorize(LER)
    public ResponseEntity<TurnoObraResponses.Boletim> boletim(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) UUID idBase) {
        return ResponseEntity.ok(consultas.boletim(data, idBase));
    }

    @Override
    @GetMapping("/turnos/{id}")
    @PreAuthorize(LER)
    public ResponseEntity<TurnoObraResponses.Detalhe> detalhar(@PathVariable UUID id) {
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PostMapping("/turnos/{id}/atividades")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> registrarAtividade(@PathVariable UUID id, @Valid @RequestBody TurnoObraRequests.Atividade request) {
        useCases.registrarAtividade(id, request);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @DeleteMapping("/turnos/{id}/atividades/{idAtividade}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> removerAtividade(@PathVariable UUID id, @PathVariable UUID idAtividade) {
        useCases.removerAtividade(id, idAtividade);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PutMapping("/turnos/{id}/relatorio")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> salvarRelatorio(@PathVariable UUID id, @Valid @RequestBody TurnoObraRequests.Relatorio request) {
        useCases.salvarRelatorio(id, request);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PostMapping("/turnos/{id}/ocorrencias")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> registrarOcorrencia(@PathVariable UUID id, @Valid @RequestBody TurnoObraRequests.Ocorrencia request) {
        useCases.registrarOcorrencia(id, request);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PutMapping("/turnos/{id}/ocorrencias/{idOcorrencia}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> alterarOcorrencia(@PathVariable UUID id, @PathVariable UUID idOcorrencia, @Valid @RequestBody TurnoObraRequests.Ocorrencia request) {
        useCases.alterarOcorrencia(id, idOcorrencia, request);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @DeleteMapping("/turnos/{id}/ocorrencias/{idOcorrencia}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> removerOcorrencia(@PathVariable UUID id, @PathVariable UUID idOcorrencia) {
        useCases.removerOcorrencia(id, idOcorrencia);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PostMapping("/turnos/{id}/aprs")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> registrarApr(@PathVariable UUID id, @Valid @RequestBody TurnoObraRequests.Apr request) {
        useCases.registrarApr(id, request);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PutMapping("/turnos/{id}/aprs/{idApr}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> alterarApr(@PathVariable UUID id, @PathVariable UUID idApr, @Valid @RequestBody TurnoObraRequests.Apr request) {
        useCases.alterarApr(id, idApr, request);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PostMapping("/turnos/{id}/aprs/{idApr}/conclusao")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> concluirApr(@PathVariable UUID id, @PathVariable UUID idApr) {
        useCases.concluirApr(id, idApr);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @DeleteMapping("/turnos/{id}/aprs/{idApr}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> removerApr(@PathVariable UUID id, @PathVariable UUID idApr) {
        useCases.removerApr(id, idApr);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PostMapping(value = "/turnos/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> anexar(@PathVariable UUID id, @RequestParam(required = false) UUID idApr, @RequestPart MultipartFile arquivo) {
        useCases.anexar(id, idApr, arquivo);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @GetMapping("/turnos/{id}/anexos/{idAnexo}/download")
    @PreAuthorize(LER)
    public ResponseEntity<StreamingResponseBody> baixarAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        DownloadAnexoObraDTO dto = useCases.baixarAnexo(id, idAnexo);
        StreamingResponseBody corpo = saida -> {
            try (java.io.InputStream stream = dto.stream()) {
                stream.transferTo(saida);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.nomeArquivo() + "\"")
                .contentType(MediaType.parseMediaType(dto.contentType()))
                .contentLength(dto.tamanhoBytes())
                .body(corpo);
    }

    @Override
    @DeleteMapping("/turnos/{id}/anexos/{idAnexo}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<TurnoObraResponses.Detalhe> removerAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        useCases.removerAnexo(id, idAnexo);
        return ResponseEntity.ok(consultas.detalhar(id));
    }

    @Override
    @PostMapping("/programacoes/{idProgramacao}/apontamentos")
    @PreAuthorize(APONTAR)
    public ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> apontar(@PathVariable UUID idProgramacao, @Valid @RequestBody TurnoObraRequests.Apontar request) {
        return ResponseEntity.ok(apontamentos.apontar(idProgramacao, request));
    }

    @Override
    @DeleteMapping("/apontamentos/{idApontamento}")
    @PreAuthorize(APONTAR)
    public ResponseEntity<Void> estornar(@PathVariable UUID idApontamento) {
        apontamentos.estornar(idApontamento);
        return ResponseEntity.noContent().build();
    }
}
