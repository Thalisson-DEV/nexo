package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Recorte do painel analítico de solicitações de material (comercial, técnico ou geral). */
@ParameterObject
public record PainelSolicitacoesFilter(
    @Parameter(description = "Início do período (data da solicitação). Padrão: 30 dias antes do fim")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    OffsetDateTime dataInicio,

    @Parameter(description = "Fim do período (data da solicitação). Padrão: agora")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    OffsetDateTime dataFim,

    @Parameter(description = "COMERCIAL ou TECNICO; vazio traz os dois processos")
    ContextoSolicitacaoMaterial contexto,

    @Parameter(description = "Base operacional da equipe")
    UUID idBaseOperacional,

    @Parameter(description = "Processo de negócio da equipe")
    UUID idProcesso,

    @Parameter(description = "Equipe (de saque ou dona do estoque de destino)")
    UUID idEquipe,

    @Parameter(description = "Almoxarifado que atende (atendimento técnico ou origem comercial)")
    UUID idAlmoxarifado
) {}
