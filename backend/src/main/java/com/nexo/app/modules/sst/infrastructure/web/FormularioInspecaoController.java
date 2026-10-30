package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AtualizarFormularioDadosRequest;
import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResumoResponse;
import com.nexo.app.modules.sst.application.dto.ReordenarRequest;
import com.nexo.app.modules.sst.application.usecases.FormularioInspecaoUseCases;
import com.nexo.app.modules.sst.doc.FormularioInspecaoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/formularios-inspecao")
@RequiredArgsConstructor
public class FormularioInspecaoController implements FormularioInspecaoControllerDoc {

    private final FormularioInspecaoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> criar(@Valid @RequestBody CriarFormularioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:formulario:read')")
    public ResponseEntity<FormularioInspecaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:formulario:read')")
    public ResponseEntity<PageResponse<FormularioInspecaoResumoResponse>> listar(
            @ParameterObject FormularioInspecaoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @PutMapping("/{id}/estrutura")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> atualizarEstrutura(
            @PathVariable UUID id, @Valid @RequestBody CriarFormularioRequest request) {
        return ResponseEntity.ok(useCases.atualizarEstrutura(id, request));
    }

    @Override
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> atualizarDados(
            @PathVariable UUID id, @Valid @RequestBody AtualizarFormularioDadosRequest request) {
        return ResponseEntity.ok(useCases.atualizarDados(id, request));
    }

    @Override
    @PostMapping("/{id}/duplicar")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.duplicar(id));
    }

    @Override
    @PatchMapping("/{id}/grupos/ordenacao")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> reordenarGrupos(
            @PathVariable UUID id, @Valid @RequestBody ReordenarRequest request) {
        return ResponseEntity.ok(useCases.reordenarGrupos(id, request));
    }

    @Override
    @PatchMapping("/{id}/grupos/{idGrupo}/perguntas/ordenacao")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> reordenarPerguntas(
            @PathVariable UUID id, @PathVariable UUID idGrupo, @Valid @RequestBody ReordenarRequest request) {
        return ResponseEntity.ok(useCases.reordenarPerguntas(id, idGrupo, request));
    }

    @Override
    @PatchMapping("/{id}/grupos/{idGrupo}/perguntas/{idPergunta}/opcoes/ordenacao")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<FormularioInspecaoResponse> reordenarOpcoes(
            @PathVariable UUID id, @PathVariable UUID idGrupo, @PathVariable UUID idPergunta,
            @Valid @RequestBody ReordenarRequest request) {
        return ResponseEntity.ok(useCases.reordenarOpcoes(id, idGrupo, idPergunta, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('sst:formulario:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
