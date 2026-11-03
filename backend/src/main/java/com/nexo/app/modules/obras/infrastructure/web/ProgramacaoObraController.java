package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.EventoProgramacaoFilter;
import com.nexo.app.modules.obras.application.dto.ProgramacaoRequests;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.application.usecases.ConsultarProgramacoesObra;
import com.nexo.app.modules.obras.application.usecases.ProgramacaoObraUseCases;
import com.nexo.app.modules.obras.doc.ProgramacaoObraControllerDoc;
import com.nexo.app.modules.obras.domain.TipoEventoProgramacao;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras")
@RequiredArgsConstructor
public class ProgramacaoObraController implements ProgramacaoObraControllerDoc {

    private static final String LER = "hasAuthority('obras:programacao:read')";
    private static final String GRAVAR = "hasAuthority('obras:programacao:write')";

    private final ProgramacaoObraUseCases useCases;
    private final ConsultarProgramacoesObra consultas;

    @Override
    @PostMapping("/{idObra}/programacoes/previa")
    @PreAuthorize(LER)
    public ResponseEntity<ProgramacaoResponses.Previa> previa(
            @PathVariable UUID idObra, @Valid @RequestBody ProgramacaoRequests.Previa request) {
        return ResponseEntity.ok(useCases.previa(idObra, request));
    }

    @Override
    @PostMapping("/{idObra}/programacoes")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<ProgramacaoResponses.Resultado> programar(
            @PathVariable UUID idObra, @Valid @RequestBody ProgramacaoRequests.Programar request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.programar(idObra, request));
    }

    @Override
    @GetMapping("/{idObra}/programacoes")
    @PreAuthorize(LER)
    public ResponseEntity<List<ProgramacaoResponses.ProgramacaoDetalhe>> listarDaObra(@PathVariable UUID idObra) {
        return ResponseEntity.ok(useCases.listarDaObra(idObra));
    }

    @Override
    @GetMapping("/programacoes/gantt")
    @PreAuthorize(LER)
    public ResponseEntity<ProgramacaoResponses.Gantt> gantt(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID idBase,
            @RequestParam(required = false) UUID idTipoEquipe,
            @RequestParam(required = false) UUID idEquipe) {
        return ResponseEntity.ok(consultas.gantt(inicio, fim, idBase, idTipoEquipe, idEquipe));
    }

    @Override
    @GetMapping("/programacoes/historico")
    @PreAuthorize(LER)
    public ResponseEntity<PageResponse<ProgramacaoResponses.Evento>> historico(
            @RequestParam(required = false) List<TipoEventoProgramacao> tipos,
            @RequestParam(required = false) UUID idObra,
            @RequestParam(required = false) UUID idProgramacao,
            @RequestParam(required = false) String obra,
            @RequestParam(required = false) String equipe,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String mudou,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(consultas.historico(new EventoProgramacaoFilter(tipos, idObra, idProgramacao, obra, equipe, usuario, mudou, de, ate, q), pageable));
    }

    @Override
    @GetMapping("/programacoes/a-programar")
    @PreAuthorize(LER)
    public ResponseEntity<List<ProgramacaoResponses.ObraAProgramar>> obrasAProgramar(@RequestParam(required = false) UUID idBase) {
        return ResponseEntity.ok(consultas.obrasAProgramar(idBase));
    }

    @Override
    @GetMapping("/planejamento/resumos")
    @PreAuthorize("hasAnyAuthority('obras:planejamento:read', 'obras:programacao:read')")
    public ResponseEntity<List<ProgramacaoResponses.ResumoPlanejamento>> resumos(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(consultas.resumos(ids));
    }

    @Override
    @GetMapping("/programacoes/{id}")
    @PreAuthorize(LER)
    public ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PutMapping("/programacoes/{id}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> editar(
            @PathVariable UUID id, @Valid @RequestBody ProgramacaoRequests.Editar request) {
        return ResponseEntity.ok(useCases.editar(id, request));
    }

    @Override
    @PostMapping("/programacoes/{id}/reprogramacao")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> reprogramar(
            @PathVariable UUID id, @Valid @RequestBody ProgramacaoRequests.Reprogramar request) {
        return ResponseEntity.ok(useCases.reprogramar(id, request));
    }

    @Override
    @PostMapping("/programacoes/{id}/cancelamento")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> cancelar(
            @PathVariable UUID id, @Valid @RequestBody ProgramacaoRequests.Encerrar request) {
        return ResponseEntity.ok(useCases.cancelar(id, request));
    }

    @Override
    @PostMapping("/programacoes/{id}/exclusao")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<Void> excluir(
            @PathVariable UUID id, @Valid @RequestBody ProgramacaoRequests.Encerrar request) {
        useCases.excluir(id, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/programacoes/{id}/itens/{idItem}")
    @PreAuthorize(GRAVAR)
    public ResponseEntity<ProgramacaoResponses.ProgramacaoDetalhe> retirarItem(
            @PathVariable UUID id, @PathVariable UUID idItem) {
        return useCases.retirarItem(id, idItem)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
