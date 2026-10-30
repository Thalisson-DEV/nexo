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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "sst_categorias_documento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoriaDocumentoSst extends BaseEntity {

    private static final Pattern CODIGO = Pattern.compile("[A-Z0-9_]{3,80}");

    @Column(name = "codigo", nullable = false, length = 80)
    private String codigo;

    @Column(name = "revisao_categoria", nullable = false)
    private int revisaoCategoria;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexto", nullable = false, length = 20)
    private ContextoCategoriaDocumentoSst contexto;

    @Enumerated(EnumType.STRING)
    @Column(name = "natureza", nullable = false, length = 40)
    private NaturezaCategoriaDocumentoSst natureza;

    @Enumerated(EnumType.STRING)
    @Column(name = "escopo_titular", nullable = false, length = 30)
    private EscopoTitularDocumentoSst escopoTitular;

    @Column(name = "jurisdicao", length = 60)
    private String jurisdicao;

    @Column(name = "autoridade_emissora", length = 160)
    private String autoridadeEmissora;

    @Column(name = "identificador_fonte", length = 160)
    private String identificadorFonte;

    @Column(name = "edicao_fonte", length = 80)
    private String edicaoFonte;

    @Column(name = "url_fonte", length = 500)
    private String urlFonte;

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "confirmada", nullable = false)
    private boolean confirmada;

    @Column(name = "data_confirmacao")
    private OffsetDateTime dataConfirmacao;

    @Column(name = "usuario_confirmacao")
    private UUID usuarioConfirmacao;

    @Column(name = "exige_numero", nullable = false)
    private boolean exigeNumero;

    @Column(name = "exige_emissor", nullable = false)
    private boolean exigeEmissor;

    @Column(name = "exige_data_emissao", nullable = false)
    private boolean exigeDataEmissao;

    @Column(name = "exige_data_validade", nullable = false)
    private boolean exigeDataValidade;

    @Column(name = "sem_validade", nullable = false)
    private boolean semValidade;

    @Column(name = "obrigatoria", nullable = false)
    private boolean obrigatoria;

    @Column(name = "retencao_minima_anos", nullable = false)
    private int retencaoMinimaAnos;

    @Column(name = "referencia_retencao", length = 160)
    private String referenciaRetencao;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sst_categoria_documento_alertas",
            joinColumns = @JoinColumn(name = "id_categoria"))
    @Column(name = "dias_antecedencia", nullable = false)
    private Set<Integer> faixasAlertaDias = new LinkedHashSet<>();

    public CategoriaDocumentoSst(String codigo,
                                 int revisaoCategoria,
                                 String nome,
                                 String descricao,
                                 ContextoCategoriaDocumentoSst contexto,
                                 NaturezaCategoriaDocumentoSst natureza,
                                 EscopoTitularDocumentoSst escopoTitular,
                                 String jurisdicao,
                                 String autoridadeEmissora,
                                 String identificadorFonte,
                                 String edicaoFonte,
                                 String urlFonte,
                                 LocalDate vigenciaInicio,
                                 LocalDate vigenciaFim,
                                 boolean confirmada,
                                 UUID usuarioConfirmacao,
                                 boolean exigeNumero,
                                 boolean exigeEmissor,
                                 boolean exigeDataEmissao,
                                 boolean exigeDataValidade,
                                 boolean semValidade,
                                 boolean obrigatoria,
                                 int retencaoMinimaAnos,
                                 String referenciaRetencao,
                                 Set<Integer> faixasAlertaDias) {
        this.codigo = normalizarCodigo(codigo);
        this.revisaoCategoria = exigirRevisao(revisaoCategoria);
        aplicarConfiguracao(nome, descricao, contexto, natureza, escopoTitular,
                jurisdicao, autoridadeEmissora, identificadorFonte, edicaoFonte,
                urlFonte, vigenciaInicio, vigenciaFim, exigeNumero, exigeEmissor,
                exigeDataEmissao, exigeDataValidade, semValidade, obrigatoria,
                retencaoMinimaAnos, referenciaRetencao, faixasAlertaDias);
        if (confirmada) {
            confirmar(usuarioConfirmacao);
        }
    }

    public CategoriaDocumentoSst criarProximaVersao(String nome,
                                                     String descricao,
                                                     ContextoCategoriaDocumentoSst contexto,
                                                     NaturezaCategoriaDocumentoSst natureza,
                                                     EscopoTitularDocumentoSst escopoTitular,
                                                     String jurisdicao,
                                                     String autoridadeEmissora,
                                                     String identificadorFonte,
                                                     String edicaoFonte,
                                                     String urlFonte,
                                                     LocalDate vigenciaInicio,
                                                     LocalDate vigenciaFim,
                                                     boolean confirmada,
                                                     UUID usuarioConfirmacao,
                                                     boolean exigeNumero,
                                                     boolean exigeEmissor,
                                                     boolean exigeDataEmissao,
                                                     boolean exigeDataValidade,
                                                     boolean semValidade,
                                                     boolean obrigatoria,
                                                     int retencaoMinimaAnos,
                                                     String referenciaRetencao,
                                                     Set<Integer> faixasAlertaDias) {
        exigirAtiva();
        CategoriaDocumentoSst proxima = new CategoriaDocumentoSst(
                codigo, revisaoCategoria + 1, nome, descricao, contexto, natureza,
                escopoTitular, jurisdicao, autoridadeEmissora, identificadorFonte,
                edicaoFonte, urlFonte, vigenciaInicio, vigenciaFim, confirmada,
                usuarioConfirmacao, exigeNumero, exigeEmissor, exigeDataEmissao,
                exigeDataValidade, semValidade, obrigatoria, retencaoMinimaAnos,
                referenciaRetencao, faixasAlertaDias);
        desativar();
        return proxima;
    }

    public void confirmar(UUID idUsuario) {
        exigirAtiva();
        if (idUsuario == null) {
            throw new InvarianteVioladaException("O usuário que confirmou a categoria é obrigatório.");
        }
        if (exigeFonteJurisdicional() &&
                (vazio(jurisdicao) || vazio(autoridadeEmissora) || vazio(identificadorFonte)
                        || vazio(edicaoFonte) || vazio(urlFonte) || vigenciaInicio == null)) {
            throw new InvarianteVioladaException(
                    "Confirme jurisdição, autoridade, fonte oficial, edição, URL e início de vigência antes de usar a categoria.");
        }
        this.confirmada = true;
        this.usuarioConfirmacao = idUsuario;
        this.dataConfirmacao = OffsetDateTime.now();
    }

    public void exigirDisponivelPara(TipoCorpoGovernancaSst tipoCorpo,
                                     EscopoTitularDocumentoSst escopo) {
        exigirAtiva();
        if (!confirmada) {
            throw new InvarianteVioladaException(
                    "A categoria ainda precisa ser confirmada contra a fonte aplicável antes de receber documentos.");
        }
        if (!contexto.aceita(tipoCorpo)) {
            throw new InvarianteVioladaException("A categoria não pertence ao contexto de corpo informado.");
        }
        if (escopoTitular != escopo) {
            throw new InvarianteVioladaException("O titular informado não corresponde ao escopo permitido pela categoria.");
        }
    }

    public int maiorFaixaAlertaDias() {
        return faixasAlertaDias.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public boolean exigeFonteJurisdicional() {
        return natureza == NaturezaCategoriaDocumentoSst.LICENCA_CORPO_BOMBEIROS
                || natureza == NaturezaCategoriaDocumentoSst.ALVARA_MUNICIPAL;
    }

    private void aplicarConfiguracao(String nome,
                                     String descricao,
                                     ContextoCategoriaDocumentoSst contexto,
                                     NaturezaCategoriaDocumentoSst natureza,
                                     EscopoTitularDocumentoSst escopoTitular,
                                     String jurisdicao,
                                     String autoridadeEmissora,
                                     String identificadorFonte,
                                     String edicaoFonte,
                                     String urlFonte,
                                     LocalDate vigenciaInicio,
                                     LocalDate vigenciaFim,
                                     boolean exigeNumero,
                                     boolean exigeEmissor,
                                     boolean exigeDataEmissao,
                                     boolean exigeDataValidade,
                                     boolean semValidade,
                                     boolean obrigatoria,
                                     int retencaoMinimaAnos,
                                     String referenciaRetencao,
                                     Set<Integer> faixasAlertaDias) {
        this.nome = exigirTexto(nome, 160, "O nome da categoria é obrigatório.");
        this.descricao = limitar(descricao, 500);
        this.contexto = exigir(contexto, "O contexto da categoria é obrigatório.");
        this.natureza = exigir(natureza, "A natureza da categoria é obrigatória.");
        this.escopoTitular = exigir(escopoTitular, "O escopo titular da categoria é obrigatório.");
        this.jurisdicao = limitar(jurisdicao, 60);
        this.autoridadeEmissora = limitar(autoridadeEmissora, 160);
        this.identificadorFonte = limitar(identificadorFonte, 160);
        this.edicaoFonte = limitar(edicaoFonte, 80);
        this.urlFonte = limitar(urlFonte, 500);
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.exigeNumero = exigeNumero;
        this.exigeEmissor = exigeEmissor;
        this.exigeDataEmissao = exigeDataEmissao;
        this.exigeDataValidade = exigeDataValidade;
        this.semValidade = semValidade;
        this.obrigatoria = obrigatoria;
        this.retencaoMinimaAnos = retencaoMinimaAnos;
        this.referenciaRetencao = limitar(referenciaRetencao, 160);
        this.faixasAlertaDias = validarFaixas(faixasAlertaDias);
        validarConfiguracao();
    }

    private void validarConfiguracao() {
        if (vigenciaInicio != null && vigenciaFim != null && vigenciaFim.isBefore(vigenciaInicio)) {
            throw new InvarianteVioladaException("O fim da vigência da categoria não pode anteceder seu início.");
        }
        if (semValidade && exigeDataValidade) {
            throw new InvarianteVioladaException("Uma categoria sem vencimento não pode exigir data de validade.");
        }
        if ((contexto == ContextoCategoriaDocumentoSst.CIPA || contexto == ContextoCategoriaDocumentoSst.AMBOS)
                && retencaoMinimaAnos < 5) {
            throw new InvarianteVioladaException(
                    "Documentos da CIPA devem preservar retenção mínima de cinco anos.");
        }
        if (retencaoMinimaAnos < 0) {
            throw new InvarianteVioladaException("O período de retenção não pode ser negativo.");
        }
        if (natureza == NaturezaCategoriaDocumentoSst.LICENCA_CORPO_BOMBEIROS
                && (contexto != ContextoCategoriaDocumentoSst.BRIGADA
                || escopoTitular != EscopoTitularDocumentoSst.BASE)) {
            throw new InvarianteVioladaException(
                    "Licença ou ato do Corpo de Bombeiros deve pertencer à base no contexto da Brigada.");
        }
        if (natureza == NaturezaCategoriaDocumentoSst.ALVARA_MUNICIPAL
                && escopoTitular != EscopoTitularDocumentoSst.BASE) {
            throw new InvarianteVioladaException("Alvará municipal deve pertencer à base.");
        }
        if (natureza == NaturezaCategoriaDocumentoSst.CERTIFICADO_TREINAMENTO
                && escopoTitular != EscopoTitularDocumentoSst.COLABORADOR) {
            throw new InvarianteVioladaException("Certificado de treinamento deve pertencer ao colaborador.");
        }
    }

    private void exigirAtiva() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("A categoria está desativada e não pode ser alterada.");
        }
    }

    private static String normalizarCodigo(String valor) {
        String codigo = exigirTexto(valor, 80, "O código da categoria é obrigatório.")
                .toUpperCase(Locale.ROOT);
        if (!CODIGO.matcher(codigo).matches()) {
            throw new InvarianteVioladaException(
                    "O código da categoria deve usar apenas letras, números e sublinhado.");
        }
        return codigo;
    }

    private static int exigirRevisao(int revisao) {
        if (revisao <= 0) {
            throw new InvarianteVioladaException("A revisão da categoria deve ser positiva.");
        }
        return revisao;
    }

    private static Set<Integer> validarFaixas(Set<Integer> faixas) {
        LinkedHashSet<Integer> resultado = new LinkedHashSet<>();
        if (faixas != null) {
            for (Integer faixa : faixas) {
                if (faixa == null || faixa <= 0 || faixa > 3650) {
                    throw new InvarianteVioladaException(
                            "Cada faixa de alerta deve estar entre 1 e 3650 dias.");
                }
                resultado.add(faixa);
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
        String limpo = valor.trim();
        if (limpo.length() > limite) {
            throw new InvarianteVioladaException("O valor excede " + limite + " caracteres.");
        }
        return limpo;
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
