package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.AtualizarFormularioDadosRequest;
import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResumoResponse;
import com.nexo.app.modules.sst.application.dto.ReordenarRequest;
import com.nexo.app.modules.sst.application.mappers.FormularioInspecaoMapper;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.GrupoPergunta;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ExecucaoChecklistGovernancaSstRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.FORMULARIOS_INSPECAO;

/**
 * Orquestra o ciclo de vida dos formulários de inspeção (templates dinâmicos).
 *
 * <p>A estrutura (grupos/perguntas/opções) só pode ser editada enquanto o
 * formulário não tiver sido utilizado em nenhuma inspeção. Após o primeiro uso,
 * apenas os metadados podem ser alterados, ou o formulário pode ser duplicado
 * para gerar uma nova versão.</p>
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = FORMULARIOS_INSPECAO)
public class FormularioInspecaoUseCases {

    private final FormularioInspecaoRepository repository;
    private final InspecaoSegurancaRepository inspecaoRepository;
    private final ExecucaoChecklistGovernancaSstRepository checklistGovernancaRepository;
    private final FormularioInspecaoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse criar(CriarFormularioRequest request) {
        validarUnicidadeTitulo(request.titulo(), null);

        FormularioInspecao formulario = new FormularioInspecao(request.titulo(), request.descricao());
        preencherEstrutura(formulario, request);
        formulario.validarEstrutura();

        return hidratar(repository.save(formulario));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse atualizarEstrutura(UUID id, CriarFormularioRequest request) {
        FormularioInspecao formulario = buscarEntidade(id);

        if (formularioEmUso(id)) {
            throw new InvarianteVioladaException(
                    "O formulário já foi utilizado em inspeções e não pode ter a estrutura alterada. "
                            + "Duplique-o para criar uma nova versão.");
        }

        validarUnicidadeTitulo(request.titulo(), id);

        formulario.atualizarDados(request.titulo(), request.descricao());
        formulario.getGrupos().clear();
        preencherEstrutura(formulario, request);
        formulario.validarEstrutura();

        return hidratar(repository.save(formulario));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse atualizarDados(UUID id, AtualizarFormularioDadosRequest request) {
        FormularioInspecao formulario = buscarEntidade(id);
        validarUnicidadeTitulo(request.titulo(), id);
        formulario.atualizarDados(request.titulo(), request.descricao());
        return hidratar(repository.save(formulario));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse duplicar(UUID id) {
        FormularioInspecao original = buscarEntidade(id);

        FormularioInspecao copia = new FormularioInspecao(
                gerarTituloCopia(original.getTitulo()), original.getDescricao());

        original.getGrupos().forEach(grupo -> {
            GrupoPergunta novoGrupo = copia.adicionarGrupo(grupo.getTitulo());
            grupo.getPerguntas().forEach(pergunta -> {
                var novaPergunta = novoGrupo.adicionarPergunta(
                        pergunta.getEnunciado(), pergunta.getTipoResposta(), pergunta.isObrigatoria());
                pergunta.getOpcoes().forEach(opcao ->
                        novaPergunta.adicionarOpcao(opcao.getTexto(), opcao.isGeraDesvio(), opcao.getGravidade(), opcao.isPadrao()));
            });
        });

        return hidratar(repository.save(copia));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        FormularioInspecao formulario = buscarEntidade(id);
        formulario.desativar();
        repository.save(formulario);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        FormularioInspecao formulario = buscarEntidade(id);
        formulario.ativar();
        repository.save(formulario);
    }

    /**
     * Reordena os grupos do formulário. Diferente da edição estrutural, a
     * reordenação é permitida mesmo após o formulário ter sido usado: ela apenas
     * altera a posição de apresentação, preservando IDs, respostas e desvios.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse reordenarGrupos(UUID idFormulario, ReordenarRequest request) {
        FormularioInspecao formulario = buscarEntidade(idFormulario);
        formulario.reordenarGrupos(request.idsNaOrdem());
        return hidratar(repository.save(formulario));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse reordenarPerguntas(UUID idFormulario, UUID idGrupo, ReordenarRequest request) {
        FormularioInspecao formulario = buscarEntidade(idFormulario);
        formulario.reordenarPerguntas(idGrupo, request.idsNaOrdem());
        return hidratar(repository.save(formulario));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public FormularioInspecaoResponse reordenarOpcoes(UUID idFormulario, UUID idGrupo, UUID idPergunta, ReordenarRequest request) {
        FormularioInspecao formulario = buscarEntidade(idFormulario);
        formulario.reordenarOpcoes(idGrupo, idPergunta, request.idsNaOrdem());
        return hidratar(repository.save(formulario));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public FormularioInspecaoResponse buscarPorId(UUID id) {
        return hidratar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<FormularioInspecaoResumoResponse> listar(FormularioInspecaoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(FormularioInspecaoSpecification.filterBy(filter), pageable)
                        .map(this::toResumo)
        );
    }

    private void preencherEstrutura(FormularioInspecao formulario, CriarFormularioRequest request) {
        request.grupos().forEach(grupoReq -> {
            GrupoPergunta grupo = formulario.adicionarGrupo(grupoReq.titulo());
            grupoReq.perguntas().forEach(perguntaReq -> {
                var pergunta = grupo.adicionarPergunta(
                        perguntaReq.enunciado(), perguntaReq.tipoResposta(), perguntaReq.obrigatoria());
                perguntaReq.opcoes().forEach(opcaoReq ->
                        pergunta.adicionarOpcao(opcaoReq.texto(), opcaoReq.geraDesvio(), opcaoReq.gravidade(), opcaoReq.padrao()));
            });
        });
    }

    private FormularioInspecaoResponse hidratar(FormularioInspecao formulario) {
        FormularioInspecaoResponse base = mapper.toResponse(formulario);
        boolean emUso = formulario.getId() != null && formularioEmUso(formulario.getId());
        return new FormularioInspecaoResponse(
                base.id(), base.titulo(), base.descricao(), base.ativo(), emUso, base.grupos());
    }

    private boolean formularioEmUso(UUID idFormulario) {
        return inspecaoRepository.existsByIdFormulario(idFormulario)
                || checklistGovernancaRepository.existsByIdFormulario(idFormulario);
    }

    private FormularioInspecaoResumoResponse toResumo(FormularioInspecao formulario) {
        int totalPerguntas = formulario.getGrupos().stream()
                .mapToInt(g -> g.getPerguntas().size())
                .sum();
        return new FormularioInspecaoResumoResponse(
                formulario.getId(), formulario.getTitulo(), formulario.getDescricao(),
                formulario.isAtivo(), totalPerguntas);
    }

    private FormularioInspecao buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Formulário de inspeção não encontrado com ID: " + id));
    }

    private void validarUnicidadeTitulo(String titulo, UUID idIgnorar) {
        repository.findByTituloIgnoreCase(titulo)
                .filter(f -> idIgnorar == null || !f.getId().equals(idIgnorar))
                .ifPresent(f -> {
                    throw new InvarianteVioladaException("Já existe um formulário de inspeção com este título.");
                });
    }

    private String gerarTituloCopia(String titulo) {
        return (titulo + " (cópia)").substring(0, Math.min(titulo.length() + 8, 150));
    }
}
