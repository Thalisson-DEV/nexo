package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="homologacoes_instituicao_sst")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class HomologacaoInstituicaoSst extends BaseEntity {
    public enum Status { PENDENTE_CONFIRMACAO, HOMOLOGADA, DESCREDENCIADA }
    @Column(name="id_instituicao",nullable=false,updatable=false) private UUID idInstituicao;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
    @Column(name="status_fonte",nullable=false,length=40) private String statusFonte;
    @Column(name="inicio_literal",length=40) private String inicioLiteral;
    @Column(name="fim_literal",length=40) private String fimLiteral;
    @Column(name="vigencia_inicio") private LocalDate vigenciaInicio;
    @Column(name="vigencia_fim") private LocalDate vigenciaFim;
    @Column(length=240) private String escopo;
    @Column(name="ead_liberado") private Boolean eadLiberado;
    @Column(name="ead_literal",length=80) private String eadLiteral;
    @Column(name="competencia_liberada") private Boolean competenciaLiberada;
    @Column(name="competencia_literal",length=80) private String competenciaLiteral;
    @Column(name="id_documento_evidencia") private UUID idDocumentoEvidencia;
    @Column(name="id_usuario_confirmacao") private UUID idUsuarioConfirmacao;
    @Column(name="confirmada_em") private OffsetDateTime confirmadaEm;

    public HomologacaoInstituicaoSst(UUID instituicao,String statusFonte,String inicio,String fim,
            String eadLiteral,String competenciaLiteral){this.idInstituicao=Objects.requireNonNull(instituicao);
        this.statusFonte=req(statusFonte,40);this.inicioLiteral=op(inicio,40);this.fimLiteral=op(fim,40);
        this.eadLiteral=op(eadLiteral,80);this.competenciaLiteral=op(competenciaLiteral,80);
        this.status=Status.PENDENTE_CONFIRMACAO;}
    public void confirmar(Status status,LocalDate inicio,LocalDate fim,String escopo,Boolean ead,
            Boolean competencia,UUID evidencia,UUID ator){if(status==Status.PENDENTE_CONFIRMACAO)throw new InvarianteVioladaException("Escolha homologada ou descredenciada.");
        if(inicio==null)throw new InvarianteVioladaException("Informe o início da vigência.");if(fim!=null&&fim.isBefore(inicio))throw new InvarianteVioladaException("A vigência final é inválida.");
        this.status=status;this.vigenciaInicio=inicio;this.vigenciaFim=fim;this.escopo=op(escopo,240);
        this.eadLiberado=ead;this.competenciaLiberada=competencia;this.idDocumentoEvidencia=evidencia;
        this.idUsuarioConfirmacao=Objects.requireNonNull(ator);this.confirmadaEm=OffsetDateTime.now();}
    private static String req(String v,int l){if(v==null||v.isBlank()||v.trim().length()>l)throw new InvarianteVioladaException("Valor inválido.");return v.trim();}
    private static String op(String v,int l){return v==null||v.isBlank()?null:req(v,l);}
}
