package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.execucao.TipoServicoApi;
import com.nexo.app.modules.execucao.application.dto.TipoServicoFilter;
import com.nexo.app.modules.execucao.application.dto.TipoServicoRequest;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.modules.execucao.application.mappers.TipoServicoMapper;
import com.nexo.app.modules.execucao.domain.TipoServico;
import com.nexo.app.modules.execucao.infrastructure.persistence.TipoServicoRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TipoServicoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.TIPOS_SERVICO;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = TIPOS_SERVICO)
public class TipoServicoUseCases implements TipoServicoApi {

    private final TipoServicoRepository repository;
    private final TipoServicoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoServicoResponse criar(TipoServicoRequest request) {
        validarUnicidade(request.descricao(), null);
        return mapper.toResponse(repository.save(new TipoServico(request.codigo(), request.descricao())));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoServicoResponse atualizar(UUID id, TipoServicoRequest request) {
        TipoServico tipo = buscarEntidade(id);
        validarUnicidade(request.descricao(), id);
        tipo.atualizar(request.codigo(), request.descricao());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void alternarStatus(UUID id) {
        TipoServico tipo = buscarEntidade(id);
        if (tipo.isAtivo()) {
            tipo.desativar();
        } else {
            validarUnicidade(tipo.getDescricao(), tipo.getId());
            tipo.ativar();
        }
        repository.save(tipo);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public TipoServicoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<TipoServicoResponse> listar(TipoServicoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(TipoServicoSpecification.comFiltro(filter), pageable)
                .map(mapper::toResponse));
    }

    /** Tipos disponíveis para novos lançamentos, em ordem alfabética — abastece o app de campo. */
    @Transactional(readOnly = true)
    @Cacheable(key = "'opcoes'")
    public List<TipoServicoResponse> listarOpcoes() {
        return repository.listarAtivosOrdenados().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(key = "'todos'")
    public List<TipoServicoResponse> listarTodos() {
        return repository.findAll(Sort.by("descricao")).stream().map(mapper::toResponse).toList();
    }

    /**
     * Confere o tipo informado num lançamento. Um tipo desativado depois da escolha
     * continua aceito: o encarregado pode tê-lo escolhido offline, quando ainda valia.
     */
    @Transactional(readOnly = true)
    public void validarParaLancamento(UUID id) {
        if (!repository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Tipo de serviço não encontrado com o ID: " + id);
        }
    }

    private TipoServico buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de serviço não encontrado: " + id));
    }

    private void validarUnicidade(String descricao, UUID idIgnorado) {
        if (descricao == null || descricao.isBlank()) {
            return;
        }
        String normalizada = SpecificationUtils.normalizarTermo(descricao.trim().replaceAll("\\s+", " "));
        repository.findByDescricaoNormalizadaAndAtivoTrue(normalizada)
                .filter(existente -> idIgnorado == null || !existente.getId().equals(idIgnorado))
                .ifPresent(existente -> {
                    throw new InvarianteVioladaException(
                            "Já existe um tipo de serviço ativo com a descrição '" + descricao.trim() + "'.");
                });
    }
}
