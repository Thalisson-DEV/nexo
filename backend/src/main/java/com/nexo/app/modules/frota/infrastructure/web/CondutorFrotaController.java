package com.nexo.app.modules.frota.infrastructure.web;

import com.nexo.app.modules.frota.application.dto.CondutorFilter;
import com.nexo.app.modules.frota.application.dto.CondutorRequest;
import com.nexo.app.modules.frota.application.dto.CondutorResponse;
import com.nexo.app.modules.frota.application.dto.EncerrarPeriodoRequest;
import com.nexo.app.modules.frota.application.dto.VinculoCondutorResponse;
import com.nexo.app.modules.frota.application.usecases.CondutorUseCases;
import com.nexo.app.modules.frota.application.usecases.VinculoCondutorUseCases;
import com.nexo.app.modules.frota.doc.CondutorFrotaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CondutorFrotaController implements CondutorFrotaControllerDoc {

    private final CondutorUseCases condutores;
    private final VinculoCondutorUseCases vinculos;

    @Override
    @GetMapping("/v1/frota/condutores")
    @PreAuthorize("hasAuthority('frota:condutor:read')")
    public ResponseEntity<PageResponse<CondutorResponse>> listar(@ParameterObject CondutorFilter filtro,
                                                                @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(condutores.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/v1/frota/condutores/{idColaborador}")
    @PreAuthorize("hasAuthority('frota:condutor:read')")
    public ResponseEntity<CondutorResponse> buscar(@PathVariable UUID idColaborador) {
        return ResponseEntity.ok(condutores.buscarPorColaborador(idColaborador));
    }

    @Override
    @PutMapping("/v1/frota/condutores/{idColaborador}")
    @PreAuthorize("hasAuthority('frota:condutor:write')")
    public ResponseEntity<CondutorResponse> registrarCnh(@PathVariable UUID idColaborador,
                                                         @Valid @RequestBody CondutorRequest request) {
        return ResponseEntity.ok(condutores.registrarCnh(idColaborador, request));
    }

    @Override
    @DeleteMapping("/v1/frota/condutores/{idColaborador}")
    @PreAuthorize("hasAuthority('frota:condutor:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID idColaborador) {
        condutores.desativar(idColaborador);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/v1/frota/condutores/{idColaborador}/veiculos")
    @PreAuthorize("hasAuthority('frota:condutor:read')")
    public ResponseEntity<PageResponse<VinculoCondutorResponse>> historico(@PathVariable UUID idColaborador,
                                                                          @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(vinculos.historicoDoCondutor(idColaborador, pageable));
    }

    @Override
    @PostMapping("/v1/frota/vinculos-condutor/{idVinculo}/encerramento")
    @PreAuthorize("hasAuthority('frota:condutor:write')")
    public ResponseEntity<VinculoCondutorResponse> encerrarVinculo(@PathVariable UUID idVinculo,
                                                                   @Valid @RequestBody EncerrarPeriodoRequest request) {
        return ResponseEntity.ok(vinculos.encerrar(idVinculo, request.fim()));
    }
}
