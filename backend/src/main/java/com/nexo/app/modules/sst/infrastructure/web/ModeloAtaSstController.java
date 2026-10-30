package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.ModeloAtaSstDto;
import com.nexo.app.modules.sst.application.usecases.ModeloAtaSstUseCases;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/modelos-ata")
@RequiredArgsConstructor
public class ModeloAtaSstController {
    private final ModeloAtaSstUseCases useCases;

    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ModeloAtaSstDto.Response> criar(@Valid @RequestBody ModeloAtaSstDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ModeloAtaSstDto.Response> atualizar(
            @PathVariable UUID id, @Valid @RequestBody ModeloAtaSstDto.AtualizarRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<ModeloAtaSstDto.Response> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }
    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<ModeloAtaSstDto.Response>> listar() {
        return ResponseEntity.ok(useCases.listar());
    }

    @GetMapping("/aplicaveis")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<ModeloAtaSstDto.Response>> listarAplicaveis(
            @RequestParam TipoCorpoGovernancaSst tipo,
            @RequestParam(required = false) UUID idBase) {
        return ResponseEntity.ok(useCases.listarAplicaveis(tipo, idBase));
    }
    @PostMapping("/{id}/duplicacao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ModeloAtaSstDto.Response> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.duplicar(id));
    }
    @PostMapping("/{id}/nova-revisao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ModeloAtaSstDto.Response> revisar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criarRevisao(id));
    }
    @PostMapping("/{id}/publicacao")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<ModeloAtaSstDto.Response> publicar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.publicar(id, SecurityUtils.getUsuarioLogadoId()));
    }
    @PostMapping("/{id}/retirada")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<ModeloAtaSstDto.Response> retirar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.retirar(id, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping(value = "/{id}/base-docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ModeloAtaSstDto.ArquivoResponse> baseDocx(
            @PathVariable UUID id, @RequestPart MultipartFile arquivo,
            @RequestParam(required = false) String mapeamentoSlots) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.enviarBaseDocx(
                id, arquivo, mapeamentoSlots, SecurityUtils.getUsuarioLogadoId()));
    }
}
