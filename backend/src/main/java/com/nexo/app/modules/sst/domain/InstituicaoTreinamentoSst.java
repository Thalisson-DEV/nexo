package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "instituicoes_treinamento_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstituicaoTreinamentoSst extends BaseEntity {
    @Column(length=20) private String cnpj;
    @Column(nullable=false,length=200) private String nome;
    @Column(name="razao_social",length=240) private String razaoSocial;
    @Column(name="parceria_instituicao",length=240) private String parceriaInstituicao;
    @Column(name="inscricao_municipal_literal",length=120) private String inscricaoMunicipalLiteral;
    @Column(length=240) private String local;
    @AuditIgnore @Convert(converter=CryptoConverterImpl.class) @Column(columnDefinition="text") private String telefones;
    @AuditIgnore @Convert(converter=CryptoConverterImpl.class) @Column(columnDefinition="text") private String contatos;
    @Column(name="fonte_referencia",length=255) private String fonteReferencia;
    @Column(name="data_referencia") private LocalDate dataReferencia;
    @Column(name="possui_dado_suspeito",nullable=false) private boolean possuiDadoSuspeito;

    public InstituicaoTreinamentoSst(String cnpj,String nome,String razaoSocial,String parceria,
            String inscricao,String local,String telefones,String contatos,String fonte,
            LocalDate dataReferencia,boolean suspeito){
        this.cnpj=op(cnpj,20);this.nome=req(nome,200);this.razaoSocial=op(razaoSocial,240);
        this.parceriaInstituicao=op(parceria,240);this.inscricaoMunicipalLiteral=op(inscricao,120);
        this.local=op(local,240);this.telefones=op(telefones,4000);this.contatos=op(contatos,4000);
        this.fonteReferencia=op(fonte,255);this.dataReferencia=dataReferencia;this.possuiDadoSuspeito=suspeito;
    }
    private static String req(String v,int l){if(v==null||v.isBlank()||v.trim().length()>l)throw new InvarianteVioladaException("Nome da instituição inválido.");return v.trim();}
    private static String op(String v,int l){return v==null||v.isBlank()?null:req(v,l);}
}
