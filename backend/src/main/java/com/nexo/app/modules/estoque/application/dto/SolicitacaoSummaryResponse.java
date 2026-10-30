package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.AcaoPermitidaSolicitacao;
import com.nexo.app.modules.estoque.domain.EtapaOperacionalSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Relation(collectionRelation = "solicitacoes", itemRelation = "solicitacao")
@Schema(description = "Resumo da solicitação para listagem (HATEOAS)")
public class SolicitacaoSummaryResponse extends RepresentationModel<SolicitacaoSummaryResponse> {
    private UUID id;
    private String estoqueOrigemNome;
    private String estoqueDestinoNome;
    private StatusSolicitacao status;
    private OffsetDateTime dataSolicitacao;
    private OffsetDateTime dataVerificacao;
    private OffsetDateTime dataEntrega;
    private LocalDate dataPrevista;
    @Schema(description = "Indica que a solicitacao foi registrada com menos de 48 horas de antecedencia")
    private boolean foraPrazoSaque;
    private UUID idRequisitante;
    private UUID idAprovador;
    private UUID idEntregadorAlmox;
    private UUID idEquipe;
    private UUID idColaboradorSaque;
    private boolean saqueForaEquipe;
    private int totalItens;
    @Schema(description = "Versao concorrente da solicitacao")
    private long versao;
    @Schema(description = "Contexto explicito do fluxo: COMERCIAL ou TECNICO")
    private ContextoSolicitacaoMaterial contexto;
    private StatusSeparacao statusSeparacao;
    @Schema(description = "Etapa operacional calculada; nula para solicitacoes comerciais")
    private EtapaOperacionalSolicitacao etapaOperacional;
    private Set<AcaoPermitidaSolicitacao> acoesPermitidas;
    @Schema(description = "Indica se o saldo da solicitacao esta encerrado")
    private boolean encerrada;
    private OffsetDateTime encerradaEm;
    private String motivoEncerramento;
    private boolean possuiPendencia;
    @Schema(description = "Motivos de pendência abertos na solicitação e nos itens, sem repetição")
    private List<String> motivosPendencia;
    @Schema(description = "Tipo de movimentação/técnico da solicitação (ex: 221 para entregas comerciais e adições comuns)")
    private TipoSolicitacaoTecnica tipoTecnico;
    private DetalhesTecnicosSolicitacaoResponse tecnico;
}
