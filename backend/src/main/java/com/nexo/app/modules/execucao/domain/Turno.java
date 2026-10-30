package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Representa um turno de trabalho de uma equipe em campo.
 */
@Entity
@Table(name = "turnos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Turno extends BaseEntity {

    @NotNull(message = "O ID da equipe é obrigatório")
    @Column(name = "id_equipe", nullable = false)
    private UUID idEquipe;

    @NotNull(message = "O ID do usuário de abertura é obrigatório")
    @Column(name = "id_usuario_abertura", nullable = false)
    private UUID idUsuarioAbertura;

    @NotNull(message = "A data de início é obrigatória")
    @Column(name = "data_inicio", nullable = false)
    private OffsetDateTime dataInicio;

    @Column(name = "data_fim")
    private OffsetDateTime dataFim;

    @NotNull(message = "O status do turno é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusTurno status;

    /** Veículo confirmado pelo responsável na abertura do turno. */
    @Column(name = "id_veiculo")
    private UUID idVeiculo;

    /** Retrato dos colaboradores que compõem a equipe neste turno. */
    @ElementCollection
    @CollectionTable(name = "turno_membros", joinColumns = @JoinColumn(name = "id_turno"))
    @Column(name = "id_colaborador", nullable = false)
    private Set<UUID> idsColaboradores = new LinkedHashSet<>();

    /** A equipe esqueceu o turno aberto e o servidor o encerrou no horário de corte. */
    @Column(name = "encerrado_automaticamente", nullable = false)
    private boolean encerradoAutomaticamente;

    /** Informativo do encerramento automático, exibido junto ao turno. */
    @Column(name = "motivo_encerramento", length = 255)
    private String motivoEncerramento;

    public Turno(UUID idEquipe, UUID idUsuarioAbertura) {
        this.idEquipe = Objects.requireNonNull(idEquipe, "ID da equipe é obrigatório");
        this.idUsuarioAbertura = Objects.requireNonNull(idUsuarioAbertura, "ID do usuário de abertura é obrigatório");
        this.dataInicio = OffsetDateTime.now();
        this.status = StatusTurno.ABERTO;
    }

    /**
     * Registra a composição confirmada na abertura (veículo e colaboradores).
     */
    public void registrarComposicao(UUID idVeiculo, Collection<UUID> idsColaboradores) {
        if (!isAberto()) {
            throw new InvarianteVioladaException("Não é possível alterar a composição de um turno fechado.");
        }
        this.idVeiculo = idVeiculo;
        this.idsColaboradores.clear();
        if (idsColaboradores != null) {
            idsColaboradores.stream().filter(Objects::nonNull).forEach(this.idsColaboradores::add);
        }
    }

    /**
     * Finaliza o turno.
     */
    public void fechar() {
        if (this.status == StatusTurno.FECHADO) {
            return;
        }
        this.status = StatusTurno.FECHADO;
        this.dataFim = OffsetDateTime.now();
    }

    /**
     * Encerra um turno esquecido aberto. O fim informado é o do último lançamento —
     * nunca antes da abertura —, não o momento do encerramento, para a jornada não
     * passar do que a equipe de fato trabalhou.
     */
    public void encerrarAutomaticamente(OffsetDateTime fim, String motivo) {
        if (!isAberto()) {
            return;
        }
        Objects.requireNonNull(fim, "O fim do turno é obrigatório");
        this.status = StatusTurno.FECHADO;
        this.dataFim = fim.isBefore(this.dataInicio) ? this.dataInicio : fim;
        this.encerradoAutomaticamente = true;
        this.motivoEncerramento = motivo;
    }

    /**
     * Lançamento que chegou depois do encerramento automático (estava na fila do
     * aparelho): o fim acompanha o lançamento para o turno continuar cobrindo o trabalho.
     */
    public void registrarLancamentoTardio(OffsetDateTime dataLancamento) {
        if (!this.encerradoAutomaticamente || dataLancamento == null) {
            return;
        }
        if (this.dataFim == null || dataLancamento.isAfter(this.dataFim)) {
            this.dataFim = dataLancamento;
        }
    }

    public boolean isAberto() {
        return this.status == StatusTurno.ABERTO;
    }
}
