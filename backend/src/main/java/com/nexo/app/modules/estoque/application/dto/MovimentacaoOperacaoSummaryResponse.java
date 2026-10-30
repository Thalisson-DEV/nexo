package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "operacoes", itemRelation = "operacao")
@Schema(description = "Resumo agrupado por operação de movimentação (HATEOAS)")
public class MovimentacaoOperacaoSummaryResponse extends RepresentationModel<MovimentacaoOperacaoSummaryResponse> {
    
    @Schema(description = "ID de referência da operação (compartilhado por todos os itens do lote)")
    private UUID idReferenciaOperacao;
    
    private TipoMovimentacao tipo;
    
    private OffsetDateTime dataHora;
    
    @Schema(description = "Nome do usuário que realizou a operação")
    private String operadorNome;
    
    private EstoqueResponse estoqueOrigem;
    
    private EstoqueResponse estoqueDestino;

    @Schema(description = "Contexto da solicitacao que originou a operacao; nulo em ajustes avulsos")
    private ContextoSolicitacaoMaterial contexto;


    private UUID idSolicitacao;

    private TipoSolicitacaoTecnica tipoTecnico;

    @Schema(description = "Snapshot do tipo da obra no instante da operacao")
    private TipoObraSnapshot tipoObra;

    private String ocorrencia;

    private UUID idReserva;

    private UUID idOperacao;

    private UUID idObra;

    private UUID idEquipe;
}
