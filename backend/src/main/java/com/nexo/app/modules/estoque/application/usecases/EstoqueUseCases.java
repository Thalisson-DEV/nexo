package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.EstoqueFilter;
import com.nexo.app.modules.estoque.application.dto.EstoqueRequest;
import com.nexo.app.modules.estoque.application.dto.EstoqueResponse;
import com.nexo.app.modules.estoque.application.mappers.EstoqueMapper;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueSpecification;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
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

import java.util.EnumSet;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.ESTOQUES;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = ESTOQUES)
public class EstoqueUseCases {

    private final EstoqueRepository repository;
    private final TipoEstoqueRepository tipoEstoqueRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository saldoSerieRepository;
    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final EstoqueMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public EstoqueResponse criar(EstoqueRequest request) {
        validarUnicidade(request.nomeIdentificador());

        TipoEstoque tipo = tipoEstoqueRepository.findById(request.idTipoEstoque())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de estoque não encontrado"));

        Estoque entity = new Estoque(request.nomeIdentificador(), tipo);
        entity.vincularBaseOperacional(request.idBaseOperacional());

        if (request.idEquipe() != null) {
            entity.vincularEquipe(request.idEquipe());
        }

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public EstoqueResponse atualizar(UUID id, EstoqueRequest request) {
        Estoque entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque não encontrado"));

        if (!entity.getNomeIdentificador().equalsIgnoreCase(request.nomeIdentificador())) {
            validarUnicidade(request.nomeIdentificador());
        }

        TipoEstoque tipo = tipoEstoqueRepository.findById(request.idTipoEstoque())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de estoque não encontrado"));

        if (entity.getIdObra() != null && !"OBRA".equalsIgnoreCase(tipo.getNome())) {
            throw new InvarianteVioladaException(
                    "O estoque patrimonial vinculado a uma obra nao pode ter seu tipo substituido.");
        }

        entity.atualizarNome(request.nomeIdentificador());
        entity.vincularTipoEstoque(tipo);
        entity.vincularBaseOperacional(request.idBaseOperacional());

        if (request.idEquipe() != null) {
            entity.vincularEquipe(request.idEquipe());
        } else {
            entity.desvincularEquipe();
        }

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<EstoqueResponse> listar(EstoqueFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(EstoqueSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public EstoqueResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque não encontrado"));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Estoque entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque não encontrado"));
        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Estoque entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque não encontrado"));
        validarDesativacaoEstoqueObra(entity, id);
        entity.desativar();
        repository.save(entity);
    }

    private void validarDesativacaoEstoqueObra(Estoque estoque, UUID idEstoque) {
        if (estoque.getIdObra() == null) {
            return;
        }
        boolean possuiCustodia = saldoRepository.existsSaldoPositivoByEstoqueId(idEstoque)
                || saldoSerieRepository.existsByEstoqueIdAndAtivoTrue(idEstoque);
        boolean possuiSolicitacaoAberta = solicitacaoRepository
                .existsByIdObraAndContextoAndEncerradaEmIsNullAndStatusIn(
                        estoque.getIdObra(), ContextoSolicitacaoMaterial.TECNICO,
                        EnumSet.of(StatusSolicitacao.PENDENTE, StatusSolicitacao.APROVADO,
                                StatusSolicitacao.ENTREGUE_PARCIAL, StatusSolicitacao.DEVOLVIDO_PARCIAL));
        if (possuiCustodia || possuiSolicitacaoAberta) {
            throw new InvarianteVioladaException(
                    "O estoque patrimonial da obra possui custodia ou solicitacoes tecnicas abertas e nao pode ser desativado.");
        }
    }

    private void validarUnicidade(String nome) {
        if (repository.existsByNomeIdentificadorIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe um estoque com este nome identificador");
        }
    }
}
