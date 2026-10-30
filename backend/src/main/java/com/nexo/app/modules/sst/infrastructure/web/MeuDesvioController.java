package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.modules.sst.application.usecases.MeuDesvioUseCases;
import com.nexo.app.modules.sst.doc.MeuDesvioControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/desvios/meus")
@RequiredArgsConstructor
public class MeuDesvioController implements MeuDesvioControllerDoc {

    private final MeuDesvioUseCases useCases;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<PageResponse<MeuDesvioResponse>> listar(
            @ParameterObject MeuDesvioFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(
                SecurityUtils.getUsuarioLogadoId(), filter, pageable));
    }

    @Override
    @PutMapping("/{idDesvio}/tratamento")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<MeuDesvioResponse> tratar(
            @PathVariable UUID idDesvio, @Valid @RequestBody TratarDesvioRequest request) {
        return ResponseEntity.ok(useCases.tratar(
                SecurityUtils.getUsuarioLogadoId(), idDesvio, request));
    }

    @Override
    @PutMapping("/tratamento-em-massa")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<TratamentoDesviosEmMassaResponse> tratarEmMassa(
            @Valid @RequestBody TratarDesviosEmMassaRequest request) {
        return ResponseEntity.ok(useCases.tratarEmMassa(
                SecurityUtils.getUsuarioLogadoId(), request));
    }
}
