package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.ProjecaoSolicitacoesObraResponse;
import com.nexo.app.modules.estoque.application.dto.SaldoObraResponse;
import com.nexo.app.modules.estoque.application.usecases.ProjecaoSolicitacoesObraUseCases;
import com.nexo.app.modules.estoque.application.usecases.SaldoObraUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/obras")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ces.estoque.solicitacoes-tecnicas", name = "enabled",
        havingValue = "true", matchIfMissing = false)
@Tag(name = "Solicitações técnicas por obra")
public class ProjecaoSolicitacoesObraController {

    private final ProjecaoSolicitacoesObraUseCases useCases;
    private final SaldoObraUseCases saldoUseCases;

    @GetMapping("/{idObra}/solicitacoes-material")
    @PreAuthorize("hasAuthority('estoque:solicitacao:read')")
    @Operation(summary = "Consulta as solicitações técnicas da obra")
    public ResponseEntity<ProjecaoSolicitacoesObraResponse> buscar(@PathVariable UUID idObra) {
        return ResponseEntity.ok(useCases.buscar(idObra));
    }

    @GetMapping("/{idObra}/saldo-material")
    @PreAuthorize("hasAuthority('estoque:saldo:read')")
    @Operation(summary = "Consulta o saldo atual do estoque da obra")
    public ResponseEntity<SaldoObraResponse> buscarSaldo(@PathVariable UUID idObra) {
        return ResponseEntity.ok(saldoUseCases.buscar(idObra));
    }
}
