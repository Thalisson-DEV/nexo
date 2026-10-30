package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.application.dto.VeiculoFilter;
import com.nexo.app.modules.rh.application.dto.VeiculoRequest;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.rh.application.mappers.VeiculoMapper;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Veiculo;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.VeiculoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.VeiculoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.VeiculoAlteradoEvent;
import com.nexo.app.shared.domain.events.VeiculoCadastradoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "veiculos")
public class VeiculoUseCases implements VeiculoApi {

    private final VeiculoRepository repository;
    private final BaseOperacionalRepository baseRepository;
    private final EquipeRepository equipeRepository;
    private final VeiculoMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @CacheEvict(allEntries = true)
    public VeiculoResponse criar(VeiculoRequest request) {
        if (repository.existsByPlacaIgnoreCase(request.placa())) {
            throw new InvarianteVioladaException("Já existe um veículo cadastrado com esta placa");
        }

        BaseOperacional base = baseRepository.findById(request.baseOperacionalId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base operacional não encontrada"));

        Veiculo veiculo = new Veiculo(
                request.placa(),
                request.modelo(),
                request.fabricante(),
                base
        );

        Veiculo salvo = repository.save(veiculo);
        eventPublisher.publishEvent(new VeiculoCadastradoEvent(salvo.getId(), salvo.getPlaca(), base.getId()));
        return mapper.toResponse(salvo);
    }

    @Override
    @Transactional
    @CacheEvict(allEntries = true)
    public VeiculoResponse cadastrar(DadosCadastroVeiculo dados) {
        return criar(new VeiculoRequest(dados.placa(), dados.modelo(), dados.fabricante(), dados.idBaseOperacional()));
    }

    @Override
    @Transactional
    @CacheEvict(allEntries = true)
    public VeiculoResponse alterarCadastro(UUID id, DadosCadastroVeiculo dados) {
        return atualizar(id, new VeiculoRequest(dados.placa(), dados.modelo(), dados.fabricante(), dados.idBaseOperacional()));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public VeiculoResponse atualizar(UUID id, VeiculoRequest request) {
        Veiculo veiculo = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado"));

        BaseOperacional base = baseRepository.findById(request.baseOperacionalId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base operacional não encontrada"));

        String placaNova = request.placa().toUpperCase().replace("-", "").trim();
        if (!placaNova.equalsIgnoreCase(veiculo.getPlaca()) && repository.existsByPlacaIgnoreCase(placaNova)) {
            throw new InvarianteVioladaException("Já existe um veículo cadastrado com esta placa");
        }

        veiculo.atualizar(request.placa(), request.modelo(), request.fabricante(), base);

        Veiculo salvo = repository.save(veiculo);
        publicarAlteracao(salvo);
        return mapper.toResponse(salvo);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Veiculo veiculo = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado"));

        veiculo.desativar();
        publicarAlteracao(repository.save(veiculo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Veiculo entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado"));

        entity.ativar();
        publicarAlteracao(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public VeiculoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado"));
    }

    @Transactional(readOnly = true)
    public PageResponse<VeiculoResponse> listar(VeiculoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(VeiculoSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VeiculoBaseData buscarDadosBase(UUID veiculoId) {
        return repository.findById(veiculoId)
                .map(v -> new VeiculoBaseData(
                        v.getId(),
                        v.getBaseOperacional().getLatitude(),
                        v.getBaseOperacional().getLongitude(),
                        v.getBaseOperacional().getRaio()
                ))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VeiculoBaseData> listarDadosBaseAtivos() {
        return repository.findAll().stream()
                .filter(Veiculo::isAtivo)
                .collect(Collectors.toMap(
                        Veiculo::getId,
                        v -> new VeiculoBaseData(
                                v.getId(),
                                v.getBaseOperacional().getLatitude(),
                                v.getBaseOperacional().getLongitude(),
                                v.getBaseOperacional().getRaio()
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VeiculoCompletoData> listarVeiculosCompletosAtivos(List<UUID> baseIds) {
        List<Veiculo> veiculos = (baseIds == null || baseIds.isEmpty()) 
                ? repository.findAllByAtivoTrue() 
                : repository.findAllByBaseOperacionalIdInAndAtivoTrue(baseIds);
        
        return veiculos.stream()
                .map(v -> new VeiculoCompletoData(
                        v.getId(), v.getPlaca(), 
                        v.getBaseOperacional().getId(), v.getBaseOperacional().getNome(),
                        v.getBaseOperacional().getLatitude(), v.getBaseOperacional().getLongitude(),
                        v.getBaseOperacional().getRaio()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VeiculoEquipeData> listarVinculosEquipeAtivos() {
        return equipeRepository.findAllByAtivoTrue().stream()
                .filter(e -> e.getVeiculo() != null)
                .collect(Collectors.toMap(
                        e -> e.getVeiculo().getId(),
                        e -> new VeiculoEquipeData(e.getVeiculo().getId(), e.getId(), e.getCodigo()),
                        (existente, novo) -> existente
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listarIdsVeiculosAtivos(List<UUID> baseIds) {
        if (baseIds == null || baseIds.isEmpty()) {
            return repository.findAllByAtivoTrue().stream().map(Veiculo::getId).toList();
        }
        return repository.findAllByBaseOperacionalIdInAndAtivoTrue(baseIds).stream().map(Veiculo::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> listarPlacasAtivas() {
        return repository.findAll().stream()
                .filter(Veiculo::isAtivo)
                .map(Veiculo::getPlaca)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, UUID> mapaPlacasIds() {
        return repository.findAll().stream()
                .filter(Veiculo::isAtivo)
                .collect(Collectors.toMap(
                        Veiculo::getPlaca,
                        Veiculo::getId
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVeiculoCadastrado(String placa) {
        if (placa == null) return false;
        String placaLimpa = placa.toUpperCase().replace("-", "").trim();
        return repository.existsByPlacaIgnoreCase(placaLimpa);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeVeiculo(UUID id) {
        return repository.existsByIdAndAtivoTrue(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPorTrecho(String trecho) {
        String termo = SpecificationUtils.normalizarTermo(trecho);
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        return repository.buscarIdsPorTrecho(termo, termo.replaceAll("[^a-z0-9]", ""));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<VeiculoResponse> buscarPorIdResponse(UUID id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VeiculoResponse> buscarPorIdsResponse(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Veiculo::getId, mapper::toResponse));
    }

    private void publicarAlteracao(Veiculo veiculo) {
        eventPublisher.publishEvent(new VeiculoAlteradoEvent(
                veiculo.getId(), veiculo.getPlaca(), veiculo.getBaseOperacional().getId(), veiculo.isAtivo()));
    }
}
