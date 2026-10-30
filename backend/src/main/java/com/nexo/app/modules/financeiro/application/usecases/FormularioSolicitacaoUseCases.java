package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.CampoCanonicoDisponivelResponse;
import com.nexo.app.modules.financeiro.application.dto.CampoRequest;
import com.nexo.app.modules.financeiro.application.dto.FormularioFilter;
import com.nexo.app.modules.financeiro.application.dto.FormularioRequest;
import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioSchemaResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioUpdateRequest;
import com.nexo.app.modules.financeiro.application.mappers.FormularioMapper;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.CampoFormulario;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FormularioSolicitacaoRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FormularioSolicitacaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Configuração das variantes de formulário — é este serviço que entrega a promessa
 * de "campos entram e saem sem deploy".
 *
 * <p>Toda alteração estrutural termina em {@code validarEstrutura()}: a variante
 * fica sempre num estado consistente, e o administrador descobre o problema ao
 * configurar, não quando o solicitante tentar usar. Reordenar é exceção
 * deliberada — mexe só em apresentação e não pode invalidar nada.</p>
 */
@Service
@RequiredArgsConstructor
public class FormularioSolicitacaoUseCases {

    private final FormularioSolicitacaoRepository repository;
    private final FormularioMapper mapper;
    private final GeradorSchemaFormulario geradorSchema;

    // ── Variante ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse criar(FormularioRequest request) {
        exigirCodigoLivre(request.tipoSolicitacao(), request.codigo());

        var formulario = new FormularioSolicitacao(request.tipoSolicitacao(), request.codigo(),
                request.titulo(), request.descricao(), request.padrao());
        if (formulario.isPadrao()) {
            destituirPadraoAnterior(formulario);
        }
        return geradorSchema.gerar(repository.saveAndFlush(formulario));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse atualizar(UUID id, FormularioUpdateRequest request) {
        var formulario = buscarEntity(id);
        formulario.atualizarDados(request.titulo(), request.descricao(), request.padrao());
        if (formulario.isPadrao()) {
            destituirPadraoAnterior(formulario);
        }
        return salvarEGerar(formulario, false);
    }

