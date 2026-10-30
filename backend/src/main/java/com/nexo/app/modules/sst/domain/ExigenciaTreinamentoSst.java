package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sst_exigencias_treinamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExigenciaTreinamentoSst extends BaseEntity {

    @Column(name = "id_grupo_versao", nullable = false)
    private UUID idGrupoVersao;

    @Column(name = "revisao_exigencia", nullable = false)
    private int revisaoExigencia;

    @Column(name = "id_exigencia_anterior")
    private UUID idExigenciaAnterior;

    @Column(name = "atual", nullable = false)
    private boolean atual = true;

    @Column(name = "id_treinamento", nullable = false)
    private UUID idTreinamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_corpo", nullable = false, length = 20)
    private TipoCorpoGovernancaSst tipoCorpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_aplicabilidade", nullable = false, length = 30)
    private ModoAplicabilidadeTreinamentoSst modoAplicabilidade;

    @Column(name = "id_funcao")
    private UUID idFuncao;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel_corpo", length = 60)
    private PapelMembroGovernancaSst papelCorpo;

    @Column(name = "id_base")
    private UUID idBase;

    @Column(name = "carga_horaria_minutos", nullable = false)
    private int cargaHorariaMinutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_instituicao", nullable = false, length = 20)
    private PoliticaInstituicaoTreinamentoSst politicaInstituicao;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sst_exigencia_instituicoes",
            joinColumns = @JoinColumn(name = "id_exigencia"))
    @Column(name = "instituicao_normalizada", nullable = false, length = 200)
    private Set<String> instituicoesPermitidas = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade", length = 30)
    private ModalidadeTreinamentoSst modalidade;

    @Column(name = "recorrencia_meses")
    private Integer recorrenciaMeses;

    @Column(name = "antecedencia_alerta_dias", nullable = false)
    private int antecedenciaAlertaDias;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_fonte", nullable = false, length = 20)
    private TipoFonteExigenciaTreinamentoSst tipoFonte;

    @Column(name = "jurisdicao", length = 60)
    private String jurisdicao;

    @Column(name = "identificador_fonte", nullable = false, length = 160)
    private String identificadorFonte;

    @Column(name = "edicao_fonte", length = 80)
    private String edicaoFonte;

    @Column(name = "url_fonte", length = 500)
    private String urlFonte;

    @Column(name = "fonte_confirmada", nullable = false)
    private boolean fonteConfirmada;

    @Column(name = "usuario_confirmacao_fonte")
    private UUID usuarioConfirmacaoFonte;

    @Column(name = "data_confirmacao_fonte")
    private OffsetDateTime dataConfirmacaoFonte;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

    public ExigenciaTreinamentoSst(UUID idTreinamento,
                                  TipoCorpoGovernancaSst tipoCorpo,
                                  ModoAplicabilidadeTreinamentoSst modoAplicabilidade,
                                  UUID idFuncao,
                                  PapelMembroGovernancaSst papelCorpo,
                                  UUID idBase,
                                  int cargaHorariaMinutos,
                                  PoliticaInstituicaoTreinamentoSst politicaInstituicao,
                                  Collection<String> instituicoesPermitidas,
                                  ModalidadeTreinamentoSst modalidade,
                                  Integer recorrenciaMeses,
                                  int antecedenciaAlertaDias,
                                  LocalDate vigenciaInicio,
                                  LocalDate vigenciaFim,
                                  TipoFonteExigenciaTreinamentoSst tipoFonte,
                                  String jurisdicao,
                                  String identificadorFonte,
                                  String edicaoFonte,
                                  String urlFonte,
                                  boolean fonteConfirmada,
                                  UUID usuarioConfirmacaoFonte,
                                  String observacoes) {
        this(UUID.randomUUID(), 1, null, idTreinamento, tipoCorpo, modoAplicabilidade,
                idFuncao, papelCorpo, idBase, cargaHorariaMinutos, politicaInstituicao,
                instituicoesPermitidas, modalidade, recorrenciaMeses, antecedenciaAlertaDias,
                vigenciaInicio, vigenciaFim, tipoFonte, jurisdicao, identificadorFonte,
                edicaoFonte, urlFonte, fonteConfirmada, usuarioConfirmacaoFonte, observacoes);
    }

    private ExigenciaTreinamentoSst(UUID idGrupoVersao,
                                   int revisaoExigencia,
                                   UUID idExigenciaAnterior,
                                   UUID idTreinamento,
                                   TipoCorpoGovernancaSst tipoCorpo,
                                   ModoAplicabilidadeTreinamentoSst modoAplicabilidade,
                                   UUID idFuncao,
                                   PapelMembroGovernancaSst papelCorpo,
                                   UUID idBase,
                                   int cargaHorariaMinutos,
                                   PoliticaInstituicaoTreinamentoSst politicaInstituicao,
                                   Collection<String> instituicoesPermitidas,
                                   ModalidadeTreinamentoSst modalidade,
                                   Integer recorrenciaMeses,
                                   int antecedenciaAlertaDias,
                                   LocalDate vigenciaInicio,
                                   LocalDate vigenciaFim,
                                   TipoFonteExigenciaTreinamentoSst tipoFonte,
                                   String jurisdicao,
                                   String identificadorFonte,
                                   String edicaoFonte,
                                   String urlFonte,
                                   boolean fonteConfirmada,
                                   UUID usuarioConfirmacaoFonte,
                                   String observacoes) {
        this.idGrupoVersao = idGrupoVersao;
        this.revisaoExigencia = revisaoExigencia;
        this.idExigenciaAnterior = idExigenciaAnterior;
        this.idTreinamento = exigir(idTreinamento, "O treinamento é obrigatório.");
        this.tipoCorpo = exigir(tipoCorpo, "O tipo de corpo é obrigatório.");
        this.modoAplicabilidade = exigir(modoAplicabilidade, "O modo de aplicabilidade é obrigatório.");
        this.idFuncao = idFuncao;
        this.papelCorpo = papelCorpo;
        this.idBase = idBase;
        this.cargaHorariaMinutos = cargaHorariaMinutos;
        this.politicaInstituicao = exigir(politicaInstituicao,
                "A política de instituição é obrigatória.");
        this.instituicoesPermitidas = normalizarInstituicoes(instituicoesPermitidas);
        this.modalidade = modalidade;
        this.recorrenciaMeses = recorrenciaMeses;
        this.antecedenciaAlertaDias = antecedenciaAlertaDias;
        this.vigenciaInicio = exigir(vigenciaInicio, "O início da vigência é obrigatório.");
        this.vigenciaFim = vigenciaFim;
        this.tipoFonte = exigir(tipoFonte, "O tipo da fonte é obrigatório.");
        this.jurisdicao = limitar(jurisdicao, 60);
        this.identificadorFonte = exigirTexto(identificadorFonte, 160,
                "O identificador da fonte é obrigatório.");
        this.edicaoFonte = limitar(edicaoFonte, 80);
        this.urlFonte = limitar(urlFonte, 500);
        this.observacoes = limitar(observacoes, 500);
        validar();
        if (fonteConfirmada) {
            confirmarFonte(usuarioConfirmacaoFonte);
        }
    }

    public ExigenciaTreinamentoSst criarProximaVersao(UUID idTreinamento,
                                                       TipoCorpoGovernancaSst tipoCorpo,
                                                       ModoAplicabilidadeTreinamentoSst modoAplicabilidade,
                                                       UUID idFuncao,
                                                       PapelMembroGovernancaSst papelCorpo,
                                                       UUID idBase,
                                                       int cargaHorariaMinutos,
                                                       PoliticaInstituicaoTreinamentoSst politicaInstituicao,
                                                       Collection<String> instituicoesPermitidas,
                                                       ModalidadeTreinamentoSst modalidade,
                                                       Integer recorrenciaMeses,
                                                       int antecedenciaAlertaDias,
                                                       LocalDate vigenciaInicio,
                                                       LocalDate vigenciaFim,
                                                       TipoFonteExigenciaTreinamentoSst tipoFonte,
                                                       String jurisdicao,
                                                       String identificadorFonte,
                                                       String edicaoFonte,
                                                       String urlFonte,
                                                       boolean fonteConfirmada,
                                                       UUID usuarioConfirmacaoFonte,
                                                       String observacoes) {
        if (!atual || !isAtivo()) {
            throw new ConflitoGovernancaSstException("Somente a versão atual pode ser versionada.");
        }
        ExigenciaTreinamentoSst proxima = new ExigenciaTreinamentoSst(
                idGrupoVersao, revisaoExigencia + 1, getId(), idTreinamento, tipoCorpo,
                modoAplicabilidade, idFuncao, papelCorpo, idBase, cargaHorariaMinutos,
                politicaInstituicao, instituicoesPermitidas, modalidade, recorrenciaMeses,
                antecedenciaAlertaDias, vigenciaInicio, vigenciaFim, tipoFonte, jurisdicao,
                identificadorFonte, edicaoFonte, urlFonte, fonteConfirmada,
                usuarioConfirmacaoFonte, observacoes);
        this.atual = false;
        LocalDate encerramento = vigenciaInicio.minusDays(1);
        if (this.vigenciaFim == null || this.vigenciaFim.isAfter(encerramento)) {
            this.vigenciaFim = encerramento;
        }
        return proxima;
    }

    public void confirmarFonte(UUID idUsuario) {
        if (idUsuario == null) {
            throw new InvarianteVioladaException("O usuário que confirmou a fonte é obrigatório.");
        }
        if (tipoFonte == TipoFonteExigenciaTreinamentoSst.NORMATIVA
                && (vazio(edicaoFonte) || vazio(urlFonte))) {
            throw new InvarianteVioladaException(
                    "Fonte normativa exige edição e URL antes da confirmação.");
        }
        if (tipoCorpo == TipoCorpoGovernancaSst.BRIGADA
                && tipoFonte == TipoFonteExigenciaTreinamentoSst.NORMATIVA
                && (vazio(jurisdicao) || !jurisdicao.toUpperCase().startsWith("BA/CBMBA"))) {
            throw new InvarianteVioladaException(
                    "Exigência normativa da Brigada deve identificar o perfil BA/CBMBA confirmado.");
        }
        this.fonteConfirmada = true;
        this.usuarioConfirmacaoFonte = idUsuario;
        this.dataConfirmacaoFonte = OffsetDateTime.now();
    }

    public boolean vigenteEm(LocalDate data) {
        return isAtivo() && atual && fonteConfirmada
                && !data.isBefore(vigenciaInicio)
                && (vigenciaFim == null || !data.isAfter(vigenciaFim));
    }

    public boolean aplicaA(UUID funcao, PapelMembroGovernancaSst papel) {
        boolean funcaoCorresponde = !modoAplicabilidade.exigeFuncao() || idFuncao.equals(funcao);
        boolean papelCorresponde = !modoAplicabilidade.exigePapel() || papelCorpo == papel;
        return funcaoCorresponde && papelCorresponde;
    }

    public boolean aceitaInstituicao(String instituicao) {
        if (politicaInstituicao == PoliticaInstituicaoTreinamentoSst.QUALQUER) {
            return true;
        }
        String normalizada = TreinamentoGovernancaSst.normalizar(instituicao);
        return normalizada != null && instituicoesPermitidas.contains(normalizada);
    }

    public LocalDate dataRecorrencia(LocalDate conclusao) {
        return conclusao == null || recorrenciaMeses == null
                ? null : conclusao.plusMonths(recorrenciaMeses);
    }

    public void desativarExigencia() {
        if (!atual) {
            throw new InvarianteVioladaException("Somente a versão atual pode ser desativada.");
        }
        atual = false;
        desativar();
    }

    private void validar() {
        if (modoAplicabilidade.exigeFuncao() != (idFuncao != null)
                || modoAplicabilidade.exigePapel() != (papelCorpo != null)) {
            throw new InvarianteVioladaException(
                    "Os seletores de função e papel não correspondem ao modo de aplicabilidade.");
        }
        if (cargaHorariaMinutos <= 0) {
            throw new InvarianteVioladaException("A carga horária mínima deve ser positiva.");
        }
        if (papelCorpo != null && !papelCorpo.pertenceA(tipoCorpo)) {
            throw new InvarianteVioladaException(
                    "O papel selecionado não pertence ao tipo de corpo da exigência.");
        }
        if (politicaInstituicao == PoliticaInstituicaoTreinamentoSst.RESTRITA
                && instituicoesPermitidas.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Informe ao menos uma instituição quando a política for restrita.");
        }
        if (politicaInstituicao == PoliticaInstituicaoTreinamentoSst.QUALQUER
                && !instituicoesPermitidas.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Não informe instituições quando a política aceitar qualquer emissor.");
        }
        if (recorrenciaMeses != null && recorrenciaMeses <= 0) {
            throw new InvarianteVioladaException("A recorrência deve ser positiva.");
        }
        if (antecedenciaAlertaDias < 0) {
            throw new InvarianteVioladaException("A antecedência de alerta não pode ser negativa.");
        }
        if (vigenciaFim != null && vigenciaFim.isBefore(vigenciaInicio)) {
            throw new InvarianteVioladaException("O fim da vigência não pode anteceder seu início.");
        }
    }

    private static Set<String> normalizarInstituicoes(Collection<String> valores) {
        LinkedHashSet<String> resultado = new LinkedHashSet<>();
        if (valores != null) {
            for (String valor : valores) {
                String normalizado = TreinamentoGovernancaSst.normalizar(valor);
                if (normalizado == null || normalizado.isBlank()) {
                    throw new InvarianteVioladaException("Instituição permitida não pode ser vazia.");
                }
                if (normalizado.length() > 200) {
                    throw new InvarianteVioladaException("Instituição excede 200 caracteres.");
                }
                resultado.add(normalizado);
            }
        }
        return resultado;
    }

    private static <T> T exigir(T valor, String mensagem) {
        if (valor == null) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor;
    }

    private static String exigirTexto(String valor, int limite, String mensagem) {
        if (vazio(valor)) {
            throw new InvarianteVioladaException(mensagem);
        }
        return limitar(valor, limite);
    }

    private static String limitar(String valor, int limite) {
        if (vazio(valor)) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > limite) {
            throw new InvarianteVioladaException("O valor excede " + limite + " caracteres.");
        }
        return limpo;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
