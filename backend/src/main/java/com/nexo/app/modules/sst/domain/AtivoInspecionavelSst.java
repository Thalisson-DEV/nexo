package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="ativos_inspecionaveis_sst")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AtivoInspecionavelSst extends BaseEntity {
    public enum Tipo { LUZ_EMERGENCIA, EXTINTOR, OUTRO }
    public enum StatusConferencia { RASCUNHO, CONFIRMADO, REJEITADO }
    @Column(name="id_base_operacional",nullable=false,updatable=false) private UUID idBaseOperacional;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Tipo tipo;
    @Column(name="identificador_local",nullable=false,length=80) private String identificadorLocal;
    @Column(nullable=false,length=300) private String localizacao;
    // Metadados físicos do equipamento (agente, capacidade e tipo) não são dados
    // pessoais. Mantê-los em JSON legível também permite a carga inicial via Flyway.
    @AuditIgnore @Column(columnDefinition="text") private String atributos;
    @Column(nullable=false,length=64) private String origem;
    @Enumerated(EnumType.STRING) @Column(name="status_conferencia",nullable=false,length=30) private StatusConferencia statusConferencia;
    @Column(name="possui_dado_suspeito",nullable=false) private boolean possuiDadoSuspeito;
    @Column(name="inicio_vigencia",nullable=false) private LocalDate inicioVigencia;
    @Column(name="fim_vigencia") private LocalDate fimVigencia;
    public AtivoInspecionavelSst(UUID base,Tipo tipo,String id,String local,String atributos,String origem,boolean suspeito){
        this.idBaseOperacional=Objects.requireNonNull(base);this.tipo=Objects.requireNonNull(tipo);this.identificadorLocal=req(id,80);
        this.localizacao=req(local,300);this.atributos=op(atributos,8000);this.origem=req(origem,64);this.possuiDadoSuspeito=suspeito;
        this.statusConferencia=StatusConferencia.RASCUNHO;this.inicioVigencia=LocalDate.now();}
    public void confirmar(){if(statusConferencia!=StatusConferencia.RASCUNHO)throw new InvarianteVioladaException("O ativo já foi conferido.");statusConferencia=StatusConferencia.CONFIRMADO;}
    public void retirar(LocalDate data){if(data==null||data.isBefore(inicioVigencia))throw new InvarianteVioladaException("A data de retirada é inválida.");fimVigencia=data;desativar();}
    private static String req(String v,int l){if(v==null||v.isBlank()||v.trim().length()>l)throw new InvarianteVioladaException("Valor inválido.");return v.trim();}
    private static String op(String v,int l){return v==null||v.isBlank()?null:req(v,l);}
}
