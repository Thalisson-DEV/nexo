package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Registro idempotente e sem conteúdo livre de uma chamada ao provedor de IA. */
@Entity
@Table(name = "tentativas_geracao_ata_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TentativaGeracaoAtaSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(name = "hash_insumos", nullable = false, updatable = false, length = 64)
    private String hashInsumos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoTentativaGeracaoAtaSst tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusTentativaGeracaoAtaSst status;

    @Column(name = "id_versao_base", updatable = false)
    private UUID idVersaoBase;

    @Column(name = "id_revisao_transcricao", nullable = false, updatable = false)
    private UUID idRevisaoTranscricao;

    @Column(name = "id_solicitante", nullable = false, updatable = false)
    private UUID idSolicitante;

    @Column(name = "id_versao_gerada")
    private UUID idVersaoGerada;

    @Column(name = "codigo_falha", length = 60)
    private String codigoFalha;

    @Column(name = "iniciada_em", nullable = false, updatable = false)
    private OffsetDateTime iniciadaEm;

    @Column(name = "concluida_em")
    private OffsetDateTime concluidaEm;

    public TentativaGeracaoAtaSst(UUID idReuniao, UUID clientRequestId, String hashInsumos,
                                  TipoTentativaGeracaoAtaSst tipo, UUID idVersaoBase,
                                  UUID idRevisaoTranscricao, UUID idSolicitante) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.clientRequestId = Objects.requireNonNull(clientRequestId, "clientRequestId é obrigatório.");
        if (hashInsumos == null || !hashInsumos.matches("[0-9a-f]{64}")) {
            throw new InvarianteVioladaException("O hash dos insumos é inválido.");
        }
        this.hashInsumos = hashInsumos;
        this.tipo = Objects.requireNonNull(tipo, "O tipo da geração é obrigatório.");
        this.idVersaoBase = idVersaoBase;
        this.idRevisaoTranscricao = Objects.requireNonNull(idRevisaoTranscricao,
                "A transcrição confirmada é obrigatória.");
        this.idSolicitante = Objects.requireNonNull(idSolicitante,
                "Não foi possível identificar o solicitante.");
        this.status = StatusTentativaGeracaoAtaSst.EM_PROCESSAMENTO;
        this.iniciadaEm = OffsetDateTime.now();
    }

    public void concluir(UUID idVersaoGerada) {
        exigirEmProcessamento();
        this.idVersaoGerada = Objects.requireNonNull(idVersaoGerada, "A versão gerada é obrigatória.");
        this.status = StatusTentativaGeracaoAtaSst.CONCLUIDA;
        this.concluidaEm = OffsetDateTime.now();
    }

    public void falhar(String codigo) {
        exigirEmProcessamento();
        if (codigo == null || !codigo.matches("[A-Z0-9_]{3,60}")) {
            throw new InvarianteVioladaException("O código da falha é inválido.");
        }
        this.codigoFalha = codigo;
        this.status = StatusTentativaGeracaoAtaSst.FALHA;
        this.concluidaEm = OffsetDateTime.now();
    }

    public boolean mesmosInsumos(String hash) {
        return hashInsumos.equals(hash);
    }

    private void exigirEmProcessamento() {
        if (status != StatusTentativaGeracaoAtaSst.EM_PROCESSAMENTO) {
            throw new InvarianteVioladaException("A tentativa de geração já foi encerrada.");
        }
    }
}
