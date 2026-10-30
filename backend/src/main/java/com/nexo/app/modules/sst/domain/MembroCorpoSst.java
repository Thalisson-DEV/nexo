package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "membros_corpo_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembroCorpoSst extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_corpo", nullable = false)
    private CorpoGovernancaSst corpo;

    @Column(name = "id_colaborador", nullable = false)
    private UUID idColaborador;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome_snapshot", nullable = false, length = 512)
    private String nomeSnapshot;

    @Column(name = "id_funcao_snapshot", nullable = false)
    private UUID idFuncaoSnapshot;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "funcao_snapshot", nullable = false, length = 512)
    private String funcaoSnapshot;

    @Column(name = "id_base_snapshot", nullable = false)
    private UUID idBaseSnapshot;

    @Column(name = "base_snapshot", nullable = false, length = 120)
    private String baseSnapshot;

    @Column(name = "turno_snapshot", length = 80)
    private String turnoSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 40)
    private PapelMembroGovernancaSst papel;

    @Enumerated(EnumType.STRING)
    @Column(name = "representacao", length = 20)
    private RepresentacaoCipa representacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "titularidade", length = 20)
    private TitularidadeCipa titularidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", length = 20)
    private OrigemMembroCipa origem;

    @Column(name = "ciclo_eleitoral", length = 120)
    private String cicloEleitoral;

    @Column(name = "classificacao_eleitoral")
    private Integer classificacaoEleitoral;

    @Column(name = "ato_designacao", length = 180)
    private String atoDesignacao;

    @Column(name = "reeleito", nullable = false)
    private boolean reeleito;

    @Column(name = "inicio_participacao", nullable = false)
    private LocalDate inicioParticipacao;

    @Column(name = "fim_participacao")
    private LocalDate fimParticipacao;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "motivo_saida", length = 2048)
    private String motivoSaida;

    @Column(name = "data_registro_saida")
    private OffsetDateTime dataRegistroSaida;

    @Column(name = "id_usuario_saida")
    private UUID idUsuarioSaida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membro_substituido")
    private MembroCorpoSst membroSubstituido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membro_substituto")
    private MembroCorpoSst membroSubstituto;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "fundamento_substituicao", length = 2048)
    private String fundamentoSubstituicao;

    @Column(name = "vacancia_pendente", nullable = false)
    private boolean vacanciaPendente;

    @Column(name = "conformidade_normativa", nullable = false)
    private boolean conformidadeNormativa = true;

    @Column(name = "codigo_divergencia_normativa", length = 80)
    private String codigoDivergenciaNormativa;

    MembroCorpoSst(
            CorpoGovernancaSst corpo,
            SnapshotMembroGovernancaSst snapshot,
            PapelMembroGovernancaSst papel,
            RepresentacaoCipa representacao,
            TitularidadeCipa titularidade,
            OrigemMembroCipa origem,
            String cicloEleitoral,
            Integer classificacaoEleitoral,
            String atoDesignacao,
            boolean reeleito,
            LocalDate inicioParticipacao) {
        if (corpo == null || snapshot == null || snapshot.idColaborador() == null
                || snapshot.nome() == null || snapshot.nome().isBlank()
                || snapshot.idFuncao() == null || snapshot.funcao() == null || snapshot.funcao().isBlank()
                || snapshot.idBaseOperacional() == null
                || snapshot.baseOperacional() == null || snapshot.baseOperacional().isBlank()) {
            throw new InvarianteVioladaException("O retrato do colaborador fornecido pelo RH está incompleto.");
        }
        if (papel == null || !papel.pertenceA(corpo.getTipo())) {
            throw new InvarianteVioladaException("O papel informado não é compatível com o tipo do corpo.");
        }
        if (inicioParticipacao == null
                || inicioParticipacao.isBefore(corpo.getInicioPeriodo())
                || inicioParticipacao.isAfter(corpo.getFimPeriodo())) {
            throw new InvarianteVioladaException("A entrada do integrante deve ocorrer dentro do período do corpo.");
        }
        this.corpo = corpo;
        this.idColaborador = snapshot.idColaborador();
        this.nomeSnapshot = snapshot.nome().trim();
        this.idFuncaoSnapshot = snapshot.idFuncao();
        this.funcaoSnapshot = snapshot.funcao().trim();
        this.idBaseSnapshot = snapshot.idBaseOperacional();
        this.baseSnapshot = snapshot.baseOperacional().trim();
        this.turnoSnapshot = textoOuNulo(snapshot.turnoDisponivel(), 80, "O turno");
        this.papel = papel;
        this.representacao = representacao;
        this.titularidade = titularidade;
        this.origem = origem;
        this.cicloEleitoral = textoOuNulo(cicloEleitoral, 120, "O ciclo eleitoral");
        this.classificacaoEleitoral = classificacaoEleitoral;
        this.atoDesignacao = textoOuNulo(atoDesignacao, 180, "O ato de designação");
        this.reeleito = reeleito;
        this.inicioParticipacao = inicioParticipacao;
        validarCamposEspecificos(corpo);
    }

    void registrarSaida(LocalDate dataEfeito, String motivo, UUID idUsuario, boolean abrirVacancia) {
        if (!isParticipacaoAtiva()) {
            throw new InvarianteVioladaException("A participação deste integrante já foi encerrada.");
        }
        if (dataEfeito == null || dataEfeito.isBefore(inicioParticipacao)
                || dataEfeito.isAfter(corpo.getFimPeriodo())) {
            throw new InvarianteVioladaException("A data de saída deve respeitar o período de participação.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new InvarianteVioladaException("O motivo da saída do integrante é obrigatório.");
        }
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o responsável pela saída.");
        }
        this.fimParticipacao = dataEfeito;
        this.motivoSaida = limitar(motivo.trim(), 500, "O motivo da saída");
        this.idUsuarioSaida = idUsuario;
        this.dataRegistroSaida = OffsetDateTime.now();
        this.vacanciaPendente = abrirVacancia;
    }

    void vincularSubstituto(MembroCorpoSst substituto, String fundamento) {
        if (substituto == null || substituto == this) {
            throw new InvarianteVioladaException("Informe um sucessor válido para a substituição.");
        }
        if (fundamento == null || fundamento.isBlank()) {
            throw new InvarianteVioladaException("A justificativa da substituição é obrigatória.");
        }
        this.membroSubstituto = substituto;
        this.fundamentoSubstituicao = limitar(fundamento.trim(), 500, "A justificativa da substituição");
        this.vacanciaPendente = false;
        substituto.membroSubstituido = this;
        substituto.fundamentoSubstituicao = this.fundamentoSubstituicao;
    }

    public void registrarDivergenciaNormativa(String codigo) {
        this.conformidadeNormativa = false;
        this.codigoDivergenciaNormativa = textoOuNulo(codigo, 80, "O código da divergência");
    }

    public boolean isParticipacaoAtiva() {
        return fimParticipacao == null;
    }

    public boolean estaAtivoEm(LocalDate data) {
        return data != null && !data.isBefore(inicioParticipacao)
                && (fimParticipacao == null || data.isBefore(fimParticipacao));
    }

    public UUID getIdCorpo() {
        return corpo == null ? null : corpo.getId();
    }

    public UUID getIdMembroSubstituido() {
        return membroSubstituido == null ? null : membroSubstituido.getId();
    }

    public UUID getIdMembroSubstituto() {
        return membroSubstituto == null ? null : membroSubstituto.getId();
    }

    private void validarCamposEspecificos(CorpoGovernancaSst corpo) {
        if (corpo.getTipo() == TipoCorpoGovernancaSst.BRIGADA) {
            if (representacao != null || titularidade != null || origem != null
                    || cicloEleitoral != null || classificacaoEleitoral != null || atoDesignacao != null || reeleito) {
                throw new InvarianteVioladaException(
                        "Campos de representação, eleição e designação da CIPA não se aplicam à Brigada.");
            }
            return;
        }

        if (corpo.getRegimeComposicao() == RegimeComposicaoSst.REPRESENTANTE_NOMEADO) {
            if (papel != PapelMembroGovernancaSst.REPRESENTANTE_NOMEADO
                    || representacao != RepresentacaoCipa.EMPREGADOS
                    || titularidade != null || origem != OrigemMembroCipa.DESIGNADO
                    || atoDesignacao == null || cicloEleitoral != null || classificacaoEleitoral != null || reeleito) {
                throw new InvarianteVioladaException(
                        "O representante nomeado deve ser empregado designado, sem presidente, vice ou suplência.");
            }
            return;
        }

        if (papel == PapelMembroGovernancaSst.REPRESENTANTE_NOMEADO
                || representacao == null || titularidade == null || origem == null) {
            throw new InvarianteVioladaException(
                    "Integrantes da CIPA dimensionada exigem representação, titularidade e origem.");
        }
        if (representacao == RepresentacaoCipa.EMPREGADOR && origem != OrigemMembroCipa.DESIGNADO) {
            throw new InvarianteVioladaException("Representantes do empregador devem possuir origem designada.");
        }
        if (representacao == RepresentacaoCipa.EMPREGADOS && origem != OrigemMembroCipa.ELEITO) {
            throw new InvarianteVioladaException("Representantes dos empregados devem possuir origem eleita.");
        }
        if (origem == OrigemMembroCipa.ELEITO
                && (cicloEleitoral == null || classificacaoEleitoral == null || classificacaoEleitoral < 1)) {
            throw new InvarianteVioladaException(
                    "Integrantes eleitos exigem ciclo eleitoral e classificação maior que zero.");
        }
        if (origem == OrigemMembroCipa.DESIGNADO && atoDesignacao == null) {
            throw new InvarianteVioladaException("Integrantes designados exigem o ato de designação.");
        }
        if (papel == PapelMembroGovernancaSst.PRESIDENTE
                && (representacao != RepresentacaoCipa.EMPREGADOR || origem != OrigemMembroCipa.DESIGNADO)) {
            throw new InvarianteVioladaException("O presidente deve representar o empregador e ser designado.");
        }
        if (papel == PapelMembroGovernancaSst.VICE_PRESIDENTE
                && (representacao != RepresentacaoCipa.EMPREGADOS
                || titularidade != TitularidadeCipa.TITULAR || origem != OrigemMembroCipa.ELEITO)) {
            throw new InvarianteVioladaException(
                    "O vice-presidente deve ser representante titular eleito dos empregados.");
        }
    }

    private static String textoOuNulo(String valor, int limite, String rotulo) {
        return valor == null || valor.isBlank() ? null : limitar(valor.trim(), limite, rotulo);
    }

    private static String limitar(String valor, int limite, String rotulo) {
        if (valor.length() > limite) {
            throw new InvarianteVioladaException(rotulo + " deve ter no máximo " + limite + " caracteres.");
        }
        return valor;
    }
}
