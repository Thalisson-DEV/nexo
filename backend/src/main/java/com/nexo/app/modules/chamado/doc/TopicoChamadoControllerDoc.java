package com.nexo.app.modules.chamado.doc;

import com.nexo.app.modules.chamado.application.dto.TopicoChamadoFilter;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Chamados - Tópicos", description = "Catálogo, roteamento e políticas de SLA")
@SecurityRequirement(name = "bearerAuth")
public interface TopicoChamadoControllerDoc {
    @Operation(summary = "Listar tópicos ativos para o formulário de abertura")
    ResponseEntity<List<TopicoChamadoResponse>> listarDisponiveis();

    @Operation(summary = "Listar tópicos para administração")
    ResponseEntity<PageResponse<TopicoChamadoResponse>> listarAdministracao(
            TopicoChamadoFilter filtro, Pageable pageable);

    @Operation(summary = "Criar tópico")
    ResponseEntity<TopicoChamadoResponse> criar(TopicoChamadoRequest request);

    @Operation(summary = "Atualizar tópico")
    ResponseEntity<TopicoChamadoResponse> atualizar(UUID id, TopicoChamadoRequest request);

    @Operation(summary = "Ativar tópico")
    ResponseEntity<Void> ativar(UUID id);

    @Operation(summary = "Desativar tópico")
    ResponseEntity<Void> desativar(UUID id);
}
