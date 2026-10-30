package com.nexo.app.modules.automacao.domain;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Uma versão imutável da definição. Salvar cria outra; a execução guarda qual rodou,
 * para o histórico mostrar o fluxo exatamente como era naquele dia.
 */
@Entity
@Table(name = "automacao_versoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersaoAutomacao extends BaseEntity {

    @Column(name = "id_automacao", nullable = false, updatable = false)
    private UUID idAutomacao;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    /** A própria versão é a trilha da definição; duplicá-la em logs_sistema só pesaria. */
    @AuditIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definicao", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> definicao;

    @Column(name = "nota", length = 300, updatable = false)
    private String nota;

    public VersaoAutomacao(UUID idAutomacao, int numero, Map<String, Object> definicao, String nota) {
        if (idAutomacao == null || numero < 1 || definicao == null) {
            throw new InvarianteVioladaException("Versão de automação incompleta.");
        }
        this.idAutomacao = idAutomacao;
        this.numero = numero;
        this.definicao = definicao;
        this.nota = nota == null ? null : (nota.length() > 300 ? nota.substring(0, 300) : nota);
    }
}
