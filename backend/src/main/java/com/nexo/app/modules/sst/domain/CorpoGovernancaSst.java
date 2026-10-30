package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.UnidadeFederativa;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Raiz do agregado de um ciclo da CIPA ou da Brigada. */
@Entity
@Table(name = "corpos_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorpoGovernancaSst extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoCorpoGovernancaSst tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_composicao", length = 30)
    private RegimeComposicaoSst regimeComposicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCorpoGovernancaSst status;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @Column(name = "base_nome", nullable = false, length = 120)
    private String baseNome;

    @Column(name = "unidade_governanca", nullable = false, length = 180)
    private String unidadeGovernanca;

    @Column(name = "unidade_governanca_normalizada", nullable = false, length = 180)
    private String unidadeGovernancaNormalizada;

    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Column(name = "inicio_periodo", nullable = false)
    private LocalDate inicioPeriodo;

    @Column(name = "fim_periodo", nullable = false)
    private LocalDate fimPeriodo;

    @Column(name = "grau_risco", nullable = false)
    private Integer grauRisco;

    @Column(name = "id_perfil_normativo")
    private UUID idPerfilNormativo;

    @Column(name = "hipotese_representante_confirmada", nullable = false)
    private boolean hipoteseRepresentanteNomeadoConfirmada;

    @Column(name = "conformidade_normativa_validada", nullable = false)
    private boolean conformidadeNormativaValidada;

    @Column(name = "codigo_avaliacao_normativa", length = 100)
    private String codigoAvaliacaoNormativa;

    @Column(name = "perfil_codigo_snapshot", length = 60)
    private String perfilCodigoSnapshot;

    @Column(name = "perfil_edicao_snapshot", length = 80)
    private String perfilEdicaoSnapshot;

    @Column(name = "perfil_url_snapshot", length = 1000)
    private String perfilUrlSnapshot;

    @Column(name = "data_ativacao")
    private OffsetDateTime dataAtivacao;

    @Column(name = "id_usuario_ativacao")
    private UUID idUsuarioAtivacao;

    @Column(name = "data_ultima_movimentacao_composicao")
    private OffsetDateTime dataUltimaMovimentacaoComposicao;

    @Column(name = "data_encerramento")
    private OffsetDateTime dataEncerramento;

    @Column(name = "data_efeito_encerramento")
    private LocalDate dataEfeitoEncerramento;

    @Column(name = "id_usuario_encerramento")
    private UUID idUsuarioEncerramento;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_encerramento", length = 50)
    private MotivoEncerramentoCorpoSst motivoEncerramento;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "justificativa_encerramento", length = 2048)
    private String justificativaEncerramento;

    @Column(name = "referencia_evidencia_encerramento", length = 255)
    private String referenciaEvidenciaEncerramento;

    /** Contagem correlacionada na própria consulta de corpos, sem carregar a coleção. */
    @Formula("(select count(m.id) from membros_corpo_sst m "
            + "where m.id_corpo = id and m.ativo = true and m.fim_participacao is null)")
    private int quantidadeMembrosAtivos;

    @JsonManagedReference
    @OneToMany(mappedBy = "corpo", cascade = CascadeType.ALL)
    private List<BaseAbrangidaCorpoSst> basesAbrangidas = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "corpo", cascade = CascadeType.ALL)
    private List<MembroCorpoSst> membros = new ArrayList<>();

    public CorpoGovernancaSst(
            TipoCorpoGovernancaSst tipo,
            RegimeComposicaoSst regimeComposicao,
            String nome,
            UUID idBaseOperacional,
            String baseNome,
            String unidadeGovernanca,
            String uf,
            LocalDate inicioPeriodo,
            LocalDate fimPeriodo,
            Integer grauRisco,
            UUID idPerfilNormativo,
            boolean hipoteseRepresentanteNomeadoConfirmada) {
        this.tipo = Objects.requireNonNull(tipo, "O tipo do corpo é obrigatório.");
        validarRegime(tipo, regimeComposicao);
        this.regimeComposicao = regimeComposicao;
        this.status = StatusCorpoGovernancaSst.EM_PREPARACAO;
        aplicarIdentificacao(nome, idBaseOperacional, baseNome, unidadeGovernanca, uf,
                inicioPeriodo, fimPeriodo, grauRisco, idPerfilNormativo,
                hipoteseRepresentanteNomeadoConfirmada);
    }

    public void atualizarPreparacao(
            String nome,
            String unidadeGovernanca,
            String uf,
            LocalDate inicioPeriodo,
            LocalDate fimPeriodo,
            Integer grauRisco,
            UUID idPerfilNormativo,
            boolean hipoteseRepresentanteNomeadoConfirmada) {
        exigirPreparacao("alterar a identificação");
        if (!membros.isEmpty() && (!Objects.equals(this.inicioPeriodo, inicioPeriodo)
                || !Objects.equals(this.fimPeriodo, fimPeriodo))) {
            throw new InvarianteVioladaException(
                    "Remova ou ajuste a composição antes de alterar o período do ciclo.");
        }
        aplicarIdentificacao(nome, idBaseOperacional, baseNome, unidadeGovernanca, uf,
                inicioPeriodo, fimPeriodo, grauRisco, idPerfilNormativo,
                hipoteseRepresentanteNomeadoConfirmada);
    }

    public void adicionarBaseAbrangida(UUID idBase, String nomeBase) {
        exigirPreparacao("alterar a abrangência");
        if (idBase == null || idBase.equals(idBaseOperacional)) {
            return;
        }
        if (basesAbrangidas.stream().anyMatch(base -> base.getIdBaseOperacional().equals(idBase))) {
            return;
        }
        basesAbrangidas.add(new BaseAbrangidaCorpoSst(this, idBase, nomeBase));
    }

    public MembroCorpoSst adicionarMembro(
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
        if (status == StatusCorpoGovernancaSst.ENCERRADO) {
            throw new InvarianteVioladaException("Não é possível alterar a composição de um corpo encerrado.");
        }
        if (snapshot == null || !baseEstaAbrangida(snapshot.idBaseOperacional())) {
            throw new InvarianteVioladaException(
                    "O colaborador deve pertencer à base principal ou a uma base abrangida pelo corpo.");
        }
        if (membros.stream().anyMatch(membro -> membro.getIdColaborador().equals(snapshot.idColaborador())
                && membro.isParticipacaoAtiva())) {
            throw new InvarianteVioladaException(
                    "Este colaborador já possui uma participação ativa na composição.");
        }
        MembroCorpoSst membro = new MembroCorpoSst(this, snapshot, papel, representacao,
                titularidade, origem, cicloEleitoral, classificacaoEleitoral,
                atoDesignacao, reeleito, inicioParticipacao);
        membros.add(membro);
        dataUltimaMovimentacaoComposicao = OffsetDateTime.now();
        return membro;
    }

    public void registrarSaida(MembroCorpoSst membro, LocalDate dataEfeito, String motivo, UUID idUsuario) {
        exigirOwnership(membro);
        boolean abreVacancia = tipo == TipoCorpoGovernancaSst.CIPA
                && status == StatusCorpoGovernancaSst.ATIVO;
        membro.registrarSaida(dataEfeito, motivo, idUsuario, abreVacancia);
        dataUltimaMovimentacaoComposicao = OffsetDateTime.now();
    }

    public MembroCorpoSst substituir(
            MembroCorpoSst sucedido,
            SnapshotMembroGovernancaSst sucessor,
            PapelMembroGovernancaSst papel,
            RepresentacaoCipa representacao,
            TitularidadeCipa titularidade,
            OrigemMembroCipa origem,
            String cicloEleitoral,
            Integer classificacaoEleitoral,
            String atoDesignacao,
            boolean reeleito,
            LocalDate dataEfeito,
            String motivoSaida,
            String fundamentoProcedimental,
            UUID idUsuario) {
        exigirOwnership(sucedido);
        if (!sucedido.isParticipacaoAtiva() && !sucedido.isVacanciaPendente()) {
            throw new InvarianteVioladaException("O integrante não possui participação ou vacância substituível.");
        }
        if (tipo == TipoCorpoGovernancaSst.CIPA
                && (fundamentoProcedimental == null || fundamentoProcedimental.isBlank())) {
            throw new InvarianteVioladaException(
                    "A substituição na CIPA exige o fundamento eleitoral ou procedimental aplicável.");
        }
        if (tipo == TipoCorpoGovernancaSst.CIPA
                && (papel != sucedido.getPapel()
                || representacao != sucedido.getRepresentacao()
                || titularidade != sucedido.getTitularidade())) {
            throw new InvarianteVioladaException(
                    "O sucessor da CIPA deve ocupar a mesma vaga, representação e titularidade do sucedido.");
        }

        MembroCorpoSst novo = adicionarMembro(sucessor, papel, representacao, titularidade,
                origem, cicloEleitoral, classificacaoEleitoral, atoDesignacao, reeleito, dataEfeito);
        if (sucedido.isParticipacaoAtiva()) {
            sucedido.registrarSaida(dataEfeito, motivoSaida, idUsuario,
                    tipo == TipoCorpoGovernancaSst.CIPA && status == StatusCorpoGovernancaSst.ATIVO);
        }
        sucedido.vincularSubstituto(novo, fundamentoProcedimental);
        dataUltimaMovimentacaoComposicao = OffsetDateTime.now();
        return novo;
    }

    public void ativar(PerfilNormativoGovernancaSst perfil, UUID idUsuario) {
        exigirPreparacao("ativar");
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o responsável pela ativação.");
        }
        validarComposicaoMinima();
        boolean perfilAplicavel = perfil != null && perfil.getId().equals(idPerfilNormativo)
                && perfil.aplicaA(tipo, uf, inicioPeriodo);

        if (regimeComposicao == RegimeComposicaoSst.REPRESENTANTE_NOMEADO
                && (!hipoteseRepresentanteNomeadoConfirmada || !perfilAplicavel
                || perfil.getEscopo() != EscopoPerfilNormativoSst.NACIONAL)) {
            throw new InvarianteVioladaException(
                    "O representante nomeado exige hipótese da NR-5 e perfil nacional confirmado para o período.");
        }

        avaliarConformidade(perfil, perfilAplicavel);
        if (perfilAplicavel) {
            perfilCodigoSnapshot = perfil.getCodigo();
            perfilEdicaoSnapshot = perfil.getEdicao();
            perfilUrlSnapshot = perfil.getUrlOficial();
        }
        status = StatusCorpoGovernancaSst.ATIVO;
        dataAtivacao = OffsetDateTime.now();
        idUsuarioAtivacao = idUsuario;
    }

    public void encerrar(
            LocalDate dataEfeito,
            MotivoEncerramentoCorpoSst motivo,
            String justificativa,
            String referenciaEvidencia,
            UUID idUsuario) {
        if (status != StatusCorpoGovernancaSst.ATIVO) {
            throw new InvarianteVioladaException("Somente um corpo ativo pode ser encerrado.");
        }
        if (dataEfeito == null || motivo == null || idUsuario == null) {
            throw new InvarianteVioladaException("Data, motivo e responsável são obrigatórios no encerramento.");
        }
        if (dataEfeito.isBefore(inicioPeriodo)) {
            throw new InvarianteVioladaException("O encerramento não pode anteceder o início do ciclo.");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new InvarianteVioladaException("A justificativa do encerramento é obrigatória.");
        }

        if (tipo == TipoCorpoGovernancaSst.CIPA && dataEfeito.isBefore(fimPeriodo)) {
            if (motivo != MotivoEncerramentoCorpoSst.ENCERRAMENTO_ESTABELECIMENTO
                    || referenciaEvidencia == null || referenciaEvidencia.isBlank()) {
                throw new InvarianteVioladaException(
                        "A CIPA só pode terminar antes do mandato por encerramento do estabelecimento, com evidência administrativa.");
            }
        } else if (tipo == TipoCorpoGovernancaSst.CIPA
                && motivo == MotivoEncerramentoCorpoSst.ENCERRAMENTO_ADMINISTRATIVO) {
            throw new InvarianteVioladaException("Encerramento administrativo não é hipótese de término da CIPA.");
        }
        if (tipo == TipoCorpoGovernancaSst.BRIGADA
                && motivo == MotivoEncerramentoCorpoSst.ENCERRAMENTO_ESTABELECIMENTO) {
            throw new InvarianteVioladaException(
                    "A exceção legal de encerramento do estabelecimento não é inferida para a Brigada.");
        }

        status = StatusCorpoGovernancaSst.ENCERRADO;
        dataEfeitoEncerramento = dataEfeito;
        dataEncerramento = OffsetDateTime.now();
        idUsuarioEncerramento = idUsuario;
        motivoEncerramento = motivo;
        justificativaEncerramento = limitar(justificativa.trim(), 500, "A justificativa");
        referenciaEvidenciaEncerramento = textoOuNulo(referenciaEvidencia, 255, "A referência da evidência");
    }

    public boolean baseEstaAbrangida(UUID idBase) {
        return idBase != null && (idBase.equals(idBaseOperacional)
                || basesAbrangidas.stream().anyMatch(base -> idBase.equals(base.getIdBaseOperacional())));
    }

    private void validarComposicaoMinima() {
        List<MembroCorpoSst> ativos = membros.stream().filter(MembroCorpoSst::isParticipacaoAtiva).toList();
        if (ativos.isEmpty()) {
            throw new InvarianteVioladaException("Inclua ao menos um integrante antes de ativar o corpo.");
        }
        if (tipo == TipoCorpoGovernancaSst.BRIGADA) {
            return;
        }
        if (regimeComposicao == RegimeComposicaoSst.REPRESENTANTE_NOMEADO) {
            if (ativos.size() != 1 || ativos.getFirst().getPapel()
                    != PapelMembroGovernancaSst.REPRESENTANTE_NOMEADO) {
                throw new InvarianteVioladaException(
                        "O regime de representante nomeado exige exatamente um representante formalizado.");
            }
            return;
        }

        long presidentes = ativos.stream()
                .filter(m -> m.getPapel() == PapelMembroGovernancaSst.PRESIDENTE).count();
        long vices = ativos.stream()
                .filter(m -> m.getPapel() == PapelMembroGovernancaSst.VICE_PRESIDENTE).count();
        if (presidentes != 1 || vices != 1) {
            throw new InvarianteVioladaException(
                    "A CIPA dimensionada exige exatamente um presidente e um vice-presidente válidos.");
        }
    }

    private void avaliarConformidade(PerfilNormativoGovernancaSst perfil, boolean perfilAplicavel) {
        conformidadeNormativaValidada = false;
        codigoAvaliacaoNormativa = "SEM_PERFIL_CONFIRMADO";
        if (!perfilAplicavel) {
            return;
        }
        if (tipo == TipoCorpoGovernancaSst.BRIGADA) {
            if (perfil.getEscopo() != EscopoPerfilNormativoSst.ESTADUAL) {
                codigoAvaliacaoNormativa = "PERFIL_ESTADUAL_NECESSARIO";
                return;
            }
            Map<PapelMembroGovernancaSst, Long> quantidades = membros.stream()
                    .filter(MembroCorpoSst::isParticipacaoAtiva)
                    .collect(Collectors.groupingBy(MembroCorpoSst::getPapel, Collectors.counting()));
            boolean atende = perfil.getRequisitosPapeis().entrySet().stream()
                    .allMatch(regra -> quantidades.getOrDefault(regra.getKey(), 0L) >= regra.getValue());
            boolean somentePapeisDoPerfil = membros.stream().filter(MembroCorpoSst::isParticipacaoAtiva)
                    .map(MembroCorpoSst::getPapel)
                    .allMatch(perfil.getRequisitosPapeis()::containsKey);
            conformidadeNormativaValidada = atende && somentePapeisDoPerfil;
            codigoAvaliacaoNormativa = conformidadeNormativaValidada
                    ? "CONFORME_PERFIL_CONFIRMADO" : "COMPOSICAO_DIVERGENTE_PERFIL";
            return;
        }
        boolean integrantesConformes = membros.stream()
                .filter(MembroCorpoSst::isParticipacaoAtiva)
                .allMatch(MembroCorpoSst::isConformidadeNormativa);
        conformidadeNormativaValidada = integrantesConformes;
        codigoAvaliacaoNormativa = integrantesConformes
                ? "CONFORME_PERFIL_CONFIRMADO" : "DIVERGENCIA_HISTORICO_ELEITORAL";
    }

    private void aplicarIdentificacao(
            String nome,
            UUID idBaseOperacional,
            String baseNome,
            String unidadeGovernanca,
            String uf,
            LocalDate inicioPeriodo,
            LocalDate fimPeriodo,
            Integer grauRisco,
            UUID idPerfilNormativo,
            boolean hipoteseRepresentanteNomeadoConfirmada) {
        this.nome = exigirTexto(nome, "O nome do corpo é obrigatório.", 150);
        if (idBaseOperacional == null || baseNome == null || baseNome.isBlank()) {
            throw new InvarianteVioladaException("A base operacional validada é obrigatória.");
        }
        this.idBaseOperacional = idBaseOperacional;
        this.baseNome = limitar(baseNome.trim(), 120, "O nome da base");
        this.unidadeGovernanca = exigirTexto(unidadeGovernanca,
                "A unidade de governança é obrigatória.", 180);
        this.unidadeGovernancaNormalizada = SpecificationUtils.normalizarTermo(this.unidadeGovernanca);
        this.uf = UnidadeFederativa.normalizar(uf);
        validarPeriodo(inicioPeriodo, fimPeriodo);
        this.inicioPeriodo = inicioPeriodo;
        this.fimPeriodo = fimPeriodo;
        if (grauRisco == null || grauRisco < 1 || grauRisco > 4) {
            throw new InvarianteVioladaException("O grau de risco deve estar entre 1 e 4.");
        }
        this.grauRisco = grauRisco;
        this.idPerfilNormativo = idPerfilNormativo;
        if (hipoteseRepresentanteNomeadoConfirmada
                && regimeComposicao != RegimeComposicaoSst.REPRESENTANTE_NOMEADO) {
            throw new InvarianteVioladaException(
                    "A confirmação da hipótese de representante nomeado só se aplica a esse regime.");
        }
        this.hipoteseRepresentanteNomeadoConfirmada = hipoteseRepresentanteNomeadoConfirmada;
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("Informe um período válido para o ciclo.");
        }
        if (tipo == TipoCorpoGovernancaSst.CIPA
                && !fim.plusDays(1).equals(inicio.plusYears(1))) {
            throw new InvarianteVioladaException("O período da CIPA deve representar um mandato anual.");
        }
    }

    private static void validarRegime(TipoCorpoGovernancaSst tipo, RegimeComposicaoSst regime) {
        if (tipo == TipoCorpoGovernancaSst.CIPA && regime == null) {
            throw new InvarianteVioladaException("Informe o regime da composição da CIPA.");
        }
        if (tipo == TipoCorpoGovernancaSst.BRIGADA && regime != null) {
            throw new InvarianteVioladaException(
                    "A Brigada não utiliza os regimes exclusivos da CIPA.");
        }
    }

    private void exigirPreparacao(String acao) {
        if (status != StatusCorpoGovernancaSst.EM_PREPARACAO) {
            throw new InvarianteVioladaException(
                    "Somente um ciclo em preparação pode " + acao + ".");
        }
    }

    private void exigirOwnership(MembroCorpoSst membro) {
        if (membro == null || membro.getCorpo() != this) {
            throw new InvarianteVioladaException("O integrante não pertence ao corpo informado.");
        }
    }

    private static String exigirTexto(String valor, String mensagem, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return limitar(valor.trim(), limite, mensagem);
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
