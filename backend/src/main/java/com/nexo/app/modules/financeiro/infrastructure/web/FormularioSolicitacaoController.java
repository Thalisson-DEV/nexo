package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.CampoCanonicoDisponivelResponse;
import com.nexo.app.modules.financeiro.application.dto.CampoRequest;
import com.nexo.app.modules.financeiro.application.dto.FormularioFilter;
import com.nexo.app.modules.financeiro.application.dto.FormularioRequest;
import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioSchemaResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioUpdateRequest;
import com.nexo.app.modules.financeiro.application.dto.ReordenarRequest;
import com.nexo.app.modules.financeiro.application.usecases.FormularioSolicitacaoUseCases;
import com.nexo.app.modules.financeiro.doc.FormularioSolicitacaoDoc;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/financeiro/formularios")
@RequiredArgsConstructor
public class FormularioSolicitacaoController implements FormularioSolicitacaoDoc {

    private final FormularioSolicitacaoUseCases useCases;

    // ── Variante ────────────────────────────────────────────────────────────────

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> criar(@Valid @RequestBody FormularioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> atualizar(@PathVariable UUID id,
                                                              @Valid @RequestBody FormularioUpdateRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PostMapping("/{id}/duplicar")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> duplicar(@PathVariable UUID id,
                                                             @Valid @RequestBody FormularioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.duplicar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    // ── Campos ──────────────────────────────────────────────────────────────────

    @Override
    @PostMapping("/{id}/campos")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> adicionarCampo(@PathVariable UUID id,
                                                                   @Valid @RequestBody CampoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.adicionarCampo(id, request));
    }

    @Override
    @PutMapping("/{id}/campos/{idCampo}")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> atualizarCampo(@PathVariable UUID id, @PathVariable UUID idCampo,
                                                                   @Valid @RequestBody CampoRequest request) {
        return ResponseEntity.ok(useCases.atualizarCampo(id, idCampo, request));
    }

    @Override
    @DeleteMapping("/{id}/campos/{idCampo}")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> removerCampo(@PathVariable UUID id, @PathVariable UUID idCampo) {
        return ResponseEntity.ok(useCases.removerCampo(id, idCampo));
    }

    @Override
    @PatchMapping("/{id}/campos/ordenacao")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<FormularioSchemaResponse> reordenarCampos(@PathVariable UUID id,
                                                                    @Valid @RequestBody ReordenarRequest request) {
        return ResponseEntity.ok(useCases.reordenarCampos(id, request.ids()));
    }

    // ── Consultas ───────────────────────────────────────────────────────────────

    @Override
    @GetMapping("/{id}/schema")
    @PreAuthorize("hasAuthority('financeiro:formulario:read')")
    public ResponseEntity<FormularioSchemaResponse> buscarSchema(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarSchema(id));
    }

    @Override
    @GetMapping("/schema")
    @PreAuthorize("hasAuthority('financeiro:formulario:read')")
    public ResponseEntity<FormularioSchemaResponse> buscarSchemaPorCodigo(@RequestParam TipoSolicitacao tipo,
                                                                          @RequestParam String codigo) {
        return ResponseEntity.ok(useCases.buscarSchemaPorCodigo(tipo, codigo));
    }

    @Override
    @GetMapping("/variantes")
    @PreAuthorize("hasAuthority('financeiro:formulario:read')")
    public ResponseEntity<List<FormularioResumoResponse>> listarVariantes(@RequestParam TipoSolicitacao tipo) {
        return ResponseEntity.ok(useCases.listarVariantes(tipo));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('financeiro:formulario:read')")
    public ResponseEntity<PageResponse<FormularioResumoResponse>> listar(@ParameterObject FormularioFilter filtro,
                                                                          @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}/campos-canonicos")
    @PreAuthorize("hasAuthority('financeiro:formulario:write')")
    public ResponseEntity<List<CampoCanonicoDisponivelResponse>> listarCamposCanonicos(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.listarCamposCanonicos(id));
    }
}
