package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo fechado dos campos que uma solicitação pode conter.
 *
 * <p>Todo campo do formulário é um destes: há sempre uma coluna tipada em
 * {@link NucleoSolicitacao} do outro lado, e portanto filtro, relatório e regra de
 * negócio possíveis. O que a configuração da variante controla é apenas
 * <em>apresentação</em> — se o campo aparece, com que rótulo, em que ordem e se é
 * obrigatório.</p>
 *
 * <p>Cada entrada declara o tipo do dado, a fonte das opções e a que domínios se
 * aplica, de modo que o schema publicado ao frontend e a validação do payload
 * sejam derivados da mesma fonte da verdade — nunca de duas listas que possam
 * divergir.</p>
 *
 * <p>A <b>visibilidade condicional</b> também mora aqui, no bloco estático ao fim
 * da enumeração. Deixou de ser cadastro porque nunca foi decisão de quem configura
 * o formulário: "nº da nota fiscal só faz sentido se há nota fiscal" é uma
 * propriedade do campo, não uma preferência. Concentrá-la em código faz a tela, a
 * validação e o schema concordarem por construção.</p>
 */
public enum CampoCanonico {

    // ── Identificação ───────────────────────────────────────────────────────────
    SOLICITANTE_COLABORADOR("solicitanteColaboradorId", TipoCampo.REFERENCIA, FonteReferencia.COLABORADOR, null,
            GrupoCampo.IDENTIFICACAO, "Nome do Solicitante", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    BASE("baseId", TipoCampo.REFERENCIA, FonteReferencia.BASE_OPERACIONAL, null,
            GrupoCampo.IDENTIFICACAO, "Base", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    /** "Setor / Centro de Custo" dos formulários é o processo de negócio do RH. */
    PROCESSO("processoId", TipoCampo.REFERENCIA, FonteReferencia.PROCESSO, null,
            GrupoCampo.IDENTIFICACAO, "Setor / Centro de Custo", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    OBRA("obraId", TipoCampo.REFERENCIA, FonteReferencia.OBRA, null,
            GrupoCampo.IDENTIFICACAO, "Obra", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    // ── Fornecedor (cadastro com fallback em texto livre) ───────────────────────
    FORNECEDOR("fornecedorId", TipoCampo.REFERENCIA, FonteReferencia.FORNECEDOR, null,
            GrupoCampo.FORNECEDOR, "Fornecedor", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    FORNECEDOR_NOME("fornecedorNome", TipoCampo.TEXTO, null, null,
            GrupoCampo.FORNECEDOR, "Fornecedor (não cadastrado)", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    FORNECEDOR_RAZAO_SOCIAL("fornecedorRazaoSocial", TipoCampo.TEXTO, null, null,
            GrupoCampo.FORNECEDOR, "Razão Social", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    // ── Valores ─────────────────────────────────────────────────────────────────
    VALOR("valor", TipoCampo.MOEDA, null, null,
            GrupoCampo.VALORES, "Valor", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    QUANTIDADE("quantidade", TipoCampo.DECIMAL, null, null,
            GrupoCampo.VALORES, "Quantidade", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    // ── Condições comerciais ────────────────────────────────────────────────────
    PRIORIDADE("prioridade", TipoCampo.SELECAO_UNICA, null, GrauPrioridade.class,
            GrupoCampo.VALORES, "Grau de Prioridade", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    LOCAL_AQUISICAO("localAquisicao", TipoCampo.SELECAO_UNICA, null, LocalAquisicao.class,
            GrupoCampo.VALORES, "Local de Aquisição", TipoSolicitacao.COMPRA),

    FORMA_PAGAMENTO("formaPagamento", TipoCampo.SELECAO_UNICA, null, FormaPagamento.class,
            GrupoCampo.VALORES, "Forma de Pagamento", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    QUANTIDADE_PARCELAS("quantidadeParcelas", TipoCampo.INTEIRO, null, null,
            GrupoCampo.VALORES, "Parcelamento", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    VENCIMENTO("vencimento", TipoCampo.DATA, null, null,
            GrupoCampo.VALORES, "Vencimento único ou 1ª parcela", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    DADOS_PAGAMENTO("dadosPagamento", TipoCampo.TEXTO_LONGO, null, null,
            GrupoCampo.VALORES, "Dados para pagamento (PIX, AG, C/C)", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    TIPO_DESPESA("tipoDespesa", TipoCampo.SELECAO_UNICA, null, TipoDespesa.class,
            GrupoCampo.VALORES, "Tipo de Despesa", TipoSolicitacao.PAGAMENTO),

    // ── Nota fiscal ─────────────────────────────────────────────────────────────
    TEM_NF("temNf", TipoCampo.BOOLEANO, null, null,
            GrupoCampo.NOTA_FISCAL, "Tem NF", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    NUMERO_NF("numeroNf", TipoCampo.TEXTO, null, null,
            GrupoCampo.NOTA_FISCAL, "nº Nota Fiscal", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    JUSTIFICATIVA_SEM_NF("justificativaSemNf", TipoCampo.TEXTO_LONGO, null, null,
            GrupoCampo.NOTA_FISCAL, "Justificativa por não haver NF", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    // ── Texto livre ─────────────────────────────────────────────────────────────
    DESCRICAO("descricao", TipoCampo.TEXTO_LONGO, null, null,
            GrupoCampo.COMPLEMENTO, "Descrição da Compra / Serviço", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    OBSERVACOES("observacoes", TipoCampo.TEXTO_LONGO, null, null,
            GrupoCampo.COMPLEMENTO, "Observações, OS ou Rateio", TipoSolicitacao.COMPRA, TipoSolicitacao.PAGAMENTO),

    // ── Frota ───────────────────────────────────────────────────────────────────
    VEICULO("veiculoId", TipoCampo.REFERENCIA, FonteReferencia.VEICULO, null,
            GrupoCampo.FROTA, "Placa", TipoSolicitacao.COMPRA),

    REGIME_VEICULO("regimeVeiculo", TipoCampo.SELECAO_UNICA, null, RegimeVeiculo.class,
            GrupoCampo.FROTA, "Veículo Próprio ou Locado?", TipoSolicitacao.COMPRA),

    NATUREZA_SERVICO("naturezaServico", TipoCampo.SELECAO_UNICA, null, NaturezaServicoFrota.class,
            GrupoCampo.FROTA, "Natureza", TipoSolicitacao.COMPRA),

    MOTIVO_SERVICO("motivoServico", TipoCampo.SELECAO_UNICA, null, MotivoServicoFrota.class,
            GrupoCampo.FROTA, "Motivo", TipoSolicitacao.COMPRA),

    // ── Exclusivos do pagamento ─────────────────────────────────────────────────
    POSSUI_COMPRA_PREVIA("possuiCompraPrevia", TipoCampo.BOOLEANO, null, null,
            GrupoCampo.ORIGEM, "Já foi feita solicitação de compra antes?", TipoSolicitacao.PAGAMENTO),

    SOLICITACAO_COMPRA("solicitacaoCompraId", TipoCampo.REFERENCIA, FonteReferencia.SOLICITACAO_COMPRA, null,
            GrupoCampo.ORIGEM, "Solicitação de compra aprovada", TipoSolicitacao.PAGAMENTO),

    MOTIVO_SEM_COMPRA("motivoSemCompra", TipoCampo.SELECAO_UNICA, null, MotivoSemCompra.class,
            GrupoCampo.ORIGEM, "Motivo de não haver solicitação de compra", TipoSolicitacao.PAGAMENTO);

    /**
     * Regra que subordina a exibição de um campo à resposta dada em outro.
     *
     * @param governante campo cuja resposta decide
     * @param valores    respostas que tornam o campo dependente visível
     */
    public record Condicao(CampoCanonico governante, Set<String> valores) {

        public Condicao {
            valores = Set.copyOf(valores);
        }

        /** Indica se a resposta dada ao governante satisfaz esta condição. */
        public boolean satisfeitaPor(Object valorDoGovernante) {
            String texto = textoDe(valorDoGovernante);
            return texto != null && valores.contains(texto);
        }
    }

    /**
     * Limites de preenchimento de um campo, publicados no schema e recobrados na
     * validação do payload.
     *
     * @param tamanhoMaximo caracteres aceitos em campos de texto
     * @param valorMinimo   piso de campos numéricos
     * @param valorMaximo   teto de campos numéricos
     */
    public record Restricoes(Integer tamanhoMaximo, BigDecimal valorMinimo, BigDecimal valorMaximo) {

        static final Restricoes NENHUMA = new Restricoes(null, null, null);

        static Restricoes texto(int tamanhoMaximo) {
            return new Restricoes(tamanhoMaximo, null, null);
        }

        static Restricoes faixa(String minimo, String maximo) {
            return new Restricoes(null,
                    minimo == null ? null : new BigDecimal(minimo),
                    maximo == null ? null : new BigDecimal(maximo));
        }

        public boolean vazio() {
            return tamanhoMaximo == null && valorMinimo == null && valorMaximo == null;
        }
    }

    private final String chave;
    private final TipoCampo tipoCampo;
    private final FonteReferencia fonteReferencia;
    private final Class<? extends Enum<?>> enumeracao;
    private final GrupoCampo grupo;
    private final String rotuloPadrao;
    private final Set<TipoSolicitacao> aplicavelA;

    /**
     * Atribuída no bloco estático abaixo, e não no construtor, porque uma condição
     * aponta para outra constante desta mesma enumeração — referência que o
     * construtor de um enum não pode fazer.
     */
    private Condicao condicao;

    private Restricoes restricoes = Restricoes.NENHUMA;

    /**
     * Exemplo do que se espera no campo, exibido enquanto ele está vazio. Vem do
     * catálogo porque depende do que o campo é, não da variante — a configuração
     * pode sobrescrevê-lo, mas nunca precisa preencher os 28 do zero.
     */
    private String placeholderPadrao;

    CampoCanonico(String chave, TipoCampo tipoCampo, FonteReferencia fonteReferencia,
                  Class<? extends Enum<?>> enumeracao, GrupoCampo grupo, String rotuloPadrao,
                  TipoSolicitacao... aplicavelA) {
        this.chave = chave;
        this.tipoCampo = tipoCampo;
        this.fonteReferencia = fonteReferencia;
        this.enumeracao = enumeracao;
        this.grupo = grupo;
        this.rotuloPadrao = rotuloPadrao;
        this.aplicavelA = EnumSet.copyOf(Arrays.asList(aplicavelA));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // As quatro condições do sistema, num lugar só.
    //
    // Um campo condicionado nunca é cobrado como obrigatório nem gravado enquanto
    // a condição não for satisfeita, e a mesma regra alimenta o schema que a tela
    // consome. Repare que a resposta ausente não satisfaz nada: por isso o campo
    // booleano governante precisa ser respondido explicitamente (Sim/Não), e não
    // por uma caixa de marcação, que não distingue "não" de "ainda não respondi".
    // ═══════════════════════════════════════════════════════════════════════════
    static {
        NUMERO_NF.condicao = new Condicao(TEM_NF, Set.of("true"));
        JUSTIFICATIVA_SEM_NF.condicao = new Condicao(TEM_NF, Set.of("false"));
        SOLICITACAO_COMPRA.condicao = new Condicao(POSSUI_COMPRA_PREVIA, Set.of("true"));
        MOTIVO_SEM_COMPRA.condicao = new Condicao(POSSUI_COMPRA_PREVIA, Set.of("false"));

        // Limites de preenchimento. Os de texto acompanham o tamanho da coluna, de
        // modo que a tela avise antes de o banco recusar; os numéricos são regra de
        // negócio — não se pede autorização de zero real, nem de 49 parcelas.
        VALOR.restricoes = Restricoes.faixa("0.01", null);
        QUANTIDADE.restricoes = Restricoes.faixa("0.0001", null);
        QUANTIDADE_PARCELAS.restricoes = Restricoes.faixa("1", "48");

        FORNECEDOR_NOME.restricoes = Restricoes.texto(150);
        FORNECEDOR_RAZAO_SOCIAL.restricoes = Restricoes.texto(200);
        NUMERO_NF.restricoes = Restricoes.texto(60);
        DADOS_PAGAMENTO.restricoes = Restricoes.texto(500);
        JUSTIFICATIVA_SEM_NF.restricoes = Restricoes.texto(2000);
        DESCRICAO.restricoes = Restricoes.texto(2000);
        OBSERVACOES.restricoes = Restricoes.texto(2000);

        // Exemplos do que se espera em cada campo. Um formulário sem eles obriga a
        // pessoa a deduzir o formato — e é o tipo de dúvida que gera preenchimento
        // errado e retrabalho de quem confere.
        SOLICITANTE_COLABORADOR.placeholderPadrao = "Pesquise pelo nome do colaborador";
        BASE.placeholderPadrao = "Selecione a base";
        PROCESSO.placeholderPadrao = "Selecione o setor ou centro de custo";
        OBRA.placeholderPadrao = "Pesquise pelo número do contrato ou cliente";

        FORNECEDOR.placeholderPadrao = "Pesquise pelo nome do fornecedor";
        FORNECEDOR_NOME.placeholderPadrao = "Ex.: Auto Peças Silva";
        FORNECEDOR_RAZAO_SOCIAL.placeholderPadrao = "Ex.: Auto Peças Silva Ltda";

        VALOR.placeholderPadrao = "0,00";
        QUANTIDADE.placeholderPadrao = "Ex.: 4";
        PRIORIDADE.placeholderPadrao = "Qual a urgência?";
        LOCAL_AQUISICAO.placeholderPadrao = "Onde será comprado?";
        FORMA_PAGAMENTO.placeholderPadrao = "Como será pago?";
        QUANTIDADE_PARCELAS.placeholderPadrao = "1 para pagamento à vista";
        VENCIMENTO.placeholderPadrao = "dd/mm/aaaa";
        DADOS_PAGAMENTO.placeholderPadrao = "Banco, agência e conta, ou a chave PIX";
        TIPO_DESPESA.placeholderPadrao = "Que tipo de despesa é esta?";

        NUMERO_NF.placeholderPadrao = "Ex.: 12345";
        JUSTIFICATIVA_SEM_NF.placeholderPadrao = "Explique por que esta despesa não tem nota fiscal";

        DESCRICAO.placeholderPadrao = "Descreva o que será comprado e para que será usado";
        OBSERVACOES.placeholderPadrao = "Rateio, número da OS ou qualquer detalhe que ajude quem aprova";

        VEICULO.placeholderPadrao = "Pesquise pela placa";
        REGIME_VEICULO.placeholderPadrao = "O veículo é próprio ou locado?";
        NATUREZA_SERVICO.placeholderPadrao = "Que tipo de serviço será feito?";
        MOTIVO_SERVICO.placeholderPadrao = "Por que o serviço é necessário?";

        SOLICITACAO_COMPRA.placeholderPadrao = "Pesquise pelo número da compra aprovada";
        MOTIVO_SEM_COMPRA.placeholderPadrao = "Por que não houve solicitação de compra?";
    }

    /** Chave estável usada no payload e no schema publicado ao frontend. */
    public String getChave() {
        return chave;
    }

    public TipoCampo getTipoCampo() {
        return tipoCampo;
    }

    public FonteReferencia getFonteReferencia() {
        return fonteReferencia;
    }

    /**
     * Enumeração fechada que origina as opções deste campo, quando houver. Permite
     * ao schema publicar os valores possíveis sem que o frontend os replique.
     */
    public Class<? extends Enum<?>> getEnumeracao() {
        return enumeracao;
    }

    /** Rótulo sugerido; a configuração do formulário pode sobrescrevê-lo. */
    public String getRotuloPadrao() {
        return rotuloPadrao;
    }

    /**
     * Bloco sob o qual o campo aparece. Como o assunto é propriedade do campo, o
     * formulário se organiza sozinho: não há grupo a cadastrar, nem grupo vazio.
     */
    public GrupoCampo getGrupo() {
        return grupo;
    }

    /** Exemplo exibido no campo vazio; a configuração da variante pode substituí-lo. */
    public String getPlaceholderPadrao() {
        return placeholderPadrao;
    }

    /** Condição que governa a exibição deste campo, ou vazio se ele é incondicional. */
    public Optional<Condicao> getCondicao() {
        return Optional.ofNullable(condicao);
    }

    /** Limites de preenchimento; nunca nulo, apenas eventualmente vazio. */
    public Restricoes getRestricoes() {
        return restricoes;
    }

    /** Valores aceitos por este campo, quando ele é apoiado numa enumeração fechada. */
    public Set<String> valoresAceitos() {
        if (enumeracao == null) {
            return Set.of();
        }
        return Arrays.stream(enumeracao.getEnumConstants())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean aplicaSeA(TipoSolicitacao tipo) {
        return aplicavelA.contains(tipo);
    }

    /** Localiza o canônico pela chave publicada, se existir. */
    public static Optional<CampoCanonico> porChave(String chave) {
        if (chave == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(c -> c.chave.equals(chave))
                .findFirst();
    }

    /**
     * Garante que este campo faz sentido no domínio informado — impede, por
     * exemplo, configurar "Placa" num formulário de pagamento.
     */
    public void exigirCompatibilidadeCom(TipoSolicitacao tipo) {
        if (!aplicaSeA(tipo)) {
            throw new InvarianteVioladaException(
                    "O campo '" + chave + "' não se aplica a solicitações de " + tipo + ".");
        }
    }

    /**
     * Normaliza um valor para a forma textual com que as condições comparam.
     * Booleanos viram "true"/"false" e enumerações viram o nome da constante — as
     * mesmas representações que trafegam no payload.
     */
    static String textoDe(Object valor) {
        return switch (valor) {
            case null -> null;
            case Boolean logico -> logico.toString();
            case Enum<?> constante -> constante.name();
            case String texto -> texto.isBlank() ? null : texto.trim();
            default -> valor.toString().trim();
        };
    }
}
