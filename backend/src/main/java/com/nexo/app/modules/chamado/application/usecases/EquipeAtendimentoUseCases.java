package com.nexo.app.modules.chamado.application.usecases;

import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoFilter;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoRequest;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoResponse;
import com.nexo.app.modules.chamado.application.mappers.ChamadoMapper;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.EquipeAtendimentoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.EquipeAtendimentoSpecification;
import com.nexo.app.modules.chamado.infrastructure.persistence.TopicoChamadoRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipeAtendimentoUseCases {

    private final EquipeAtendimentoRepository repository;
    private final TopicoChamadoRepository topicoRepository;
    private final ChamadoRepository chamadoRepository;
    private final ChamadoMapper mapper;

    @Transactional
    @CacheEvict(value = CacheNames.CHAMADO_EQUIPES, allEntries = true)
    public EquipeAtendimentoResponse criar(EquipeAtendimentoRequest request) {
        exigirCodigoUnico(request.codigo(), null);
        EquipeAtendimento equipe = new EquipeAtendimento(request.codigo(), request.nome(),
                request.descricao(), request.membros());
        return mapper.toResponse(repository.save(equipe));
    }

    @Transactional
    @CacheEvict(value = {CacheNames.CHAMADO_EQUIPES, CacheNames.CHAMADO_TOPICOS}, allEntries = true)
    public EquipeAtendimentoResponse atualizar(UUID id, EquipeAtendimentoRequest request) {
        EquipeAtendimento equipe = buscarEntity(id);
        exigirCodigoUnico(request.codigo(), id);
        equipe.atualizar(request.codigo(), request.nome(), request.descricao(), request.membros());
        return mapper.toResponse(repository.save(equipe));
    }

    @Transactional
    @CacheEvict(value = {CacheNames.CHAMADO_EQUIPES, CacheNames.CHAMADO_TOPICOS}, allEntries = true)
    public void desativar(UUID id) {
        EquipeAtendimento equipe = buscarEntity(id);
        if (topicoRepository.existsByEquipePadraoIdAndAtivoTrue(id)) {
            throw new InvarianteVioladaException(
                    "Desative ou redirecione os tópicos ativos desta equipe antes de desativá-la.");
        }
        if (chamadoRepository.existsByEquipeAtendimentoIdAndAtivoTrueAndStatusIn(id,
                List.of(StatusChamado.ABERTO, StatusChamado.EM_ATENDIMENTO,
                        StatusChamado.AGUARDANDO_SOLICITANTE, StatusChamado.RESOLVIDO))) {
            throw new InvarianteVioladaException(
                    "Encaminhe ou finalize os chamados ativos desta equipe antes de desativá-la.");
        }
        equipe.desativar();
        repository.save(equipe);
    }

    @Transactional
    @CacheEvict(value = CacheNames.CHAMADO_EQUIPES, allEntries = true)
    public void ativar(UUID id) {
        EquipeAtendimento equipe = buscarEntity(id);
        equipe.ativar();
        repository.save(equipe);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CHAMADO_EQUIPES,
            key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort + '-'"
                    + " + (#filtro != null ? #filtro.hashCode() : 'sem-filtro')")
    public PageResponse<EquipeAtendimentoResponse> listar(EquipeAtendimentoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                EquipeAtendimentoSpecification.comFiltros(filtro), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public EquipeAtendimentoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    public EquipeAtendimento buscarEntity(UUID id) {
        if (id == null) {
            throw new InvarianteVioladaException("Informe a equipe de atendimento.");
        }
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe de atendimento não encontrada."));
    }

    private void exigirCodigoUnico(String codigo, UUID idIgnorado) {
        repository.findByCodigoIgnoreCase(codigo).ifPresent(existente -> {
            if (!existente.getId().equals(idIgnorado)) {
                throw new InvarianteVioladaException("Já existe uma equipe com o código informado.");
            }
        });
    }
}
