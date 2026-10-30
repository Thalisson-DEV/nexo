package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.DesvioDetalhadoResponse;
import com.nexo.app.modules.sst.application.dto.DesvioSegurancaFilter;
import com.nexo.app.modules.sst.application.usecases.DesvioSegurancaUseCases;
import com.nexo.app.modules.sst.doc.DesvioSegurancaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/desvios")
@RequiredArgsConstructor
public class DesvioSegurancaController implements DesvioSegurancaControllerDoc {

    private final DesvioSegurancaUseCases useCases;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<PageResponse<DesvioDetalhadoResponse>> listar(
            @ParameterObject DesvioSegurancaFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/{idDesvio}")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<DesvioDetalhadoResponse> buscarPorId(@PathVariable UUID idDesvio) {
        return ResponseEntity.ok(useCases.buscarPorId(idDesvio));
    }
}
