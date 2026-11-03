package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de uma edição, reprogramação, cancelamento ou exclusão. Guarda números e códigos como texto para
 * o histórico continuar legível depois de a programação, a obra ou a equipe mudarem.
 */
@Entity
@Table(name = "eventos_programacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProgramacao extends BaseEntity {

    /** Foto da programação antes da mudança, que o caso de uso completa com os textos da obra e da equipe. */
    public record Origem(UUID idEquipe, LocalDate inicio, LocalDate fim) {
    }

    @Column(name = "id_programacao", nullable = false)
    private UUID idProgramacao;

    @Column(name = "numero_programacao", nullable = false)
    private Long numeroProgramacao;

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "numero_obra")
    private String numeroObra;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoEventoProgramacao tipo;

    @Column(name = "id_equipe_origem")
    private UUID idEquipeOrigem;

    @Column(name = "equipe_origem", length = 50)
    private String equipeOrigem;

    @Column(name = "id_equipe_destino")
    private UUID idEquipeDestino;

    @Column(name = "equipe_destino", length = 50)
    private String equipeDestino;

    @Column(name = "inicio_original")
    private LocalDate inicioOriginal;

    @Column(name = "fim_original")
    private LocalDate fimOriginal;

    @Column(name = "inicio_novo")
    private LocalDate inicioNovo;

    @Column(name = "fim_novo")
    private LocalDate fimNovo;

    @Column(name = "observacao", length = 2000)
    private String observacao;

    EventoProgramacao(Programacao programacao, TipoEventoProgramacao tipo, Origem origem, String observacao) {
        this.idProgramacao = programacao.getId();
        this.numeroProgramacao = programacao.getNumero();
        this.idObra = programacao.getIdObra();
        this.tipo = tipo;
        this.idEquipeOrigem = origem.idEquipe();
        this.inicioOriginal = origem.inicio();
        this.fimOriginal = origem.fim();
        this.idEquipeDestino = programacao.getIdEquipe();
        this.inicioNovo = programacao.getDataInicio();
        this.fimNovo = programacao.getDataFim();
        this.observacao = observacao;
    }

    public void identificar(String numeroObra, String equipeOrigem, String equipeDestino) {
        this.numeroObra = numeroObra;
        this.equipeOrigem = equipeOrigem;
        this.equipeDestino = equipeDestino;
    }
}
