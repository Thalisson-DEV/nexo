package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Um ponto da linha do tempo da nota. Imutável: corrigir uma alteração é fazer outra,
 * e as duas ficam registradas.
 */
@Entity
@Table(name = "execucoes_historico")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoHistoricoNota {

    public static final int TAMANHO_MOTIVO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_execucao", nullable = false, updatable = false)
    private UUID idExecucao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30, updatable = false)
    private TipoEventoNota tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20, updatable = false)
    private OrigemAlteracaoNota origem;

    @Column(name = "id_autor", updatable = false)
    private UUID idAutor;

    @Column(name = "autor_snapshot", nullable = false, length = 200, updatable = false)
    private String autor;

    @Column(name = "motivo", length = TAMANHO_MOTIVO, updatable = false)
    private String motivo;

    @Column(name = "valor_anterior", precision = 12, scale = 2, updatable = false)
    private BigDecimal valorAnterior;

    @Column(name = "valor_novo", precision = 12, scale = 2, updatable = false)
    private BigDecimal valorNovo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalhes", nullable = false, columnDefinition = "jsonb", updatable = false)
    private DetalhesEventoNota detalhes;

    /** Gerado pelo aparelho na alteração feita em campo; garante que o reenvio não duplique. */
    @Column(name = "id_operacao", updatable = false)
    private UUID idOperacao;

    /** Quando a pessoa fez a mudança (no aparelho, pode ser antes de chegar ao servidor). */
    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private OffsetDateTime ocorridoEm;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private OffsetDateTime registradoEm;

    public EventoHistoricoNota(UUID idExecucao, TipoEventoNota tipo, OrigemAlteracaoNota origem, UUID idAutor,
                               String autor, String motivo, BigDecimal valorAnterior, BigDecimal valorNovo,
                               DetalhesEventoNota detalhes, UUID idOperacao, OffsetDateTime ocorridoEm,
                               OffsetDateTime registradoEm) {
        this.idExecucao = Objects.requireNonNull(idExecucao, "Execução é obrigatória");
        this.tipo = Objects.requireNonNull(tipo, "Tipo do evento é obrigatório");
        this.origem = Objects.requireNonNull(origem, "Origem do evento é obrigatória");
        this.idAutor = idAutor;
        this.autor = autor == null || autor.isBlank() ? "SISTEMA" : autor.trim();
        String motivoLimpo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (tipo == TipoEventoNota.ALTERADA && motivoLimpo == null) {
            throw new InvarianteVioladaException("Informe o motivo da alteração.");
        }
        if (motivoLimpo != null && motivoLimpo.length() > TAMANHO_MOTIVO) {
            throw new InvarianteVioladaException("O motivo pode ter no máximo " + TAMANHO_MOTIVO + " caracteres.");
        }
        this.motivo = motivoLimpo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.detalhes = Objects.requireNonNull(detalhes, "Detalhes do evento são obrigatórios");
        this.idOperacao = idOperacao;
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "Data do evento é obrigatória");
        this.registradoEm = Objects.requireNonNull(registradoEm, "Data de registro é obrigatória");
    }
}
