package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Memória do último disparo de uma regra, por escopo. É o que dá sentido ao
 * cooldown.
 *
 * <p>O escopo separa o que deve ser silenciado do que não deve: numa regra por
 * ocorrência ele é o registro de origem (silenciar avisos repetidos sobre <em>o
 * mesmo</em> desconto, sem silenciar os outros); numa regra por limiar é a própria
 * regra, já que a fila pendente é uma só e reavisar a cada varredura seria spam.</p>
 */
@Entity
@Table(name = "execucoes_regra_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoRegra extends BaseEntity {

    public static final String ESCOPO_GLOBAL = "GLOBAL";

    @Column(name = "id_regra", nullable = false, updatable = false)
    private UUID idRegra;

    @Column(name = "chave_escopo", nullable = false, updatable = false, length = 100)
    private String chaveEscopo;

    @Column(name = "ultima_execucao_em", nullable = false)
    private OffsetDateTime ultimaExecucaoEm;

    public ExecucaoRegra(UUID idRegra, String chaveEscopo, OffsetDateTime momento) {
        if (idRegra == null) {
            throw new InvarianteVioladaException("A regra da execução é obrigatória.");
        }
        this.idRegra = idRegra;
        this.chaveEscopo = chaveEscopo == null || chaveEscopo.isBlank() ? ESCOPO_GLOBAL : chaveEscopo;
        this.ultimaExecucaoEm = momento == null ? OffsetDateTime.now() : momento;
    }

    public void registrar(OffsetDateTime momento) {
        this.ultimaExecucaoEm = momento == null ? OffsetDateTime.now() : momento;
    }

    /** Ainda dentro do silêncio configurado pela regra. */
    public boolean emCooldown(int cooldownMinutos, OffsetDateTime momento) {
        if (cooldownMinutos <= 0) {
            return false;
        }
        return this.ultimaExecucaoEm.plusMinutes(cooldownMinutos).isAfter(momento);
    }
}
