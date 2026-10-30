package com.nexo.app.modules.sst.domain;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "sst_documentos_governanca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoSst extends BaseEntity {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @Column(name = "id_categoria", nullable = false)
    private UUID idCategoria;

    @Column(name = "categoria_codigo", nullable = false, length = 80)
    private String categoriaCodigo;

    @Column(name = "categoria_revisao", nullable = false)
    private int categoriaRevisao;

    @Column(name = "id_base", nullable = false)
    private UUID idBase;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexto", nullable = false, length = 20)
    private TipoCorpoGovernancaSst contexto;

    @Enumerated(EnumType.STRING)
    @Column(name = "escopo_titular", nullable = false, length = 30)
    private EscopoTitularDocumentoSst escopoTitular;

    @Column(name = "id_titular", nullable = false)
    private UUID idTitular;

    @Column(name = "id_colaborador")
    private UUID idColaborador;

    @Column(name = "id_treinamento")
    private UUID idTreinamento;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "numero_documento", length = 120)
    private String numeroDocumento;

    @Column(name = "emissor", length = 200)
    private String emissor;

    @Column(name = "uf", length = 2)
    private String uf;

    @Column(name = "referencia_normativa", length = 300)
    private String referenciaNormativa;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(name = "antecedencia_alerta_dias", nullable = false)
    private int antecedenciaAlertaDias;

    @Column(name = "data_inicio_alerta")
    private LocalDate dataInicioAlerta;

    @Column(name = "data_conclusao_treinamento")
    private LocalDate dataConclusaoTreinamento;

    @Column(name = "carga_horaria_minutos")
    private Integer cargaHorariaMinutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade", length = 30)
    private ModalidadeTreinamentoSst modalidade;

    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_conferencia", nullable = false, length = 20)
    private SituacaoConferenciaDocumentoSst situacaoConferencia =
            SituacaoConferenciaDocumentoSst.PENDING_REVIEW;

    @Column(name = "usuario_conferencia")
    private UUID usuarioConferencia;

    @Column(name = "data_conferencia")
    private OffsetDateTime dataConferencia;

    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "motivo_rejeicao", columnDefinition = "TEXT")
    private String motivoRejeicao;

    @Column(name = "id_documento_substituido")
    private UUID idDocumentoSubstituido;

    @Column(name = "id_documento_substituto")
    private UUID idDocumentoSubstituto;

    @Column(name = "atual", nullable = false)
    private boolean atual;

    @Column(name = "data_arquivamento")
    private OffsetDateTime dataArquivamento;

    @Column(name = "usuario_arquivamento")
    private UUID usuarioArquivamento;

    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "motivo_arquivamento", columnDefinition = "TEXT")
    private String motivoArquivamento;

    @Column(name = "data_retencao_ate")
    private LocalDate dataRetencaoAte;

    @Column(name = "referencia_retencao", length = 160)
    private String referenciaRetencao;

    @Column(name = "usuario_envio", nullable = false)
    private UUID usuarioEnvio;

    public DocumentoSst(CategoriaDocumentoSst categoria,
                        ArquivoPrivadoDocumentoSst arquivo,
                        DadosDocumentoSst dados,
                        UUID usuarioEnvio,
                        LocalDate dataRegistro,
                        UUID idDocumentoSubstituido) {
        Objects.requireNonNull(categoria, "A categoria é obrigatória.");
        Objects.requireNonNull(arquivo, "O arquivo é obrigatório.");
        Objects.requireNonNull(dados, "Os metadados são obrigatórios.");
        categoria.exigirDisponivelPara(dados.contexto(), dados.escopoTitular());

        this.idCategoria = exigir(categoria.getId(), "A categoria precisa estar persistida.");
        this.categoriaCodigo = categoria.getCodigo();
        this.categoriaRevisao = categoria.getRevisaoCategoria();
        this.idBase = exigir(dados.idBase(), "A base é obrigatória.");
        this.contexto = exigir(dados.contexto(), "O contexto de CIPA ou Brigada é obrigatório.");
        this.escopoTitular = exigir(dados.escopoTitular(), "O escopo do titular é obrigatório.");
        this.idTitular = exigir(dados.idTitular(), "O titular do documento é obrigatório.");
        this.idColaborador = dados.idColaborador();
        this.idTreinamento = dados.idTreinamento();
        validarTitularidade();

        this.nomeArquivo = exigirTexto(arquivo.nomeArquivo(), 255, "O nome do arquivo é obrigatório.");
        this.objectKey = exigirTexto(arquivo.objectKey(), 500, "A chave privada do arquivo é obrigatória.");
        this.contentType = exigirTexto(arquivo.contentType(), 150, "O tipo detectado do arquivo é obrigatório.");
        if (arquivo.tamanhoBytes() <= 0) {
            throw new InvarianteVioladaException("O arquivo deve possuir conteúdo.");
        }
        this.tamanhoBytes = arquivo.tamanhoBytes();
        this.sha256 = validarSha(arquivo.sha256());

        this.numeroDocumento = limitar(dados.numeroDocumento(), 120);
        this.emissor = limitar(dados.emissor(), 200);
        this.uf = normalizarUf(dados.uf());
        this.referenciaNormativa = limitar(dados.referenciaNormativa(), 300);
        this.dataEmissao = dados.dataEmissao();
        this.dataValidade = dados.dataValidade();
        this.antecedenciaAlertaDias = categoria.maiorFaixaAlertaDias();
        this.dataInicioAlerta = dataValidade == null || antecedenciaAlertaDias == 0
                ? null : dataValidade.minusDays(antecedenciaAlertaDias);
        this.dataConclusaoTreinamento = dados.dataConclusaoTreinamento();
        this.cargaHorariaMinutos = dados.cargaHorariaMinutos();
        this.modalidade = dados.modalidade();
        this.observacoes = limitar(dados.observacoes(), 4000);
        validarMetadados(categoria);

        this.usuarioEnvio = exigir(usuarioEnvio, "O usuário responsável pelo envio é obrigatório.");
        LocalDate registro = exigir(dataRegistro, "A data de registro é obrigatória.");
        this.dataRetencaoAte = categoria.getRetencaoMinimaAnos() == 0
                ? null : registro.plusYears(categoria.getRetencaoMinimaAnos());
        this.referenciaRetencao = categoria.getReferenciaRetencao();
        this.idDocumentoSubstituido = idDocumentoSubstituido;
        this.atual = idDocumentoSubstituido == null;
    }

    public void aprovar(UUID idUsuario, DocumentoSst documentoAnterior) {
        exigirPendente();
        UUID usuario = exigir(idUsuario, "O usuário responsável pela conferência é obrigatório.");
        if (idDocumentoSubstituido != null) {
            if (documentoAnterior == null || !idDocumentoSubstituido.equals(documentoAnterior.getId())) {
                throw new InvarianteVioladaException("A versão anterior da substituição é obrigatória.");
            }
            documentoAnterior.marcarComoSubstituidoPor(getId());
            this.atual = true;
        }
        this.situacaoConferencia = SituacaoConferenciaDocumentoSst.APPROVED;
        this.usuarioConferencia = usuario;
        this.dataConferencia = OffsetDateTime.now();
        this.motivoRejeicao = null;
    }

    public void rejeitar(UUID idUsuario, String motivo) {
        exigirPendente();
        this.situacaoConferencia = SituacaoConferenciaDocumentoSst.REJECTED;
        this.usuarioConferencia = exigir(idUsuario, "O usuário responsável pela conferência é obrigatório.");
        this.dataConferencia = OffsetDateTime.now();
        this.motivoRejeicao = exigirTexto(motivo, 2000, "O motivo da rejeição é obrigatório.");
    }

    public void arquivar(UUID idUsuario, String motivo) {
        if (estaArquivado()) {
            throw new InvarianteVioladaException("O documento já está arquivado.");
        }
        this.dataArquivamento = OffsetDateTime.now();
        this.usuarioArquivamento = exigir(idUsuario, "O usuário responsável pelo arquivamento é obrigatório.");
        this.motivoArquivamento = exigirTexto(motivo, 2000, "O motivo do arquivamento é obrigatório.");
        this.atual = false;
    }

    public StatusValidadeDocumentoSst statusValidade(LocalDate dataReferencia, int antecedenciaDias) {
        Objects.requireNonNull(dataReferencia, "A data de referência é obrigatória.");
        if (dataValidade == null) {
            return StatusValidadeDocumentoSst.SEM_VALIDADE;
        }
        if (dataReferencia.isAfter(dataValidade)) {
            return StatusValidadeDocumentoSst.VENCIDO;
        }
        int antecedencia = Math.max(0, antecedenciaDias);
        if (!dataValidade.isAfter(dataReferencia.plusDays(antecedencia))) {
            return StatusValidadeDocumentoSst.A_VENCER;
        }
        return StatusValidadeDocumentoSst.REGULAR;
    }

    public StatusValidadeDocumentoSst statusValidade(LocalDate dataReferencia) {
        return statusValidade(dataReferencia, antecedenciaAlertaDias);
    }

    public boolean podeComporConformidade() {
        return isAtivo() && atual && !estaArquivado()
                && situacaoConferencia == SituacaoConferenciaDocumentoSst.APPROVED;
    }

    public boolean estaArquivado() {
        return dataArquivamento != null;
    }

    public boolean permiteExpurgoEm(LocalDate dataReferencia) {
        return dataRetencaoAte == null || !dataReferencia.isBefore(dataRetencaoAte);
    }

    private void marcarComoSubstituidoPor(UUID idSubstituto) {
        if (!atual || estaArquivado()) {
            throw new ConflitoGovernancaSstException(
                    "A versão anterior deixou de ser a versão atual; recarregue o histórico antes de aprovar.");
        }
        this.idDocumentoSubstituto = exigir(idSubstituto, "O identificador da versão substituta é obrigatório.");
        this.atual = false;
    }

    private void exigirPendente() {
        if (estaArquivado()) {
            throw new InvarianteVioladaException("Documento arquivado não pode ser conferido.");
        }
        if (situacaoConferencia != SituacaoConferenciaDocumentoSst.PENDING_REVIEW) {
            throw new InvarianteVioladaException("A decisão de conferência desta versão já foi registrada.");
        }
    }

    private void validarTitularidade() {
        if (escopoTitular == EscopoTitularDocumentoSst.BASE && !idTitular.equals(idBase)) {
            throw new InvarianteVioladaException("Documento da base deve usar a própria base como titular.");
        }
        if (escopoTitular == EscopoTitularDocumentoSst.COLABORADOR) {
            if (idColaborador == null || !idTitular.equals(idColaborador)) {
                throw new InvarianteVioladaException(
                        "Documento do colaborador deve usar o mesmo colaborador como titular.");
            }
        } else if (idColaborador != null) {
            throw new InvarianteVioladaException(
                    "Somente documento de escopo colaborador pode informar colaborador titular.");
        }
    }

    private void validarMetadados(CategoriaDocumentoSst categoria) {
        if (categoria.isExigeNumero() && vazio(numeroDocumento)) {
            throw new InvarianteVioladaException("O número do documento é obrigatório para esta categoria.");
        }
        if (categoria.isExigeEmissor() && vazio(emissor)) {
            throw new InvarianteVioladaException("O emissor é obrigatório para esta categoria.");
        }
        if (categoria.isExigeDataEmissao() && dataEmissao == null) {
            throw new InvarianteVioladaException("A data de emissão é obrigatória para esta categoria.");
        }
        if (categoria.isExigeDataValidade() && dataValidade == null) {
            throw new InvarianteVioladaException("A validade informada no documento é obrigatória para esta categoria.");
        }
        if (categoria.isSemValidade() && dataValidade != null) {
            throw new InvarianteVioladaException("Esta categoria foi configurada sem vencimento.");
        }
        if (dataEmissao != null && dataValidade != null && dataValidade.isBefore(dataEmissao)) {
            throw new InvarianteVioladaException("A validade não pode anteceder a data de emissão.");
        }
        if (categoria.getNatureza() == NaturezaCategoriaDocumentoSst.CERTIFICADO_TREINAMENTO) {
            if (idTreinamento == null || dataConclusaoTreinamento == null
                    || cargaHorariaMinutos == null || cargaHorariaMinutos <= 0
                    || modalidade == null || vazio(emissor)) {
                throw new InvarianteVioladaException(
                        "Certificado exige treinamento, conclusão, carga horária, instituição e modalidade.");
            }
        } else if (idTreinamento != null) {
            throw new InvarianteVioladaException(
                    "Somente uma categoria de certificado pode referenciar treinamento.");
        }
    }

    private static String validarSha(String valor) {
        String hash = exigirTexto(valor, 64, "O SHA-256 do arquivo é obrigatório.")
                .toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(hash).matches()) {
            throw new InvarianteVioladaException("O SHA-256 do arquivo é inválido.");
        }
        return hash;
    }

    private static String normalizarUf(String valor) {
        if (vazio(valor)) {
            return null;
        }
        String uf = valor.trim().toUpperCase(Locale.ROOT);
        if (!uf.matches("[A-Z]{2}")) {
            throw new InvarianteVioladaException("A UF deve possuir duas letras.");
        }
        return uf;
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
