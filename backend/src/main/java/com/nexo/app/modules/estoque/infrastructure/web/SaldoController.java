package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.SaldoFilter;
import com.nexo.app.modules.estoque.application.dto.SaldoResponse;
import com.nexo.app.modules.estoque.application.dto.SaldoSerieFilter;
import com.nexo.app.modules.estoque.application.dto.SaldoSerieResponse;
import com.nexo.app.modules.estoque.application.dto.VerificarSeriesRequest;
import com.nexo.app.modules.estoque.application.usecases.SaldoUseCases;
import com.nexo.app.modules.estoque.doc.SaldoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/saldos")
@RequiredArgsConstructor
public class SaldoController implements SaldoControllerDoc {

    private final SaldoUseCases useCases;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:saldo:read')")
    public ResponseEntity<PageResponse<SaldoResponse>> listar(@ParameterObject SaldoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/seriais")
    @PreAuthorize("hasAuthority('estoque:saldo:read')")
    public ResponseEntity<PageResponse<SaldoSerieResponse>> listarSeriais(@ParameterObject SaldoSerieFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listarSeriais(filter, pageable));
    }

    @Override
    @PostMapping("/seriais/verificar")
    @PreAuthorize("hasAuthority('estoque:saldo:read')")
    public ResponseEntity<List<SaldoSerieResponse>> verificarSeries(@jakarta.validation.Valid @RequestBody VerificarSeriesRequest request) {
        return ResponseEntity.ok(useCases.verificarSeries(request.series()));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:saldo:read')")
    public ResponseEntity<SaldoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }
}
