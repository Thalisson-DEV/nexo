package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.FornecedorFilter;
import com.nexo.app.modules.financeiro.application.dto.FornecedorRequest;
import com.nexo.app.modules.financeiro.application.dto.FornecedorResponse;
import com.nexo.app.modules.financeiro.application.mappers.FornecedorMapper;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FornecedorRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FornecedorSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cadastro de fornecedores e sua curadoria pelo financeiro.
 *
 * <p>O fluxo assume que o solicitante encontra fornecedores novos no meio do
 * trabalho: qualquer um com permissão de escrita cria o registro com o mínimo, ele
 * nasce pendente e já pode ser usado. O financeiro depois completa os dados e
 * homologa — que é quando razão social e documento passam a ser cobrados.</p>
 */
@Service
@RequiredArgsConstructor
public class FornecedorUseCases {

    private final FornecedorRepository repository;
    private final FornecedorMapper mapper;

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public FornecedorResponse criar(FornecedorRequest request) {
        var fornecedor = new Fornecedor(request.nomeFantasia(), request.razaoSocial(), request.documento(),
                request.dadosBancarios(), request.observacoes());
        exigirDocumentoInedito(fornecedor.getDocumento(), null);
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public FornecedorResponse atualizar(UUID id, FornecedorRequest request) {
        var fornecedor = buscarEntity(id);
        fornecedor.atualizar(request.nomeFantasia(), request.razaoSocial(), request.documento(),
                request.dadosBancarios(), request.observacoes());
        exigirDocumentoInedito(fornecedor.getDocumento(), id);
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public FornecedorResponse homologar(UUID id) {
        var fornecedor = buscarEntity(id);
        fornecedor.homologar();
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public FornecedorResponse bloquear(UUID id, String motivo) {
        var fornecedor = buscarEntity(id);
        fornecedor.bloquear(motivo);
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public FornecedorResponse desbloquear(UUID id) {
        var fornecedor = buscarEntity(id);
        fornecedor.desbloquear();
        return mapper.toResponse(repository.save(fornecedor));
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public void desativar(UUID id) {
        var fornecedor = buscarEntity(id);
        fornecedor.desativar();
        repository.save(fornecedor);
    }

    @Transactional
    @CacheEvict(value = CacheNames.FINANCEIRO_FORNECEDORES, allEntries = true)
    public void ativar(UUID id) {
        var fornecedor = buscarEntity(id);
        fornecedor.ativar();
        repository.save(fornecedor);
    }

    @Transactional(readOnly = true)
    public FornecedorResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.FINANCEIRO_FORNECEDORES,
            key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort + '-' "
                    + "+ (#filtro != null ? #filtro.hashCode() : 'sem-filtro')")
    public PageResponse<FornecedorResponse> listar(FornecedorFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(FornecedorSpecification.comFiltros(filtro), pageable).map(mapper::toResponse));
    }

    Fornecedor buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado."));
    }

    /**
     * O documento é a única chave natural confiável — sem essa trava, o mesmo
     * fornecedor apareceria várias vezes na lista, que é justamente o problema que
     * o cadastro veio resolver.
     */
    private void exigirDocumentoInedito(String documento, UUID idIgnorado) {
        if (documento == null) {
            return;
        }
        repository.findByDocumento(documento).ifPresent(existente -> {
            if (!existente.getId().equals(idIgnorado)) {
                throw new InvarianteVioladaException(
                        "Já existe o fornecedor '" + existente.getNomeFantasia() + "' com este CNPJ/CPF.");
            }
        });
    }
}
