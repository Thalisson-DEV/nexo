package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.EtapaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Parâmetros para filtragem de solicitações de material")
public record SolicitacaoFilter(
    @Schema(description = "Filtrar por estoque de saída", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID idEstoqueOrigem,

    @Schema(description = "Filtrar por estoque que recebeu o material", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    UUID idEstoqueDestino,

    @Schema(description = "Filtrar pela base operacional do estoque de origem")
    UUID idBaseOrigem,

    @Schema(description = "Filtrar pela base operacional do estoque de destino")
    UUID idBaseDestino,

    @Schema(description = "Filtrar por usuário que fez o pedido", example = "c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1")
    UUID idRequisitante,

    @Schema(description = "Filtrar por status da solicitação (PENDENTE, APROVADO, REJEITADO, ENTREGUE, ESTORNADO)")
    StatusSolicitacao status,
    List<StatusSolicitacao> statuses,
    ContextoSolicitacaoMaterial contexto,
    TipoSolicitacaoTecnica tipoTecnico,
    TipoObraSnapshot tipoObra,
    UUID idObra,
    String ocorrencia,
    UUID idEquipe,
    UUID idAlmoxarifado,
    String numeroReserva,
    UUID idAgenteReserva,
    StatusSeparacao statusSeparacao,
    @Schema(description = "Filtrar pela etapa operacional da fila tecnica",
            example = "AGUARDANDO_PLANEJAMENTO",
            allowableValues = {"AGUARDANDO_PLANEJAMENTO", "EM_SEPARACAO", "PENDENTE_ENTREGA"})
    EtapaSolicitacaoTecnica etapa,

    @Schema(description = "Data inicial para filtro por período de solicitação", example = "2024-01-01T00:00:00Z")
    OffsetDateTime dataInicio,

    @Schema(description = "Data final para filtro por período de solicitação", example = "2024-12-31T23:59:59Z")
    OffsetDateTime dataFim,

    @Schema(description = "Referência de mês/ano para filtro (ex: JAN/2026 ou 01/2026)", example = "JAN/2026")
    String referencia,

    @Schema(description = "Filtrar parcialmente pelo número de série de algum material contido na solicitação", example = "MED-123")
    String numSerie,

    @Schema(description = "Busca rápida (LIKE, case/accent-insensitive) por número de série")
    String buscaGlobal,

    @Schema(description = "Saque previsto a partir desta data (inclusive)", example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate previstaDe,

    @Schema(description = "Saque previsto até esta data (inclusive)", example = "2026-10-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate previstaAte
) {
    /** Mantem compatibilidade com consumidores que montam o filtro sem o recorte de saque previsto. */
    public SolicitacaoFilter(
            UUID idEstoqueOrigem,
            UUID idEstoqueDestino,
            UUID idBaseOrigem,
            UUID idBaseDestino,
            UUID idRequisitante,
            StatusSolicitacao status,
            List<StatusSolicitacao> statuses,
            ContextoSolicitacaoMaterial contexto,
            TipoSolicitacaoTecnica tipoTecnico,
            TipoObraSnapshot tipoObra,
            UUID idObra,
            String ocorrencia,
            UUID idEquipe,
            UUID idAlmoxarifado,
            String numeroReserva,
            UUID idAgenteReserva,
            StatusSeparacao statusSeparacao,
            EtapaSolicitacaoTecnica etapa,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            String referencia,
            String numSerie,
            String buscaGlobal
    ) {
        this(idEstoqueOrigem, idEstoqueDestino, idBaseOrigem, idBaseDestino, idRequisitante,
                status, statuses, contexto, tipoTecnico, tipoObra, idObra, ocorrencia, idEquipe,
                idAlmoxarifado, numeroReserva, idAgenteReserva, statusSeparacao, etapa, dataInicio,
                dataFim, referencia, numSerie, buscaGlobal, null, null);
    }

    /** Mantem compatibilidade com consumidores que ainda montam o filtro sem etapa. */
    public SolicitacaoFilter(
            UUID idEstoqueOrigem,
            UUID idEstoqueDestino,
            UUID idBaseOrigem,
            UUID idBaseDestino,
            UUID idRequisitante,
            StatusSolicitacao status,
            List<StatusSolicitacao> statuses,
            ContextoSolicitacaoMaterial contexto,
            TipoSolicitacaoTecnica tipoTecnico,
            TipoObraSnapshot tipoObra,
            UUID idObra,
            String ocorrencia,
            UUID idEquipe,
            UUID idAlmoxarifado,
            String numeroReserva,
            UUID idAgenteReserva,
            StatusSeparacao statusSeparacao,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            String referencia,
            String numSerie,
            String buscaGlobal
    ) {
        this(idEstoqueOrigem, idEstoqueDestino, idBaseOrigem, idBaseDestino, idRequisitante,
                status, statuses, contexto, tipoTecnico, tipoObra, idObra, ocorrencia, idEquipe,
                idAlmoxarifado, numeroReserva, idAgenteReserva, statusSeparacao, null, dataInicio,
                dataFim, referencia, numSerie, buscaGlobal);
    }

    public SolicitacaoFilter comContexto(ContextoSolicitacaoMaterial novoContexto) {
        return new SolicitacaoFilter(
                idEstoqueOrigem, idEstoqueDestino, idBaseOrigem, idBaseDestino, idRequisitante,
                status, statuses, novoContexto, tipoTecnico, tipoObra, idObra, ocorrencia, idEquipe,
                idAlmoxarifado, numeroReserva, idAgenteReserva, statusSeparacao, etapa, dataInicio,
                dataFim, referencia, numSerie, buscaGlobal, previstaDe, previstaAte);
    }

    public SolicitacaoFilter comRequisitante(UUID novoRequisitante) {
        return new SolicitacaoFilter(
                idEstoqueOrigem, idEstoqueDestino, idBaseOrigem, idBaseDestino, novoRequisitante,
                status, statuses, contexto, tipoTecnico, tipoObra, idObra, ocorrencia, idEquipe,
                idAlmoxarifado, numeroReserva, idAgenteReserva, statusSeparacao, etapa, dataInicio,
                dataFim, referencia, numSerie, buscaGlobal, previstaDe, previstaAte);
    }
}
