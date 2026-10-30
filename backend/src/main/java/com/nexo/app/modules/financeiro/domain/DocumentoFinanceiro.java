package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Um documento no repositório financeiro: nota fiscal, boleto, comprovante,
 * orçamento, contrato.
 *
 * <p>É <b>agregado próprio</b>, e não filho da solicitação. A escolha resolve um
 * problema concreto do desenho anterior: os anexos pertenciam à solicitação e só
 * podiam ser enviados enquanto ela estava editável — ou seja, nunca depois da
 * aprovação, que é justamente quando a nota fiscal e o boleto chegam. Aqui o
 * vínculo com a solicitação é uma {@link OrigemDocumento} opcional, e o documento
 * também pode ser avulso: a contabilidade recebe um boleto por e-mail e o guarda
 * sem que exista pedido correspondente.</p>
 *
 * <p>O conteúdo binário fica no object storage; aqui guarda-se a chave para
 * recuperá-lo, os metadados pelos quais se procura e a trilha da conferência. O
 * {@code hashConteudo} existe para que o mesmo arquivo enviado duas vezes seja
 * reconhecido — duplicata em repositório contábil não é detalhe de arrumação, é
 * risco de pagar a mesma nota duas vezes.</p>
 *
 * <p>As colunas de contexto ({@code baseId}, {@code processoId}, {@code obraId})
 * são copiadas da solicitação no momento do envio, em vez de resolvidas por join.
 * Um documento é uma foto do que foi apresentado: se a solicitação for corrigida
 * depois, o documento continua pertencendo ao rateio sob o qual foi aceito.</p>
 */
