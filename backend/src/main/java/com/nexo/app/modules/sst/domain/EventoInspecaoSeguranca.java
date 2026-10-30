package com.nexo.app.modules.sst.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "eventos_inspecao_seguranca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoInspecaoSeguranca {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_inspecao", nullable = false)
    private UUID idInspecao;

    @Column(name = "sequencia", nullable = false)
    private long sequencia;

    @Column(name = "tipo_evento", nullable = false, length = 80)
    private String tipoEvento;

    @Column(name = "id_autor")
    private UUID idAutor;

    @Column(name = "autor_snapshot", nullable = false, length = 200)
    private String autorSnapshot;

    @Column(name = "origem", nullable = false, length = 20)
    private String origem;

    @Column(name = "justificativa", length = 1000)
    private String justificativa;

    @Column(name = "payload_canonico", nullable = false, columnDefinition = "TEXT")
    private String payloadCanonico;

    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    @Column(name = "hash_atual", length = 64)
    private String hashAtual;

    @Column(name = "ocorrido_em", nullable = false)
    private OffsetDateTime ocorridoEm;

    public EventoInspecaoSeguranca(
            UUID idInspecao,
            long sequencia,
            String tipoEvento,
            UUID idAutor,
            String autorSnapshot,
            String origem,
            String justificativa,
            String payloadCanonico,
            OffsetDateTime ocorridoEm
    ) {
        this.idInspecao = Objects.requireNonNull(idInspecao, "ID da inspeção é obrigatório");
        if (sequencia <= 0) {
            throw new IllegalArgumentException("A sequência deve ser positiva");
        }
        this.sequencia = sequencia;
        this.tipoEvento = Objects.requireNonNull(tipoEvento, "Tipo de evento é obrigatório");
        this.idAutor = idAutor;
        this.autorSnapshot = (autorSnapshot != null && !autorSnapshot.isBlank()) ? autorSnapshot.trim() : "Sistema";
        this.origem = (origem != null && !origem.isBlank()) ? origem.trim() : "UI";
        this.justificativa = (justificativa != null && !justificativa.isBlank()) ? justificativa.trim() : null;
        this.payloadCanonico = Objects.requireNonNull(payloadCanonico, "Payload canônico é obrigatório");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "Instante do evento é obrigatório");
    }

    public void selar(String hashAnterior, String hashAtual) {
        if (this.hashAtual != null) {
            throw new IllegalStateException("O evento já foi selado");
        }
        this.hashAnterior = hashAnterior;
        this.hashAtual = Objects.requireNonNull(hashAtual, "Hash atual é obrigatório");
    }

    public void recalibrarHash(String hashAnterior, String hashAtual) {
        this.hashAnterior = hashAnterior;
        this.hashAtual = Objects.requireNonNull(hashAtual, "Hash atual é obrigatório");
    }
}
