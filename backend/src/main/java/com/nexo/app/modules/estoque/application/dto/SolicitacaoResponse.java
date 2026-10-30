package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.domain.AcaoPermitidaSolicitacao;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@Schema(description = "Dados detalhados da solicitação de material para o frontend")
public record SolicitacaoResponse(
    @Schema(description = "ID único da solicitação (UUID v4)")
    UUID id,

    @Schema(description = "Dados completos do estoque de origem")
    EstoqueResponse estoqueOrigem,

    @Schema(description = "Dados completos do estoque de destino")
    EstoqueResponse estoqueDestino,

    @Schema(description = "ID do usuário que realizou o pedido")
    UUID idRequisitante,

    @Schema(description = "ID do usuário que aprovou/rejeitou (Almoxarifado/Gestão)")
    UUID idAprovador,

    @Schema(description = "ID do usuário que realizou a entrega física no almoxarifado")
    UUID idEntregadorAlmox,

    UUID idEquipe,

    UUID idColaboradorSaque,

    boolean saqueForaEquipe,

    @Schema(description = "Status atual da solicitação. Fluxo: PENDENTE -> APROVADO -> ENTREGUE.")
    StatusSolicitacao status,

    @Schema(description = "Data e hora em que o pedido foi criado")
    OffsetDateTime dataSolicitacao,

    @Schema(description = "Data e hora da aprovação ou rejeição")
    OffsetDateTime dataVerificacao,

    @Schema(description = "Data e hora da entrega física do material")
    OffsetDateTime dataEntrega,

    @Schema(description = "Data prevista para o saque dos materiais")
    LocalDate dataPrevista,

    @Schema(description = "Indica que a solicitacao foi registrada com menos de 48 horas de antecedencia")
    boolean foraPrazoSaque,

    String assinaturaAlmox,

    String assinaturaEquipe,

    MotivoRejeicaoResponse motivoRejeicao,

    String justificativaRejeicao,

    MotivoPendenciaResponse motivoPendencia,

    String justificativaPendencia,

    boolean possuiPendencia,

    @Schema(description = "Lista de materiais incluídos na solicitação")
    List<ItemSolicitacaoResponse> itens,

    @Schema(description = "Operacoes comerciais no mesmo formato visual do historico tecnico")
    List<OperacaoSolicitacaoComercialResponse> operacoes,

    @Schema(description = "Tipo de movimentação/técnico da solicitação (ex: 221 para entregas comerciais e adições comuns)")
    TipoSolicitacaoTecnica tipoTecnico,

    @Schema(description = "Contexto, totais e ações do fluxo técnico; nulo para solicitações comerciais")
    DetalhesTecnicosSolicitacaoResponse tecnico,

    @Schema(description = "Contexto explicito do fluxo: COMERCIAL ou TECNICO")
    ContextoSolicitacaoMaterial contexto,

    @Schema(description = "Versao concorrente da solicitacao")
    long versao,

    StatusSeparacao statusSeparacao,

    @Schema(description = "Acoes autorizadas para o usuario no estado atual")
    Set<AcaoPermitidaSolicitacao> acoesPermitidas,

    @Schema(description = "Indica se o saldo da solicitacao esta encerrado")
    boolean encerrada,

    @Schema(description = "Data e hora em que a solicitacao foi encerrada")
    OffsetDateTime encerradaEm,

    @Schema(description = "Motivo do encerramento da solicitacao")
    String motivoEncerramento
) {
    public SolicitacaoResponse(
            UUID id,
            EstoqueResponse estoqueOrigem,
            EstoqueResponse estoqueDestino,
            UUID idRequisitante,
            UUID idAprovador,
            UUID idEntregadorAlmox,
            StatusSolicitacao status,
            OffsetDateTime dataSolicitacao,
            OffsetDateTime dataVerificacao,
            OffsetDateTime dataEntrega,
            String assinaturaAlmox,
            String assinaturaEquipe,
            MotivoRejeicaoResponse motivoRejeicao,
            String justificativaRejeicao,
            List<ItemSolicitacaoResponse> itens,
            DetalhesTecnicosSolicitacaoResponse tecnico
    ) {
        this(id, estoqueOrigem, estoqueDestino, idRequisitante, idAprovador, idEntregadorAlmox,
                null, null, false,
                status, dataSolicitacao, dataVerificacao, dataEntrega, null, false, assinaturaAlmox, assinaturaEquipe,
                motivoRejeicao, justificativaRejeicao, null, null, false, itens, List.of(),
                tecnico != null ? tecnico.tipoTecnico() : null, tecnico, null, 0L, null, Set.of(),
                false, null, null);
    }

    /**
     * Os nomes permanecem no contrato legado, mas assinatura é dado de entrada e
     * evidência protegida, nunca dado de leitura da API.
     */
    public SolicitacaoResponse {
        assinaturaAlmox = null;
        assinaturaEquipe = null;
    }

    public SolicitacaoResponse comTecnico(DetalhesTecnicosSolicitacaoResponse detalhes) {
        return new SolicitacaoResponse(
                id, estoqueOrigem, estoqueDestino, idRequisitante, idAprovador, idEntregadorAlmox,
                idEquipe, idColaboradorSaque, saqueForaEquipe,
                status, dataSolicitacao, dataVerificacao, dataEntrega, dataPrevista, foraPrazoSaque, assinaturaAlmox, assinaturaEquipe,
                motivoRejeicao, justificativaRejeicao, motivoPendencia, justificativaPendencia, possuiPendencia, itens, operacoes,
                detalhes != null && detalhes.tipoTecnico() != null ? detalhes.tipoTecnico() : tipoTecnico,
                detalhes, contexto, versao,
                statusSeparacao, acoesPermitidas, encerrada, encerradaEm, motivoEncerramento);
    }

    public SolicitacaoResponse comAcoesPermitidas(Set<AcaoPermitidaSolicitacao> acoes) {
        return new SolicitacaoResponse(
                id, estoqueOrigem, estoqueDestino, idRequisitante, idAprovador, idEntregadorAlmox,
                idEquipe, idColaboradorSaque, saqueForaEquipe,
                status, dataSolicitacao, dataVerificacao, dataEntrega, dataPrevista, foraPrazoSaque, assinaturaAlmox, assinaturaEquipe,
                motivoRejeicao, justificativaRejeicao, motivoPendencia, justificativaPendencia, possuiPendencia, itens, operacoes, tipoTecnico, tecnico, contexto, versao,
                statusSeparacao, acoes == null ? Set.of() : Set.copyOf(acoes),
                encerrada, encerradaEm, motivoEncerramento);
    }

    public SolicitacaoResponse comOperacoes(List<OperacaoSolicitacaoComercialResponse> novasOperacoes) {
        return new SolicitacaoResponse(
                id, estoqueOrigem, estoqueDestino, idRequisitante, idAprovador, idEntregadorAlmox,
                idEquipe, idColaboradorSaque, saqueForaEquipe,
                status, dataSolicitacao, dataVerificacao, dataEntrega, dataPrevista, foraPrazoSaque, assinaturaAlmox, assinaturaEquipe,
                motivoRejeicao, justificativaRejeicao, motivoPendencia, justificativaPendencia, possuiPendencia, itens,
                novasOperacoes == null ? List.of() : List.copyOf(novasOperacoes), tipoTecnico, tecnico, contexto, versao,
                statusSeparacao, acoesPermitidas, encerrada, encerradaEm, motivoEncerramento);
    }

    @Schema(description = "Detalhes de um item solicitado")
    public record ItemSolicitacaoResponse(
        @Schema(description = "ID único do registro de item")
        UUID id,

        @Schema(description = "Dados completos do material solicitado")
        MaterialResponse material,

        @Schema(description = "Quantidade total do item")
        BigDecimal quantidade,

        @Schema(description = "Quantidade que já foi entregue fisicamente")
        BigDecimal quantidadeEntregue,

        @Schema(description = "Número de série cifrado (se aplicável ao material)")
        String numSerie,

        BigDecimal quantidadeEntregueBruta,

        BigDecimal quantidadeEntregaEstornada,

        BigDecimal quantidadeEntregaLiquida,

        BigDecimal quantidadeDevolvidaBruta,

        BigDecimal quantidadeDevolucaoEstornada,

        BigDecimal quantidadeDevolucaoLiquida,

        BigDecimal quantidadePendente,
        MotivoPendenciaResponse motivoPendencia,
        String justificativaPendencia,

        @Schema(description = "Números de série reservados para o item antes da entrega")
        java.util.List<String> seriesReservadas
    ) {}
}
