package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * As colunas tipadas de uma solicitação — onde todo dado preenchido vai parar.
 *
 * <p>É deliberadamente um <b>superconjunto</b>: nem toda variante usa todos os
 * campos (placa só interessa à frota, obra às obras civis, tipo de despesa ao
 * pagamento). Quem decide o que aparece, o que é obrigatório e em que ordem é o
 * {@link FormularioSolicitacao}; aqui garante-se que todo dado tenha coluna
 * própria, filtrável e reportável.</p>
 *
 * <p>Instâncias são construídas inteiras a partir de {@link ValoresCanonicos} e
 * substituídas por completo a cada edição — não há mutação parcial.</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NucleoSolicitacao {

    // ── Identificação ───────────────────────────────────────────────────────────
    @Column(name = "solicitante_colaborador_id")
    private UUID solicitanteColaboradorId;

    @Column(name = "base_id")
    private UUID baseId;

    /** Setor / centro de custo — processo de negócio do RH. */
    @Column(name = "processo_id")
    private UUID processoId;

    /** Obra (contrato) à qual a despesa se vincula. */
    @Column(name = "obra_id")
    private UUID obraId;

    // ── Fornecedor ──────────────────────────────────────────────────────────────
    @Column(name = "fornecedor_id")
    private UUID fornecedorId;

    /** Preenchido apenas quando o fornecedor ainda não está cadastrado. */
    @Column(name = "fornecedor_nome", length = 150)
    private String fornecedorNome;

    @Column(name = "fornecedor_razao_social", length = 200)
    private String fornecedorRazaoSocial;

    /** Nome normalizado e desnormalizado exclusivamente para busca e ordenacao. */
    @Column(name = "fornecedor_ordenacao", length = 200)
    private String fornecedorOrdenacao;

    // ── Valores ─────────────────────────────────────────────────────────────────
    @Column(name = "valor", precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(name = "quantidade", precision = 14, scale = 4)
    private BigDecimal quantidade;

    // ── Condições comerciais ────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", length = 20)
    private GrauPrioridade prioridade;

    @Enumerated(EnumType.STRING)
    @Column(name = "local_aquisicao", length = 20)
    private LocalAquisicao localAquisicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 30)
    private FormaPagamento formaPagamento;

    /** Informativo para quem efetua o pagamento; não gera cronograma. */
    @Column(name = "quantidade_parcelas")
    private Integer quantidadeParcelas;

    /** Vencimento único ou da 1ª parcela. */
    @Column(name = "vencimento")
    private LocalDate vencimento;

    /** 🔒 Banco, agência, conta e/ou chave PIX informados na solicitação. */
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "dados_pagamento", columnDefinition = "TEXT")
    private String dadosPagamento;

    /** Blind indices dos termos bancarios; permite busca sem consultar o texto cifrado. */
    @Column(name = "dados_pagamento_busca", columnDefinition = "TEXT")
    private String dadosPagamentoBusca;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_despesa", length = 20)
    private TipoDespesa tipoDespesa;

    // ── Nota fiscal ─────────────────────────────────────────────────────────────
    @Column(name = "tem_nf")
    private Boolean temNf;

    @Column(name = "numero_nf", length = 60)
    private String numeroNf;

    @Column(name = "justificativa_sem_nf", columnDefinition = "TEXT")
    private String justificativaSemNf;

    // ── Texto livre ─────────────────────────────────────────────────────────────
    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    // ── Frota ───────────────────────────────────────────────────────────────────
    @Column(name = "veiculo_id")
    private UUID veiculoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_veiculo", length = 20)
    private RegimeVeiculo regimeVeiculo;

    @Enumerated(EnumType.STRING)
    @Column(name = "natureza_servico", length = 40)
    private NaturezaServicoFrota naturezaServico;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_servico", length = 30)
    private MotivoServicoFrota motivoServico;

    // ── Origem do pagamento ─────────────────────────────────────────────────────
    @Column(name = "possui_compra_previa")
    private Boolean possuiCompraPrevia;

    /**
     * Compra que originou este pagamento, quando houver. É uma referência simples,
     * não uma associação: serve para rastrear e para herdar o preenchimento, e não
     * impõe trava de valor entre os dois documentos.
     */
    @Column(name = "solicitacao_compra_id")
    private UUID solicitacaoCompraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_sem_compra", length = 40)
    private MotivoSemCompra motivoSemCompra;

    /**
     * Monta o núcleo a partir dos valores já validados contra o formulário.
     * Campos ausentes da variante ficam nulos.
     */
    public static NucleoSolicitacao de(ValoresCanonicos valores) {
        return de(valores, null);
    }

    /**
     * Monta o nucleo incluindo o nome resolvido do fornecedor. Esse valor separado
     * preserva o preenchimento original e viabiliza ordenar fornecedores cadastrados
     * e digitados pela mesma coluna.
     */
    public static NucleoSolicitacao de(ValoresCanonicos valores, String fornecedorOrdenacao) {
        NucleoSolicitacao nucleo = new NucleoSolicitacao();
        nucleo.solicitanteColaboradorId = valores.referencia(CampoCanonico.SOLICITANTE_COLABORADOR);
        nucleo.baseId = valores.referencia(CampoCanonico.BASE);
        nucleo.processoId = valores.referencia(CampoCanonico.PROCESSO);
        nucleo.obraId = valores.referencia(CampoCanonico.OBRA);

        nucleo.fornecedorId = valores.referencia(CampoCanonico.FORNECEDOR);
        nucleo.fornecedorNome = valores.texto(CampoCanonico.FORNECEDOR_NOME);
        nucleo.fornecedorRazaoSocial = valores.texto(CampoCanonico.FORNECEDOR_RAZAO_SOCIAL);
        String nomeOrdenacao = fornecedorOrdenacao != null ? fornecedorOrdenacao
                : (nucleo.fornecedorNome != null ? nucleo.fornecedorNome : nucleo.fornecedorRazaoSocial);
        nucleo.fornecedorOrdenacao = normalizarOrdenacao(nomeOrdenacao);

        nucleo.valor = valores.decimal(CampoCanonico.VALOR);
        nucleo.quantidade = valores.decimal(CampoCanonico.QUANTIDADE);

        nucleo.prioridade = valores.enumerado(CampoCanonico.PRIORIDADE, GrauPrioridade.class);
        nucleo.localAquisicao = valores.enumerado(CampoCanonico.LOCAL_AQUISICAO, LocalAquisicao.class);
        nucleo.formaPagamento = valores.enumerado(CampoCanonico.FORMA_PAGAMENTO, FormaPagamento.class);
        nucleo.quantidadeParcelas = valores.inteiro(CampoCanonico.QUANTIDADE_PARCELAS);
        nucleo.vencimento = valores.data(CampoCanonico.VENCIMENTO);
        nucleo.dadosPagamento = valores.texto(CampoCanonico.DADOS_PAGAMENTO);
        nucleo.dadosPagamentoBusca = HashUtils.gerarIndicesBusca(nucleo.dadosPagamento);
        nucleo.tipoDespesa = valores.enumerado(CampoCanonico.TIPO_DESPESA, TipoDespesa.class);

        nucleo.temNf = valores.logico(CampoCanonico.TEM_NF);
        nucleo.numeroNf = valores.texto(CampoCanonico.NUMERO_NF);
        nucleo.justificativaSemNf = valores.texto(CampoCanonico.JUSTIFICATIVA_SEM_NF);

        nucleo.descricao = valores.texto(CampoCanonico.DESCRICAO);
        nucleo.observacoes = valores.texto(CampoCanonico.OBSERVACOES);

        nucleo.veiculoId = valores.referencia(CampoCanonico.VEICULO);
        nucleo.regimeVeiculo = valores.enumerado(CampoCanonico.REGIME_VEICULO, RegimeVeiculo.class);
        nucleo.naturezaServico = valores.enumerado(CampoCanonico.NATUREZA_SERVICO, NaturezaServicoFrota.class);
        nucleo.motivoServico = valores.enumerado(CampoCanonico.MOTIVO_SERVICO, MotivoServicoFrota.class);

        nucleo.possuiCompraPrevia = valores.logico(CampoCanonico.POSSUI_COMPRA_PREVIA);
        nucleo.solicitacaoCompraId = valores.referencia(CampoCanonico.SOLICITACAO_COMPRA);
        nucleo.motivoSemCompra = valores.enumerado(CampoCanonico.MOTIVO_SEM_COMPRA, MotivoSemCompra.class);
        return nucleo;
    }

    private static String normalizarOrdenacao(String valor) {
        if (valor == null) {
            return null;
        }
        return java.text.Normalizer.normalize(valor.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Lê a coluna correspondente a um campo canônico. É a operação inversa de
     * {@link #de(ValoresCanonicos)} e permite percorrer o formulário genericamente,
     * sem que a montagem da resposta conheça cada coluna.
     */
    public Object valorDe(CampoCanonico campo) {
        if (campo == null) {
            return null;
        }
        return switch (campo) {
            case SOLICITANTE_COLABORADOR -> solicitanteColaboradorId;
            case BASE -> baseId;
            case PROCESSO -> processoId;
            case OBRA -> obraId;
            case FORNECEDOR -> fornecedorId;
            case FORNECEDOR_NOME -> fornecedorNome;
            case FORNECEDOR_RAZAO_SOCIAL -> fornecedorRazaoSocial;
            case VALOR -> valor;
            case QUANTIDADE -> quantidade;
            case PRIORIDADE -> prioridade;
            case LOCAL_AQUISICAO -> localAquisicao;
            case FORMA_PAGAMENTO -> formaPagamento;
            case QUANTIDADE_PARCELAS -> quantidadeParcelas;
            case VENCIMENTO -> vencimento;
            case DADOS_PAGAMENTO -> dadosPagamento;
            case TIPO_DESPESA -> tipoDespesa;
            case TEM_NF -> temNf;
            case NUMERO_NF -> numeroNf;
            case JUSTIFICATIVA_SEM_NF -> justificativaSemNf;
            case DESCRICAO -> descricao;
            case OBSERVACOES -> observacoes;
            case VEICULO -> veiculoId;
            case REGIME_VEICULO -> regimeVeiculo;
            case NATUREZA_SERVICO -> naturezaServico;
            case MOTIVO_SERVICO -> motivoServico;
            case POSSUI_COMPRA_PREVIA -> possuiCompraPrevia;
            case SOLICITACAO_COMPRA -> solicitacaoCompraId;
            case MOTIVO_SEM_COMPRA -> motivoSemCompra;
        };
    }

    /** Valor da solicitação, nunca nulo, para uso em somas e comparações. */
    public BigDecimal valorOuZero() {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /** Reconstroi os blind indices depois de carregar um registro anterior a V131. */
    public void reindexarDadosPagamento() {
        this.dadosPagamentoBusca = HashUtils.gerarIndicesBusca(dadosPagamento);
    }
}
