package com.nexo.app.modules.frota.domain;

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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma leitura do odômetro, a fonte única de quilometragem da frota.
 *
 * <p>O registro é somente inserção: checklist, abastecimento, manutenção, telemetria e
 * lançamento manual gravam aqui. Leitura incoerente com as vizinhas não é descartada —
 * fica {@link SituacaoLeituraOdometro#INCONSISTENTE} com o motivo, porque dado de campo
 * offline não pode se perder. Uma leitura errada é anulada com justificativa, nunca
 * apagada.</p>
 */
@Entity
@Table(name = "frota_leituras_odometro")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeituraOdometro extends BaseEntity {

    /** Leituras com hora no futuro além disto são recusadas (relógio do aparelho errado). */
    private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(10);
    private static final long KM_MAXIMO = 9_999_999L;
    private static final java.time.ZoneId FUSO = java.time.ZoneId.of("America/Sao_Paulo");
    private static final java.time.format.DateTimeFormatter FORMATO_DATA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Column(name = "id_veiculo", nullable = false, updatable = false)
    private UUID idVeiculo;

    @Column(name = "km", nullable = false, updatable = false)
    private Long km;

    @Column(name = "lido_em", nullable = false, updatable = false)
    private OffsetDateTime lidoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20, updatable = false)
    private OrigemLeituraOdometro origem;

    @Column(name = "id_origem", updatable = false)
    private UUID idOrigem;

    @Column(name = "id_usuario_registro", updatable = false)
    private UUID idUsuarioRegistro;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoLeituraOdometro situacao;

    @Column(name = "motivo_situacao", length = 500)
    private String motivoSituacao;

    /** KM da leitura válida imediatamente anterior no momento do registro. */
    @Column(name = "km_anterior", updatable = false)
    private Long kmAnterior;

    @Column(name = "data_anulacao")
    private OffsetDateTime dataAnulacao;

    @Column(name = "id_usuario_anulacao")
    private UUID idUsuarioAnulacao;

    @Column(name = "justificativa_anulacao", length = 1000)
    private String justificativaAnulacao;

    /**
     * Registra uma leitura classificando-a contra as leituras válidas vizinhas.
     *
     * @param anterior  última leitura válida com {@code lidoEm} até o instante desta, ou nulo
     * @param posterior primeira leitura válida depois do instante desta, ou nulo (chegada fora de ordem)
     * @param limiteKmDia média diária acima da qual o salto é considerado incoerente
     */
    public static LeituraOdometro registrar(UUID idVeiculo, long km, OffsetDateTime lidoEm,
                                            OrigemLeituraOdometro origem, UUID idOrigem, UUID idUsuario,
                                            LeituraOdometro anterior, LeituraOdometro posterior,
                                            int limiteKmDia) {
        if (idVeiculo == null) throw new InvarianteVioladaException("Informe o veículo.");
        Objects.requireNonNull(origem, "A origem da leitura é obrigatória");
        if (km < 0 || km > KM_MAXIMO) {
            throw new InvarianteVioladaException("Quilometragem inválida.");
        }
        if (lidoEm == null) throw new InvarianteVioladaException("Informe a data e hora da leitura.");
        if (lidoEm.isAfter(OffsetDateTime.now().plus(TOLERANCIA_FUTURO))) {
            throw new InvarianteVioladaException("A data da leitura não pode estar no futuro.");
        }
        if (origem != OrigemLeituraOdometro.MANUAL && idOrigem == null) {
            throw new InvarianteVioladaException("Leituras automáticas precisam do registro de origem.");
        }

        var leitura = new LeituraOdometro();
        leitura.idVeiculo = idVeiculo;
        leitura.km = km;
        leitura.lidoEm = lidoEm;
        leitura.origem = origem;
        leitura.idOrigem = idOrigem;
        leitura.idUsuarioRegistro = idUsuario;
        leitura.kmAnterior = anterior == null ? null : anterior.km;
        leitura.classificar(anterior, posterior, limiteKmDia);
        return leitura;
    }

    public boolean isValida() {
        return situacao == SituacaoLeituraOdometro.VALIDA;
    }

    public boolean isInconsistente() {
        return situacao == SituacaoLeituraOdometro.INCONSISTENTE;
    }

    /** Descarta a leitura do cálculo de KM, guardando quem e por quê. */
    public void anular(UUID idUsuario, String justificativa) {
        if (situacao == SituacaoLeituraOdometro.ANULADA) {
            throw new InvarianteVioladaException("Esta leitura já foi anulada.");
        }
        this.justificativaAnulacao = Textos.obrigatorio(justificativa, "justificativa da anulação", 1000);
        this.situacao = SituacaoLeituraOdometro.ANULADA;
        this.dataAnulacao = OffsetDateTime.now();
        this.idUsuarioAnulacao = idUsuario;
    }

    /** Gestor conferiu a leitura inconsistente e a considera correta. */
    public void confirmar(String justificativa) {
        if (situacao != SituacaoLeituraOdometro.INCONSISTENTE) {
            throw new InvarianteVioladaException("Só leituras inconsistentes podem ser confirmadas.");
        }
        this.situacao = SituacaoLeituraOdometro.VALIDA;
        this.motivoSituacao = "Confirmada pelo gestor: "
                + Textos.obrigatorio(justificativa, "justificativa da confirmação", 400);
    }

    private void classificar(LeituraOdometro anterior, LeituraOdometro posterior, int limiteKmDia) {
        if (anterior != null && km < anterior.km) {
            marcarInconsistente("KM menor que a leitura anterior (" + anterior.km + " km em " + formatar(anterior.lidoEm) + ").");
            return;
        }
        if (posterior != null && km > posterior.km) {
            marcarInconsistente("KM maior que a leitura seguinte (" + posterior.km + " km em " + formatar(posterior.lidoEm) + ").");
            return;
        }
        if (anterior != null && limiteKmDia > 0) {
            long rodado = km - anterior.km;
            double dias = Math.max(1.0, Duration.between(anterior.lidoEm, lidoEm).toMinutes() / 1440.0);
            if (rodado / dias > limiteKmDia) {
                marcarInconsistente(String.format(java.util.Locale.ROOT,
                        "Salto de %d km desde a leitura anterior, acima do limite de %d km por dia.",
                        rodado, limiteKmDia));
                return;
            }
        }
        this.situacao = SituacaoLeituraOdometro.VALIDA;
        this.motivoSituacao = null;
    }

    private static String formatar(OffsetDateTime instante) {
        return FORMATO_DATA.format(instante.atZoneSameInstant(FUSO));
    }

    private void marcarInconsistente(String motivo) {
        this.situacao = SituacaoLeituraOdometro.INCONSISTENTE;
        this.motivoSituacao = motivo;
    }
}
