package com.nexo.app.modules.notificacao.doc;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoRequest;
import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notificações - Políticas de roteamento",
        description = "Matrizes reutilizáveis que escolhem um usuário pelo contexto do gatilho.")
@SecurityRequirement(name = "bearerAuth")
public interface PoliticaRoteamentoControllerDoc {

    @Operation(summary = "Listar políticas de roteamento")
    ResponseEntity<List<PoliticaRoteamentoResponse>> listar();

    @Operation(summary = "Buscar política de roteamento")
    ResponseEntity<PoliticaRoteamentoResponse> buscar(UUID id);

    @Operation(summary = "Criar política de roteamento")
    ResponseEntity<PoliticaRoteamentoResponse> criar(PoliticaRoteamentoRequest request);

    @Operation(summary = "Atualizar política de roteamento")
    ResponseEntity<PoliticaRoteamentoResponse> atualizar(UUID id, PoliticaRoteamentoRequest request);

    @Operation(summary = "Ativar política de roteamento")
    ResponseEntity<PoliticaRoteamentoResponse> ativar(UUID id);

    @Operation(summary = "Desativar política de roteamento")
    ResponseEntity<PoliticaRoteamentoResponse> desativar(UUID id);
}
