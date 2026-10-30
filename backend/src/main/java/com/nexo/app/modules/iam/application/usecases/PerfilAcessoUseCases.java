package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.PerfilAcessoFilter;
import com.nexo.app.modules.iam.application.dto.PerfilAcessoRequest;
import com.nexo.app.modules.iam.application.dto.PerfilAcessoResponse;
import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.modules.iam.application.dto.PermissoesPerfilRequest;
import com.nexo.app.modules.iam.application.mappers.PerfilAcessoMapper;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.Permissao;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoSpecification;
import com.nexo.app.modules.iam.infrastructure.persistence.PermissaoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.PERMISSOES_PERFIL;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "perfis-acessos")
public class PerfilAcessoUseCases {

    private final PerfilAcessoRepository repository;
    private final PermissaoRepository permissaoRepository;
    private final PerfilAcessoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public PerfilAcessoResponse criar(PerfilAcessoRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome())) {
            throw new InvarianteVioladaException("Já existe um perfil de acesso com este nome");
        }

        PerfilAcesso perfil = new PerfilAcesso(request.nome());
        return mapper.toResponse(repository.save(perfil));
    }

    @Transactional
    @CacheEvict(value = {"perfis-acessos", PERMISSOES_PERFIL}, key = "#id")
    public void ativar(UUID id) {
        PerfilAcesso perfilAcesso = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        perfilAcesso.ativar();
        repository.save(perfilAcesso);
    }

    @Transactional
    @CacheEvict(value = {"perfis-acessos", PERMISSOES_PERFIL}, key = "#id")
    public void desativar(UUID id) {
        repository.findById(id)
                .ifPresentOrElse(
                        perfil -> {
                            perfil.desativar();
                            repository.save(perfil);
                        },
                        () -> { throw new RecursoNaoEncontradoException("Perfil de acesso não encontrado"); }
                );
    }

    @Transactional
    @CacheEvict(value = {"perfis-acessos", PERMISSOES_PERFIL}, key = "#id")
    public PerfilAcessoResponse atualizar(UUID id, PerfilAcessoRequest request) {
        PerfilAcesso perfil = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        repository.findByNomeIgnoreCase(request.nome())
                .filter(p -> !p.getId().equals(id))
                .ifPresent(p -> {
                    throw new InvarianteVioladaException("Já existe outro perfil com este nome");
                });

        perfil.atualizar(request.nome());
        return mapper.toResponse(repository.save(perfil));
    }

    @Transactional
    @CacheEvict(value = PERMISSOES_PERFIL, key = "#idPerfil")
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public void vincularPermissao(UUID idPerfil, UUID idPermissao) {
        PerfilAcesso perfil = repository.findById(idPerfil)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        Permissao permissao = permissaoRepository.findById(idPermissao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Permissão não encontrada"));

        perfil.adicionarPermissao(permissao);
        repository.save(perfil);
    }

    @Transactional
    @CacheEvict(value = PERMISSOES_PERFIL, key = "#idPerfil")
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public void removerPermissao(UUID idPerfil, UUID idPermissao) {
        PerfilAcesso perfil = repository.findById(idPerfil)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        Permissao permissao = permissaoRepository.findById(idPermissao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Permissão não encontrada"));

        perfil.removerPermissao(permissao);
        repository.save(perfil);
    }

    /**
     * Grava o conjunto completo de permissões numa transação só. A tela manda tudo o que está
     * marcado; antes ela disparava uma requisição por permissão, em paralelo, e as requisições
     * disputavam a versão do mesmo perfil (erro de "registro alterado por outro usuário").
     */
    @Transactional
    @CacheEvict(value = {"perfis-acessos", PERMISSOES_PERFIL}, key = "#idPerfil")
    public List<PermissaoResponse> definirPermissoes(UUID idPerfil, PermissoesPerfilRequest request) {
        PerfilAcesso perfil = repository.findById(idPerfil)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        List<Permissao> permissoes = permissaoRepository.findAllById(request.idsPermissoes());
        if (permissoes.size() != request.idsPermissoes().size()) {
            throw new RecursoNaoEncontradoException(
                    "Uma ou mais permissões não existem mais. Recarregue a tela e tente de novo.");
        }

        perfil.definirPermissoes(permissoes);
        return repository.save(perfil).getPermissoes().stream()
                .map(p -> new PermissaoResponse(p.getId(), p.getNome()))
                .sorted(java.util.Comparator.comparing(PermissaoResponse::nome))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissaoResponse> listarPermissoesPorPerfil(UUID idPerfil) {
        PerfilAcesso perfil = repository.findById(idPerfil)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        return perfil.getPermissoes().stream()
                .map(p -> new PermissaoResponse(p.getId(), p.getNome()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public PerfilAcessoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter != null ? #filter.toString() : 'no-filter', #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<PerfilAcessoResponse> listar(PerfilAcessoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(PerfilAcessoSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }
}
