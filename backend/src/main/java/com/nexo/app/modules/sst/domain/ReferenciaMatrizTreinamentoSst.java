package com.nexo.app.modules.sst.domain;
import com.nexo.app.shared.domain.BaseEntity;import jakarta.persistence.*;import lombok.*;import java.util.UUID;
@Entity @Table(name="referencias_matriz_treinamento_sst") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ReferenciaMatrizTreinamentoSst extends BaseEntity{
 public enum Tipo{PRINCIPAL,POP} public enum Status{PENDENTE_ENQUADRAMENTO,ENQUADRADA,REJEITADA}
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Tipo tipo;
 @Column(name="processo_literal",nullable=false,length=240) private String processoLiteral;
 @Column(name="funcao_literal",nullable=false,length=240) private String funcaoLiteral;
 @Column(name="requisitos_literal",nullable=false,columnDefinition="text") private String requisitosLiteral;
 @Column(name="total_horas_fonte") private Integer totalHorasFonte;
 @Column(name="sha256_fonte",nullable=false,length=64) private String sha256Fonte;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
 @Column(name="id_funcao_rh") private UUID idFuncaoRh; @Column(name="id_processo") private UUID idProcesso;
 @Column(name="id_usuario_enquadramento") private UUID idUsuarioEnquadramento;
 @Column(name="enquadrada_em") private java.time.OffsetDateTime enquadradaEm;
 public ReferenciaMatrizTreinamentoSst(Tipo t,String p,String f,String r,Integer total,String hash){tipo=t;processoLiteral=p;funcaoLiteral=f;requisitosLiteral=r;totalHorasFonte=total;sha256Fonte=hash;status=Status.PENDENTE_ENQUADRAMENTO;}
}
