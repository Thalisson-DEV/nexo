package com.nexo.app.modules.sincronizacao.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity @Table(name="anexos_sincronizacao", uniqueConstraints=@UniqueConstraint(name="uk_anexo_local_operacao",columnNames={"id_operacao","id_anexo_local"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AnexoSincronizacao extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_operacao",nullable=false,updatable=false) private OperacaoSincronizacao operacao;
 @Column(name="id_anexo_local",nullable=false,updatable=false) private UUID idAnexoLocal;
 @Column(name="nome",nullable=false,updatable=false,length=255) private String nome;
 @Column(name="content_type",nullable=false,updatable=false,length=150) private String contentType;
 @Column(name="sha256",nullable=false,updatable=false,length=64) private String sha256;
 @JdbcTypeCode(SqlTypes.BINARY) @Basic(fetch=FetchType.LAZY) @Column(name="conteudo",nullable=false,updatable=false,columnDefinition="bytea") private byte[] conteudo;
 @Column(name="tamanho_bytes",nullable=false,updatable=false) private long tamanhoBytes;
 public AnexoSincronizacao(OperacaoSincronizacao o,UUID id,String n,String ct,String sha,byte[] bytes){operacao=o;idAnexoLocal=id;nome=n;contentType=ct;sha256=sha;conteudo=bytes;tamanhoBytes=bytes.length;}
}
