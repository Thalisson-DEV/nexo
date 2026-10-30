package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.sst.application.dto.AtividadeSstFilter;
import com.nexo.app.modules.sst.application.dto.AtividadeSstRequest;
import com.nexo.app.modules.sst.application.dto.AtividadeSstResponse;
import com.nexo.app.modules.sst.application.mappers.AtividadeSstMapper;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SST_ATIVIDADES;

/**
 * Mantém as atividades pré-cadastradas sob as quais os eventos de SST acontecem.
 *
 * <p>O gestor é validado contra o RH quando informado, e o nome dele é resolvido na
 * leitura — aqui o vínculo é vivo, e a página inteira é resolvida em uma consulta só
 * para não cair em N+1. É no registro do <b>evento</b> que esse dado deixa de ser
 * vínculo e vira cópia.</p>
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = SST_ATIVIDADES)
public class AtividadeSstUseCases {

    private final AtividadeSstRepository repository;
    private final AtividadeSstMapper mapper;
    private final ColaboradorApi colaboradorApi;
    private final ProcessoNegocioApi processoNegocioApi;

    @Transactional
    @CacheEvict(allEntries = true)
    public AtividadeSstResponse criar(AtividadeSstRequest request) {
        validarGestor(request.idGestor());
        validarProcesso(request.idProcesso());
        validarUnicidade(request.nome(), null);

        AtividadeSst atividade = new AtividadeSst(
                request.nome(), request.descricao(), request.idGestor(), request.idProcesso());

        return hidratar(repository.save(atividade));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public AtividadeSstResponse atualizar(UUID id, AtividadeSstRequest request) {
        AtividadeSst atividade = buscarEntidade(id);
        validarGestor(request.idGestor());
        validarProcesso(request.idProcesso());
        validarUnicidade(request.nome(), id);

        atividade.atualizar(request.nome(), request.descricao(), request.idGestor(), request.idProcesso());

        return hidratar(repository.save(atividade));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public AtividadeSstResponse buscarPorId(UUID id) {
        return hidratar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<AtividadeSstResponse> listar(AtividadeSstFilter filter, Pageable pageable) {
        Page<AtividadeSst> pagina = repository.findAll(AtividadeSstSpecification.filterBy(filter), pageable);
        return PageResponse.fromPage(pagina.map(hidratadorDe(pagina.getContent())::apply));
    }

    /** Atividades ativas em ordem alfabética — abastece o campo do formulário de registro. */
    @Transactional(readOnly = true)
    @Cacheable(key = "'opcoes'")
    public List<AtividadeSstResponse> listarOpcoes() {
        List<AtividadeSst> ativas = repository.findByAtivoTrueOrderByNomeAsc();
        return ativas.stream().map(hidratadorDe(ativas)::apply).toList();
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        AtividadeSst atividade = buscarEntidade(id);
        atividade.desativar();
        repository.save(atividade);
    }

    /**
     * Reativa uma atividade. A unicidade é reavaliada aqui: enquanto ela esteve
     * desativada, outra pessoa pode ter cadastrado o mesmo nome.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        AtividadeSst atividade = buscarEntidade(id);
        validarUnicidade(atividade.getNome(), id);
        atividade.ativar();
        repository.save(atividade);
    }

    private void validarGestor(UUID idGestor) {
        if (idGestor != null && !colaboradorApi.existeColaborador(idGestor)) {
            throw new InvarianteVioladaException(
                    "Colaborador não encontrado ou inativo para ser gestor da atividade: " + idGestor);
        }
    }

    private void validarProcesso(UUID idProcesso) {
        if (idProcesso != null && !processoNegocioApi.existeProcesso(idProcesso)) {
            throw new RecursoNaoEncontradoException(
                    "Processo de negócio não encontrado ou inativo com ID: " + idProcesso);
        }
    }

    private void validarUnicidade(String nome, UUID idIgnorar) {
        String normalizado = SpecificationUtils.normalizarTermo(nome);
        if (normalizado == null || normalizado.isBlank()) {
            return; // O nome em branco é recusado pelo domínio, com mensagem própria.
        }

        boolean duplicada = idIgnorar == null
                ? repository.existsByNomeNormalizadoAndAtivoTrue(normalizado)
                : repository.existsByNomeNormalizadoAndAtivoTrueAndIdNot(normalizado, idIgnorar);

        if (duplicada) {
            throw new InvarianteVioladaException(
                    "Já existe uma atividade ativa chamada \"" + nome.trim() + "\".");
        }
    }

    private AtividadeSstResponse hidratar(AtividadeSst atividade) {
        return hidratadorDe(List.of(atividade)).apply(atividade);
    }

    /**
     * Resolve gestores e processos de uma lista inteira antes de montar as respostas:
     * um mapa por chamada, em vez de uma consulta por linha.
     */
    private Function<AtividadeSst, AtividadeSstResponse> hidratadorDe(List<AtividadeSst> atividades) {
        Set<UUID> idsGestores = atividades.stream()
                .map(AtividadeSst::getIdGestor)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, ResumoColaborador> gestores = idsGestores.isEmpty()
                ? Map.of()
                : colaboradorApi.buscarResumosPorIds(idsGestores);

        Map<UUID, String> processos = new HashMap<>();
        atividades.stream()
                .map(AtividadeSst::getIdProcesso)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(idProcesso -> processos.put(idProcesso,
                        processoNegocioApi.buscarPorIdResponse(idProcesso)
                                .map(ProcessoNegocioResponse::nome)
                                .orElse(null)));

        return atividade -> {
            AtividadeSstResponse base = mapper.toResponse(atividade);
            ResumoColaborador gestor = atividade.getIdGestor() == null
                    ? null : gestores.get(atividade.getIdGestor());

            return new AtividadeSstResponse(
                    base.id(), base.nome(), base.descricao(),
                    base.idGestor(),
                    gestor == null ? null : gestor.nomeCompleto(),
                    gestor == null ? null : gestor.funcao(),
                    base.idProcesso(),
                    atividade.getIdProcesso() == null ? null : processos.get(atividade.getIdProcesso()),
                    base.ativo());
        };
    }

    private AtividadeSst buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Atividade de SST não encontrada com ID: " + id));
    }
}
