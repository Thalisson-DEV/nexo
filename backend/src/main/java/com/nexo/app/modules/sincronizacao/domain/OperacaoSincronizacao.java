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

@Entity
@Table(name = "operacoes_sincronizacao", uniqueConstraints = @UniqueConstraint(name = "uk_operacao_local_usuario", columnNames = {"id_operacao_local", "id_usuario_origem"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperacaoSincronizacao extends BaseEntity {
    @Column(name = "id_operacao_local", nullable = false, updatable = false)
    private UUID idOperacaoLocal;
    @Column(name = "id_registro_local", updatable = false) private UUID idRegistroLocal;
    @Enumerated(EnumType.STRING) @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoOperacaoSincronizacao tipo;
    @Column(name = "id_usuario_origem", nullable = false, updatable = false) private UUID idUsuarioOrigem;
    @Column(name = "instante_origem", nullable = false, updatable = false) private OffsetDateTime instanteOrigem;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload_original", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String,Object> payloadOriginal;
    @Column(name = "hash_original", nullable = false, updatable = false, length = 64) private String hashOriginal;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 35) private StatusOperacaoSincronizacao status = StatusOperacaoSincronizacao.SALVO_NO_SERVIDOR;
    @Column(name = "tentativas", nullable = false) private int tentativas;

    public OperacaoSincronizacao(UUID local, UUID registro, TipoOperacaoSincronizacao tipo, UUID usuario,
                                 OffsetDateTime instante, Map<String,Object> payload, String hash) {
        this.idOperacaoLocal=local; this.idRegistroLocal=registro; this.tipo=tipo; this.idUsuarioOrigem=usuario;
        this.instanteOrigem=instante; this.payloadOriginal=payload; this.hashOriginal=hash;
    }
    public void emTratamento(){ this.status=StatusOperacaoSincronizacao.EM_TRATAMENTO; }
    public void resolvida(){ this.status=StatusOperacaoSincronizacao.RESOLVIDO; }
    public void rejeitada(){ this.status=StatusOperacaoSincronizacao.REJEITADO; }
    public void tentativa(){ this.tentativas++; }
}
