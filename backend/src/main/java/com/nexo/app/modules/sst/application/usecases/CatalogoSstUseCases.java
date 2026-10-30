package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.CatalogoSstFilter;
import com.nexo.app.modules.sst.application.dto.CatalogoSstRequest;
import com.nexo.app.modules.sst.application.dto.CatalogoSstResponse;
import com.nexo.app.modules.sst.application.mappers.CatalogoSstMapper;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SST_CATALOGOS;

/**
 * Mantém os vocabulários de SST — classificação do evento, local e natureza da lesão
 * e agente causador.
 *
 * <p>Os quatro dividem uma tabela discriminada por {@link TipoCatalogoSst}, o que
 * transfere para cá duas garantias que o banco não dá sozinho: o tipo de uma entrada
 * nunca muda depois de criada, e a unicidade do nome vale <b>dentro</b> do tipo, entre
 * as entradas ativas.</p>
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = SST_CATALOGOS)
public class CatalogoSstUseCases {

    private final CatalogoSstRepository repository;
    private final CatalogoSstMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public CatalogoSstResponse criar(CatalogoSstRequest request) {
        validarUnicidade(request.tipo(), request.nome(), null);

        CatalogoSst entrada = new CatalogoSst(
                request.tipo(), request.nome(), request.descricao(), request.codigo(), request.ordem());

        return mapper.toResponse(repository.save(entrada));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CatalogoSstResponse atualizar(UUID id, CatalogoSstRequest request) {
        CatalogoSst entrada = buscarEntidade(id);
        validarTipoImutavel(entrada, request.tipo());
        validarUnicidade(entrada.getTipo(), request.nome(), id);

        entrada.atualizar(request.nome(), request.descricao(), request.codigo(), request.ordem());

        return mapper.toResponse(repository.save(entrada));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public CatalogoSstResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<CatalogoSstResponse> listar(CatalogoSstFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(CatalogoSstSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    /**
     * Entradas ativas de um vocabulário, na ordem de exibição — é o que abastece um
     * campo do formulário de registro de evento, sem paginação.
     */
    @Transactional(readOnly = true)
    @Cacheable(key = "'opcoes-' + #tipo")
    public List<CatalogoSstResponse> listarOpcoes(TipoCatalogoSst tipo) {
        return repository.findByTipoAndAtivoTrueOrderByOrdemAscNomeAsc(tipo).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        CatalogoSst entrada = buscarEntidade(id);
        entrada.desativar();
        repository.save(entrada);
    }

    /**
     * Reativa uma entrada. A unicidade é reavaliada aqui: enquanto ela esteve
     * desativada, outra pessoa pode ter cadastrado o mesmo nome.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        CatalogoSst entrada = buscarEntidade(id);
        validarUnicidade(entrada.getTipo(), entrada.getNome(), id);
        entrada.ativar();
        repository.save(entrada);
    }

    private void validarTipoImutavel(CatalogoSst entrada, TipoCatalogoSst tipoInformado) {
        if (tipoInformado != null && !entrada.isDoTipo(tipoInformado)) {
            throw new InvarianteVioladaException(
                    "O tipo de uma entrada de catálogo não pode ser alterado — os eventos que já a "
                            + "referenciam mudariam de significado. Cadastre uma entrada no outro tipo.");
        }
    }

    private void validarUnicidade(TipoCatalogoSst tipo, String nome, UUID idIgnorar) {
        String normalizado = SpecificationUtils.normalizarTermo(nome);
        if (normalizado == null || normalizado.isBlank()) {
            return; // O nome em branco é recusado pelo domínio, com mensagem própria.
        }

        boolean duplicado = idIgnorar == null
                ? repository.existsByTipoAndNomeNormalizadoAndAtivoTrue(tipo, normalizado)
                : repository.existsByTipoAndNomeNormalizadoAndAtivoTrueAndIdNot(tipo, normalizado, idIgnorar);

        if (duplicado) {
            throw new InvarianteVioladaException(
                    "Já existe uma entrada ativa chamada \"" + nome.trim() + "\" em " + tipo.getRotulo() + ".");
        }
    }

    private CatalogoSst buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Entrada de catálogo de SST não encontrada com ID: " + id));
    }
}
