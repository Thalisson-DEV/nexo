package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailRequest;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailResponse;
import com.nexo.app.modules.financeiro.application.usecases.DecisaoPorEmailUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A página do link de decisão do e-mail. Sem login de propósito: o token no caminho
 * é a credencial, e o serviço confere tudo o mais (prazo, uso, status, alçada).
 */
@RestController
@RequestMapping("/v1/publico/financeiro/decisoes")
@RequiredArgsConstructor
@Tag(name = "Financeiro - Decisão por e-mail", description = "Aprovar ou rejeitar pelo link pessoal enviado ao aprovador")
public class DecisaoPorEmailController {

    private final DecisaoPorEmailUseCases useCases;

    @GetMapping("/{token}")
    @Operation(summary = "Consultar o link", description = "A solicitação e se a decisão ainda cabe por este link.")
    @ApiResponse(responseCode = "404", description = "Link inválido")
    public ResponseEntity<DecisaoEmailResponse> consultar(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCases.consultar(token));
    }

    @PostMapping("/{token}")
    @Operation(summary = "Decidir pelo link", description = "Aprova ou rejeita em nome de quem recebeu o link. Uso único.")
    @ApiResponse(responseCode = "422", description = "Link usado ou expirado, solicitação já decidida ou sem alçada")
    public ResponseEntity<DecisaoEmailResponse> decidir(@PathVariable String token,
                                                        @Valid @RequestBody DecisaoEmailRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCases.decidir(token, request));
    }
}
