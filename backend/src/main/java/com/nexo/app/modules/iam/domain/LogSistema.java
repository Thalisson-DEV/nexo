package com.nexo.app.modules.iam.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * Entidade para o Histórico Global de Auditoria.
 *
 * @author <b>Thalisson<b/>
 * @version <b>1.0<b/>
 */
@Entity
@Table(name = "logs_sistema")
@Getter
@Setter
@NoArgsConstructor
public class LogSistema extends BaseEntity {

    @Column(name = "tabela", nullable = false)
    private String tabela;

    @Column(name = "registro_id", nullable = false)
    private UUID registroId;

    @Column(name = "operacao", nullable = false)
    private String operacao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_anteriores", columnDefinition = "jsonb")
    private Map<String, Object> dadosAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_novos", columnDefinition = "jsonb")
    private Map<String, Object> dadosNovos;

    public LogSistema(String tabela, UUID registroId, String operacao, Map<String, Object> dadosAnteriores, Map<String, Object> dadosNovos) {
        this.tabela = tabela;
        this.registroId = registroId;
        this.operacao = operacao;
        this.dadosAnteriores = dadosAnteriores;
        this.dadosNovos = dadosNovos;
    }
}
