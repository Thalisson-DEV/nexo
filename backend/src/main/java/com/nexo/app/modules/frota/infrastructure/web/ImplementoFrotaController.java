package com.nexo.app.modules.frota.infrastructure.web;

import com.nexo.app.modules.frota.application.dto.AcoplamentoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.AcoplarImplementoRequest;
import com.nexo.app.modules.frota.application.dto.EncerrarPeriodoRequest;
import com.nexo.app.modules.frota.application.dto.ImplementoFilter;
import com.nexo.app.modules.frota.application.dto.ImplementoRequest;
import com.nexo.app.modules.frota.application.dto.ImplementoResponse;
import com.nexo.app.modules.frota.application.usecases.ImplementoUseCases;
import com.nexo.app.modules.frota.doc.ImplementoFrotaControllerDoc;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/frota/implementos")
@RequiredArgsConstructor
public class ImplementoFrotaController implements ImplementoFrotaControllerDoc {

    private final ImplementoUseCases implementos;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<PageResponse<ImplementoResponse>> listar(@ParameterObject ImplementoFilter filtro,
                                                                  @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(implementos.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<ImplementoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(implementos.buscarPorId(id));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<ImplementoResponse> criar(@Valid @RequestBody ImplementoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(implementos.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<ImplementoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody ImplementoRequest request) {
        return ResponseEntity.ok(implementos.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        implementos.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        implementos.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/acoplamentos")
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<AcoplamentoImplementoResponse> acoplar(@PathVariable UUID id,
                                                                 @Valid @RequestBody AcoplarImplementoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(implementos.acoplar(id, request));
    }

    @Override
    @PostMapping("/{id}/desacoplamento")
    @PreAuthorize("hasAuthority('frota:veiculo:write')")
    public ResponseEntity<AcoplamentoImplementoResponse> desacoplar(@PathVariable UUID id,
                                                                    @Valid @RequestBody EncerrarPeriodoRequest request) {
        return ResponseEntity.ok(implementos.desacoplar(id, request.fim()));
    }

    @Override
    @GetMapping("/{id}/acoplamentos")
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<List<AcoplamentoImplementoResponse>> historico(@PathVariable UUID id) {
        return ResponseEntity.ok(implementos.historicoDoImplemento(id));
    }
}
