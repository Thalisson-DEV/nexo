package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import java.time.OffsetDateTime;
import java.util.UUID;

@ParameterObject
public record DashboardSolicitacaoFilter(
    @Parameter(description = "Data inicial da solicitação")
    OffsetDateTime dataInicio,

    @Parameter(description = "Data final da solicitação")
    OffsetDateTime dataFim,

    @Parameter(description = "ID do Estoque de Origem")
    UUID idEstoqueOrigem,

    @Parameter(description = "ID do Estoque de Destino")
    UUID idEstoqueDestino,

    @Parameter(description = "ID da Base Operacional (Filtro Cross-Module)")
    UUID idBaseOperacional,

    @Parameter(description = "ID do Processo de Negócio (Filtro Cross-Module)")
    UUID idProcesso,

    @Parameter(description = "Contexto COMERCIAL ou TECNICO; as metricas legadas permanecem comerciais")
    ContextoSolicitacaoMaterial contexto,

    @Parameter(description = "Tipo tecnico informado: 221, 921, 222 ou 922")
    TipoSolicitacaoTecnica tipoTecnico,

    @Parameter(description = "Tipo da obra registrado no snapshot")
    TipoObraSnapshot tipoObra,

    @Parameter(description = "Ocorrencia OC/OS normalizada, quando aplicavel")
    String ocorrencia,

    @Parameter(description = "ID da obra")
    UUID idObra,

    @Parameter(description = "ID da equipe planejada")
    UUID idEquipe,

    @Parameter(description = "Estado da separacao; aplicavel somente a adicoes")
    StatusSeparacao statusSeparacao,

    @Parameter(description = "Almoxarifado da rota de atendimento, sem contabilizacao de saldo tecnico")
    UUID idAlmoxarifadoAtendimento
) {}
