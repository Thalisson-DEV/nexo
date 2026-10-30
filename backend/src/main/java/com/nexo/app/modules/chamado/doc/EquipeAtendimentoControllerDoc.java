package com.nexo.app.modules.chamado.doc;

import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoFilter;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoRequest;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Chamados - Equipes", description = "Equipes, membros e roteamento de atendimento")
@SecurityRequirement(name = "bearerAuth")
public interface EquipeAtendimentoControllerDoc {
    @Operation(summary = "Listar equipes")
    ResponseEntity<PageResponse<EquipeAtendimentoResponse>> listar(
            EquipeAtendimentoFilter filtro, Pageable pageable);

    @Operation(summary = "Consultar equipe")
    ResponseEntity<EquipeAtendimentoResponse> buscarPorId(UUID id);

    @Operation(summary = "Criar equipe")
    ResponseEntity<EquipeAtendimentoResponse> criar(EquipeAtendimentoRequest request);

    @Operation(summary = "Atualizar equipe e membros")
    ResponseEntity<EquipeAtendimentoResponse> atualizar(UUID id, EquipeAtendimentoRequest request);

    @Operation(summary = "Ativar equipe")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar equipe")
    ResponseEntity<Void> desativar(UUID id);
}