    /**
     * Cria uma cópia integral da variante sob um novo código.
     *
     * <p>É o caminho seguro para evoluir um formulário já em uso: em vez de mexer
     * no que gerou as solicitações históricas, publica-se uma versão nova e
     * desativa-se a antiga, preservando a leitura do que já foi enviado.</p>
     */
    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse duplicar(UUID id, FormularioRequest request) {
        var origem = buscarEntity(id);
        exigirCodigoLivre(origem.getTipoSolicitacao(), request.codigo());

        var copia = new FormularioSolicitacao(origem.getTipoSolicitacao(), request.codigo(),
                request.titulo(), request.descricao(), false);
        for (CampoFormulario campo : origem.camposOrdenados()) {
            copia.adicionarCampo(campo.getCampoCanonico(), campo.getRotulo(),
                    campo.getAjuda(), campo.isObrigatorio());
        }
        copia.validarEstrutura();
        return geradorSchema.gerar(repository.saveAndFlush(copia));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public void desativar(UUID id) {
        var formulario = buscarEntity(id);
        formulario.desativar();
        formulario.deixarDeSerPadrao();
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public void ativar(UUID id) {
        buscarEntity(id).ativar();
    }

    // ── Campos ──────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse adicionarCampo(UUID id, CampoRequest request) {
        var formulario = buscarEntity(id);
        formulario.adicionarCampo(request.campoCanonico(), request.rotulo(),
                request.ajuda(), request.obrigatorio());
        return salvarEGerar(formulario, true);
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse atualizarCampo(UUID id, UUID idCampo, CampoRequest request) {
        var formulario = buscarEntity(id);
        formulario.atualizarCampo(idCampo, request.rotulo(), request.ajuda(),
                request.obrigatorio());
        return salvarEGerar(formulario, true);
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse removerCampo(UUID id, UUID idCampo) {
        var formulario = buscarEntity(id);
        formulario.removerCampo(idCampo);
        return salvarEGerar(formulario, true);
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORMULARIOS, allEntries = true)
    public FormularioSchemaResponse reordenarCampos(UUID id, List<UUID> idsNaOrdem) {
        var formulario = buscarEntity(id);
        formulario.reordenarCampos(idsNaOrdem);
        return salvarEGerar(formulario, false);
    }

    // ── Consultas ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FormularioSchemaResponse buscarSchema(UUID id) {
        return geradorSchema.gerar(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    public FormularioSchemaResponse buscarSchemaPorCodigo(TipoSolicitacao tipo, String codigo) {
        return geradorSchema.gerar(repository
                .findByTipoSolicitacaoAndCodigo(tipo, codigo == null ? null : codigo.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Variante '" + codigo + "' de " + tipo + " não encontrada.")));
    }

    /** Variantes que o solicitante pode escolher ao abrir uma nova solicitação. */
    @Transactional(readOnly = true)
    public List<FormularioResumoResponse> listarVariantes(TipoSolicitacao tipo) {
        return repository.findByTipoSolicitacaoAndAtivoTrueOrderByPadraoDescCodigoAsc(tipo).stream()
                .map(mapper::toResumo)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<FormularioResumoResponse> listar(FormularioFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(FormularioSolicitacaoSpecification.comFiltros(filtro), pageable)
                        .map(mapper::toResumo));
    }

    /**
     * Catálogo de campos do sistema aplicáveis à variante, com a marcação dos que
     * já estão em uso. Evita que a tela de configuração mantenha essa lista em
     * código e desatualize a cada campo novo.
     */
    @Transactional(readOnly = true)
    public List<CampoCanonicoDisponivelResponse> listarCamposCanonicos(UUID id) {
        var formulario = buscarEntity(id);
        return Arrays.stream(CampoCanonico.values())
                .filter(canonico -> canonico.aplicaSeA(formulario.getTipoSolicitacao()))
                .map(canonico -> new CampoCanonicoDisponivelResponse(
                        canonico,
                        canonico.getChave(),
                        canonico.getRotuloPadrao(),
                        canonico.getTipoCampo(),
                        canonico.getGrupo(),
                        canonico.getGrupo().getRotulo(),
                        canonico.getFonteReferencia(),
                        formulario.possuiCanonico(canonico)))
                .toList();
    }

    /** Carrega a variante para os use cases de solicitação, sem duplicar a mensagem. */
    FormularioSolicitacao buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Formulário não encontrado."));
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────

    /**
     * A entidade já está gerenciada pela transação, então basta um flush para que
     * os campos recém-criados ganhem ID antes de o schema ser montado — chamar
     * {@code save()} aqui faria um merge desnecessário sobre um agregado vivo.
     *
     * @param revalidarEstrutura falso nas operações que só mexem em apresentação,
     *                           que devem funcionar mesmo numa variante incompleta
     */
    private FormularioSchemaResponse salvarEGerar(FormularioSolicitacao formulario, boolean revalidarEstrutura) {
        if (revalidarEstrutura) {
            formulario.validarEstrutura();
        }
        repository.flush();
        return geradorSchema.gerar(formulario);
    }

    private void exigirCodigoLivre(TipoSolicitacao tipo, String codigo) {
        if (repository.existsByTipoSolicitacaoAndCodigo(tipo, codigo.trim().toUpperCase())) {
            throw new InvarianteVioladaException(
                    "Já existe uma variante de " + tipo + " com o código '" + codigo + "'.");
        }
    }

    private void destituirPadraoAnterior(FormularioSolicitacao novoPadrao) {
        repository.findByTipoSolicitacaoAndPadraoTrue(novoPadrao.getTipoSolicitacao()).stream()
                .filter(outro -> !outro.getId().equals(novoPadrao.getId()))
                .forEach(FormularioSolicitacao::deixarDeSerPadrao);
    }
}
