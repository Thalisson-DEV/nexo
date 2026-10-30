package com.nexo.app.modules.sincronizacao.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="casos_sincronizacao") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CasoSincronizacao extends BaseEntity {
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="id_operacao", nullable=false, unique=true, updatable=false)
    private OperacaoSincronizacao operacao;
    @Enumerated(EnumType.STRING) @Column(name="status", nullable=false, length=40) private StatusCasoSincronizacao status=StatusCasoSincronizacao.ABERTO;
    @Column(name="motivo", nullable=false, length=2000) private String motivo;
    @Column(name="id_responsavel") private UUID idResponsavel;
    @Column(name="justificativa_ultima", length=4000) private String justificativaUltima;
    @Column(name="data_resolucao") private OffsetDateTime dataResolucao;
    public CasoSincronizacao(OperacaoSincronizacao op, String motivo){this.operacao=op;this.motivo=motivo;}
    public void atribuir(UUID usuario){exigirAberto();this.idResponsavel=usuario;this.status=StatusCasoSincronizacao.EM_ANALISE;}
    public void corrigir(String justificativa){exigirAberto();exigirJustificativa(justificativa);this.justificativaUltima=justificativa;this.status=StatusCasoSincronizacao.AGUARDANDO_REPROCESSAMENTO;}
    public void resolver(String justificativa){exigirAberto();exigirJustificativa(justificativa);this.justificativaUltima=justificativa;this.status=StatusCasoSincronizacao.RESOLVIDO;this.dataResolucao=OffsetDateTime.now();}
    public void rejeitar(String justificativa){exigirAberto();exigirJustificativa(justificativa);this.justificativaUltima=justificativa;this.status=StatusCasoSincronizacao.REJEITADO;this.dataResolucao=OffsetDateTime.now();}
    public boolean isEncerrado(){return status==StatusCasoSincronizacao.RESOLVIDO || status==StatusCasoSincronizacao.REJEITADO;}
    private void exigirAberto(){if(status==StatusCasoSincronizacao.RESOLVIDO || status==StatusCasoSincronizacao.REJEITADO) throw new InvarianteVioladaException("Este caso ja foi encerrado.");}
    private static void exigirJustificativa(String justificativa){if(justificativa==null || justificativa.isBlank()) throw new InvarianteVioladaException("A justificativa do tratamento e obrigatoria.");}
}
