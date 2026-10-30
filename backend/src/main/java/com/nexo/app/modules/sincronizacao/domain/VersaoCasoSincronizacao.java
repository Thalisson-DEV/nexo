package com.nexo.app.modules.sincronizacao.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name="versoes_caso_sincronizacao") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class VersaoCasoSincronizacao extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="id_caso", nullable=false, updatable=false) private CasoSincronizacao caso;
    @Column(name="numero", nullable=false, updatable=false) private int numero;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="payload", nullable=false, updatable=false, columnDefinition="jsonb") private Map<String,Object> payload;
    @Column(name="justificativa", nullable=false, updatable=false, length=4000) private String justificativa;
    @Column(name="id_usuario", nullable=false, updatable=false) private UUID idUsuario;
    @Column(name="hash", nullable=false, updatable=false, length=64) private String hash;
    @Column(name="criada_em", nullable=false, updatable=false) private OffsetDateTime criadaEm=OffsetDateTime.now();
    public VersaoCasoSincronizacao(CasoSincronizacao c,int n,Map<String,Object> p,String j,UUID u,String h){caso=c;numero=n;payload=p;justificativa=j;idUsuario=u;hash=h;}
}