@Entity
@Table(name = "financeiro_documento",
        indexes = {
                @Index(name = "idx_documento_situacao", columnList = "situacao"),
                @Index(name = "idx_documento_tipo", columnList = "tipo"),
                @Index(name = "idx_documento_origem", columnList = "origem_solicitacao_id"),
                @Index(name = "idx_documento_competencia", columnList = "competencia"),
                @Index(name = "idx_documento_vencimento", columnList = "data_vencimento"),
                @Index(name = "idx_documento_hash", columnList = "hash_conteudo")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoFinanceiro extends BaseEntity {

    private static final int TAMANHO_MAXIMO_NOME = 255;
    private static final int DIGITOS_CHAVE_NFE = 44;

    // ── Arquivo ─────────────────────────────────────────────────────────────────

    @Column(name = "nome_arquivo", nullable = false, length = TAMANHO_MAXIMO_NOME)
    private String nomeArquivo;

    /** Chave do objeto no storage — não é URL pública; o download passa pelo backend. */
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    /** SHA-256 do conteúdo, em hexadecimal. É o que denuncia o mesmo arquivo reenviado. */
    @Column(name = "hash_conteudo", length = 64)
    private String hashConteudo;

    // ── Classificação ───────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoDocumentoFinanceiro tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoDocumento situacao = SituacaoDocumento.PENDENTE;

    /** Número da nota, do boleto ou do recibo, como impresso no documento. */
    @Column(name = "numero_documento", length = 60)
    private String numeroDocumento;

    /** Chave de acesso da NF-e: 44 dígitos, guardados sem formatação. */
    @Column(name = "chave_acesso", length = 44)
    private String chaveAcesso;

    /** Linha digitavel, codigo de barras ou PIX copia-e-cola usado para pagar. */
    @Column(name = "codigo_pagamento", length = 512)
    private String codigoPagamento;

    // ── Emissor ─────────────────────────────────────────────────────────────────

    @Column(name = "fornecedor_id")
    private UUID fornecedorId;

    /** Nome do emissor quando ele não está no cadastro de fornecedores. */
    @Column(name = "emissor_nome", length = 200)
    private String emissorNome;

    // ── Datas e valor ───────────────────────────────────────────────────────────

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    /**
     * Mês de competência contábil, normalizado no dia 1. É por ele que a
     * contabilidade fecha o período, e ele não coincide com a data de emissão:
     * uma nota emitida em 2 de agosto pode competir a julho.
     */
    @Column(name = "competencia")
    private LocalDate competencia;

    @Column(name = "valor", precision = 14, scale = 2)
    private BigDecimal valor;

    // ── Contexto (copiado da solicitação de origem) ─────────────────────────────

    @Column(name = "base_id")
    private UUID baseId;

    /** Setor / centro de custo — processo de negócio do RH. */
    @Column(name = "processo_id")
    private UUID processoId;

    @Column(name = "obra_id")
    private UUID obraId;

    // ── Texto livre ─────────────────────────────────────────────────────────────

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    // ── Origem e trilha ─────────────────────────────────────────────────────────

    @Embedded
    private OrigemDocumento origem = OrigemDocumento.semVinculo();

    public OrigemDocumento getOrigem() {
        return origem != null ? origem : OrigemDocumento.semVinculo();
    }

    /** Usuário autenticado que enviou o arquivo (nunca vem do payload). */
    @Column(name = "usuario_envio_id", nullable = false)
    private UUID usuarioEnvioId;

    @Column(name = "usuario_conferencia_id")
    private UUID usuarioConferenciaId;

    @Column(name = "data_conferencia")
    private OffsetDateTime dataConferencia;

    @Column(name = "observacao_conferencia", columnDefinition = "TEXT")
    private String observacaoConferencia;

    @Column(name = "motivo_pendencia", columnDefinition = "TEXT")
    private String motivoPendencia;

    /**
     * Metadados editáveis do documento. Viajam juntos porque são preenchidos
     * juntos — no envio e em cada correção — e porque um construtor com quinze
     * parâmetros posicionais do mesmo tipo é um convite a trocar dois deles de
     * lugar sem que o compilador perceba.
     */
    public record Metadados(
            TipoDocumentoFinanceiro tipo,
            String numeroDocumento,
            String chaveAcesso,
            String codigoPagamento,
            UUID fornecedorId,
            String emissorNome,
            LocalDate dataEmissao,
            LocalDate dataVencimento,
            LocalDate competencia,
            BigDecimal valor,
            UUID baseId,
            UUID processoId,
            UUID obraId,
            String descricao,
            String observacoes) {

        /** Mantem compatibilidade para chamadores que ainda nao informam codigo pagavel. */
        public Metadados(TipoDocumentoFinanceiro tipo, String numeroDocumento, String chaveAcesso,
                         UUID fornecedorId, String emissorNome, LocalDate dataEmissao,
                         LocalDate dataVencimento, LocalDate competencia, BigDecimal valor,
                         UUID baseId, UUID processoId, UUID obraId, String descricao,
                         String observacoes) {
            this(tipo, numeroDocumento, chaveAcesso, null, fornecedorId, emissorNome,
                    dataEmissao, dataVencimento, competencia, valor, baseId, processoId,
                    obraId, descricao, observacoes);
        }

        /** Metadados mínimos de um envio rápido, em que só a natureza é informada. */
        public static Metadados apenasTipo(TipoDocumentoFinanceiro tipo) {
            return new Metadados(tipo, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null);
        }
    }

    /** Arquivo já gravado no storage, pronto para virar documento. */
    public record Arquivo(String nomeArquivo, String objectKey, String contentType,
                          Long tamanhoBytes, String hashConteudo) {
    }

    public DocumentoFinanceiro(Arquivo arquivo, Metadados metadados, UUID usuarioEnvioId) {
        Objects.requireNonNull(arquivo, "Os dados do arquivo são obrigatórios.");
        if (!StringUtils.hasText(arquivo.nomeArquivo())) {
            throw new InvarianteVioladaException("O nome do arquivo é obrigatório.");
        }
        if (!StringUtils.hasText(arquivo.objectKey())) {
            throw new InvarianteVioladaException("A chave do objeto armazenado é obrigatória.");
        }
        if (!StringUtils.hasText(arquivo.contentType())) {
            throw new InvarianteVioladaException("O tipo de conteúdo do arquivo é obrigatório.");
        }
        if (arquivo.tamanhoBytes() == null || arquivo.tamanhoBytes() <= 0) {
            throw new InvarianteVioladaException("O tamanho do arquivo é inválido.");
        }

        this.nomeArquivo = truncarNome(arquivo.nomeArquivo());
        this.objectKey = arquivo.objectKey();
        this.contentType = arquivo.contentType();
        this.tamanhoBytes = arquivo.tamanhoBytes();
        this.hashConteudo = arquivo.hashConteudo();
        this.usuarioEnvioId = Objects.requireNonNull(usuarioEnvioId, "O usuário que enviou é obrigatório.");
        this.situacao = SituacaoDocumento.PENDENTE;
        aplicar(metadados);
    }

    // ── Metadados ───────────────────────────────────────────────────────────────

    /**
     * Substitui por completo os metadados. Corrigir é sempre "tudo de uma vez"
     * porque os campos se sustentam entre si: mudar o tipo para nota fiscal sem
     * revisar número e competência produziria um documento meio classificado.
     *
     * <p>Um documento arquivado não aceita correção: se algo nele está errado,
     * o caminho é reabri-lo, e a reabertura fica registrada.</p>
     */
    public void atualizarMetadados(Metadados metadados) {
        if (situacao == SituacaoDocumento.ARQUIVADO) {
            throw new InvarianteVioladaException(
                    "O documento está arquivado. Reabra-o antes de alterar os dados.");
        }
        aplicar(metadados);
    }

    private void aplicar(Metadados metadados) {
        Objects.requireNonNull(metadados, "Os dados do documento são obrigatórios.");
        this.tipo = metadados.tipo() == null ? TipoDocumentoFinanceiro.OUTRO : metadados.tipo();
        this.numeroDocumento = normalizar(metadados.numeroDocumento());
        this.chaveAcesso = validarChaveAcesso(metadados.chaveAcesso());
        this.codigoPagamento = normalizar(metadados.codigoPagamento());
        this.fornecedorId = metadados.fornecedorId();
        this.emissorNome = normalizar(metadados.emissorNome());
        this.dataEmissao = metadados.dataEmissao();
        this.dataVencimento = metadados.dataVencimento();
        this.competencia = primeiroDiaDoMes(metadados.competencia());
        this.valor = validarValor(metadados.valor());
        this.baseId = metadados.baseId();
        this.processoId = metadados.processoId();
        this.obraId = metadados.obraId();
        this.descricao = normalizar(metadados.descricao());
        this.observacoes = normalizar(metadados.observacoes());

        if (dataEmissao != null && dataVencimento != null && dataVencimento.isBefore(dataEmissao)) {
            throw new InvarianteVioladaException(
                    "O vencimento não pode ser anterior à data de emissão do documento.");
        }
    }

    /**
     * Preenche a competência quando ela não foi informada, deduzindo-a do
     * documento. A ordem — vencimento antes da emissão — segue o que a
     * contabilidade faz na mão: uma despesa compete ao mês em que vence; sem
     * vencimento, ao mês em que o documento foi emitido.
     */
    public void deduzirCompetenciaSeAusente() {
        if (competencia != null) {
            return;
        }
        LocalDate referencia = dataVencimento != null ? dataVencimento : dataEmissao;
        if (referencia != null) {
            this.competencia = referencia.withDayOfMonth(1);
        }
    }

    // ── Vínculo com a solicitação ───────────────────────────────────────────────

    public void vincular(SolicitacaoBase solicitacao) {
        this.origem = OrigemDocumento.de(
                Objects.requireNonNull(solicitacao, "A solicitação de origem é obrigatória."));
    }

    /** Desfaz o vínculo, quando o documento foi anexado à solicitação errada. */
    public void desvincular() {
        this.origem = OrigemDocumento.semVinculo();
    }

    // ── Conferência ─────────────────────────────────────────────────────────────

    /** Aceita o documento. É a etapa que transforma arquivo recebido em comprovação. */
    public void conferir(UUID usuarioId, String observacao) {
        if (situacao == SituacaoDocumento.ARQUIVADO) {
            throw new InvarianteVioladaException("O documento está arquivado e não admite nova conferência.");
        }
        this.situacao = SituacaoDocumento.CONFERIDO;
        this.usuarioConferenciaId = Objects.requireNonNull(usuarioId, "O usuário que confere é obrigatório.");
        this.dataConferencia = OffsetDateTime.now();
        this.observacaoConferencia = normalizar(observacao);
        this.motivoPendencia = null;
    }

    /** Recusa o documento com um motivo, devolvendo a bola para quem o enviou. */
    public void apontarPendencia(UUID usuarioId, String motivo) {
        if (situacao == SituacaoDocumento.ARQUIVADO) {
            throw new InvarianteVioladaException("O documento está arquivado. Reabra-o antes de apontar pendência.");
        }
        if (!StringUtils.hasText(motivo)) {
            throw new InvarianteVioladaException("Informe o que está pendente no documento.");
        }
        this.situacao = SituacaoDocumento.COM_PENDENCIA;
        this.usuarioConferenciaId = Objects.requireNonNull(usuarioId, "O usuário que confere é obrigatório.");
        this.dataConferencia = OffsetDateTime.now();
        this.motivoPendencia = motivo.trim();
    }

    /**
     * Encerra o documento. Só o que já foi conferido pode ser arquivado: arquivar
     * sem conferir esconderia o documento das filas sem que ninguém o tivesse
     * olhado, que é o modo mais silencioso de perder uma nota.
     */
    public void arquivar() {
        if (situacao != SituacaoDocumento.CONFERIDO) {
            throw new InvarianteVioladaException(
                    "Apenas documentos conferidos podem ser arquivados (situação atual: "
                            + situacao.getRotulo() + ").");
        }
        this.situacao = SituacaoDocumento.ARQUIVADO;
    }

    /** Devolve o documento com pendência à fila de conferência ou desarquiva o documento. */
    public void reabrir() {
        if (situacao == SituacaoDocumento.PENDENTE) {
            throw new InvarianteVioladaException("O documento já está aguardando conferência.");
        }
        if (situacao == SituacaoDocumento.CONFERIDO) {
            throw new InvarianteVioladaException(
                    "Documento já conferido não pode retornar para aguardando conferência.");
        }
        if (situacao == SituacaoDocumento.ARQUIVADO) {
            this.situacao = SituacaoDocumento.CONFERIDO;
            return;
        }
        this.situacao = SituacaoDocumento.PENDENTE;
        this.usuarioConferenciaId = null;
        this.dataConferencia = null;
        this.observacaoConferencia = null;
        this.motivoPendencia = null;
    }

    // ── Leituras ────────────────────────────────────────────────────────────────

    /** Documento que deveria trazer número identificador e não traz. */
    public boolean identificacaoPendente() {
        return tipo != null && tipo.exigeIdentificacao() && !StringUtils.hasText(numeroDocumento);
    }

    public boolean possuiCodigoPagamento() {
        return StringUtils.hasText(codigoPagamento);
    }

    public boolean isFiscal() {
        return tipo != null && tipo.isFiscal();
    }

    /** Vencido e ainda em aberto na conferência — só aí a data cobra alguém. */
    public boolean vencidoEm(LocalDate referencia) {
        return dataVencimento != null && situacao != null && situacao.isEmAberto() && dataVencimento.isBefore(referencia);
    }

    /** Dias na fila desde a última movimentação de conferência, ou desde o envio. */
    public long diasNaSituacao(LocalDate referencia) {
        OffsetDateTime marco = dataConferencia != null ? dataConferencia : getDataCriacao();
        if (marco == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(marco.toLocalDate(), referencia));
    }

    public BigDecimal valorOuZero() {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    // ── Normalização ────────────────────────────────────────────────────────────

    private static String truncarNome(String nome) {
        return nome.length() > TAMANHO_MAXIMO_NOME
                ? nome.substring(nome.length() - TAMANHO_MAXIMO_NOME)
                : nome;
    }

    private static String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private static LocalDate primeiroDiaDoMes(LocalDate competencia) {
        return competencia == null ? null : competencia.withDayOfMonth(1);
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() <= 0) {
            throw new InvarianteVioladaException("O valor do documento deve ser maior que zero.");
        }
        return valor;
    }

    /**
     * Aceita a chave com ou sem formatação e a guarda apenas com dígitos, para que
     * a busca por chave não dependa de como ela foi colada.
     */
    private static String validarChaveAcesso(String chaveAcesso) {
        if (!StringUtils.hasText(chaveAcesso)) {
            return null;
        }
        String digitos = chaveAcesso.replaceAll("\\D", "");
        if (digitos.length() != DIGITOS_CHAVE_NFE) {
            throw new InvarianteVioladaException(
                    "A chave de acesso da nota fiscal deve ter " + DIGITOS_CHAVE_NFE + " dígitos.");
        }
        return digitos;
    }
}
