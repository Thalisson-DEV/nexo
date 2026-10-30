package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** Retrato de um membro do corpo congelado para uma reunião específica. */
@Entity
@Table(name = "participacoes_reuniao_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipacaoReuniaoSst extends BaseEntity {

    @Column(name = "id_reuniao", nullable = false, updatable = false)
    private UUID idReuniao;

    @Column(name = "id_membro_corpo", nullable = false, updatable = false)
    private UUID idMembroCorpo;

    @Column(name = "id_colaborador", nullable = false, updatable = false)
    private UUID idColaborador;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome_snapshot", nullable = false, updatable = false, length = 1024)
    private String nomeSnapshot;

    @Column(name = "funcao_snapshot", updatable = false, length = 160)
    private String funcaoSnapshot;

    @Column(name = "papel_snapshot", nullable = false, updatable = false, length = 60)
    private String papelSnapshot;

    @Column(name = "representacao_cipa_snapshot", updatable = false, length = 30)
    private String representacaoCipaSnapshot;

    @Column(name = "titularidade_cipa_snapshot", updatable = false, length = 30)
    private String titularidadeCipaSnapshot;

    @Column(name = "turno_snapshot", updatable = false, length = 80)
    private String turnoSnapshot;

    @Column(name = "obrigatorio", nullable = false, updatable = false)
    private boolean obrigatorio;

    @Enumerated(EnumType.STRING)
    @Column(name = "presenca", nullable = false, length = 40)
    private StatusPresencaReuniaoSst presenca;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "justificativa_ausencia", columnDefinition = "text")
    private String justificativaAusencia;

    @Column(name = "id_usuario_classificacao")
    private UUID idUsuarioClassificacao;

    @Column(name = "classificada_em")
    private OffsetDateTime classificadaEm;

    public ParticipacaoReuniaoSst(
            UUID idReuniao,
            UUID idMembroCorpo,
            UUID idColaborador,
            String nomeSnapshot,
            String funcaoSnapshot,
            String papelSnapshot,
            String representacaoCipaSnapshot,
            String titularidadeCipaSnapshot,
            String turnoSnapshot) {
        this.idReuniao = Objects.requireNonNull(idReuniao, "A reunião é obrigatória.");
        this.idMembroCorpo = Objects.requireNonNull(idMembroCorpo, "O membro do corpo é obrigatório.");
        this.idColaborador = Objects.requireNonNull(idColaborador, "O colaborador é obrigatório.");
        this.nomeSnapshot = exigir(nomeSnapshot, "O nome do participante é obrigatório.");
        this.funcaoSnapshot = opcional(funcaoSnapshot);
        this.papelSnapshot = exigir(papelSnapshot, "O papel do participante é obrigatório.");
        this.representacaoCipaSnapshot = opcional(representacaoCipaSnapshot);
        this.titularidadeCipaSnapshot = opcional(titularidadeCipaSnapshot);
        this.turnoSnapshot = opcional(turnoSnapshot);
        this.obrigatorio = true;
        this.presenca = StatusPresencaReuniaoSst.NAO_REGISTRADA;
    }

    public void registrarPresenca(StatusPresencaReuniaoSst novaPresenca, String justificativa, UUID idUsuario) {
        if (novaPresenca == null || novaPresenca == StatusPresencaReuniaoSst.NAO_REGISTRADA) {
            throw new InvarianteVioladaException("Informe se o participante esteve presente ou ausente.");
        }
        if (novaPresenca == StatusPresencaReuniaoSst.AUSENTE_JUSTIFICADA) {
            this.justificativaAusencia = exigir(justificativa,
                    "Informe uma justificativa objetiva para a ausência.");
            if (this.justificativaAusencia.length() > 1000) {
                throw new InvarianteVioladaException("A justificativa deve ter no máximo 1000 caracteres.");
            }
        } else {
            this.justificativaAusencia = null;
        }
        this.presenca = novaPresenca;
        this.idUsuarioClassificacao = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem registrou a presença.");
        this.classificadaEm = OffsetDateTime.now();
    }

    public boolean pendente() {
        return obrigatorio && presenca == StatusPresencaReuniaoSst.NAO_REGISTRADA;
    }

    public boolean faltaOrdinariaInjustificadaComputavel(TipoCorpoGovernancaSst tipoCorpo,
                                                          TipoReuniaoGovernancaSst tipoReuniao) {
        return tipoCorpo == TipoCorpoGovernancaSst.CIPA
                && tipoReuniao == TipoReuniaoGovernancaSst.ORDINARIA
                && "TITULAR".equals(titularidadeCipaSnapshot)
                && presenca == StatusPresencaReuniaoSst.AUSENTE_NAO_JUSTIFICADA;
    }

    public boolean pertenceA(UUID idReuniao) {
        return this.idReuniao.equals(idReuniao);
    }

    private static String exigir(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) throw new InvarianteVioladaException(mensagem);
        return valor.trim();
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
