package com.nexo.app.modules.chamado.application.usecases;

import com.nexo.app.modules.chamado.application.dto.TopicoChamadoFilter;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoResponse;
import com.nexo.app.modules.chamado.application.mappers.ChamadoMapper;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.modules.chamado.domain.TopicoChamado;
import com.nexo.app.modules.chamado.infrastructure.persistence.TopicoChamadoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.TopicoChamadoSpecification;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopicoChamadoUseCases {

    private final TopicoChamadoRepository repository;
    private final EquipeAtendimentoUseCases equipeUseCases;
    private final ChamadoMapper mapper;

    @Transactional
    @CacheEvict(value = CacheNames.CHAMADO_TOPICOS, allEntries = true)
    public TopicoChamadoResponse criar(TopicoChamadoRequest request) {
        exigirCodigoUnico(request.codigo(), null);
        EquipeAtendimento equipe = equipeUseCases.buscarEntity(request.equipePadraoId());
        TopicoChamado topico = novoTopico(request, equipe);
        return mapper.toResponse(repository.save(topico));
    }

    @Transactional
    @CacheEvict(value = CacheNames.CHAMADO_TOPICOS, allEntries = true)
    public TopicoChamadoResponse atualizar(UUID id, TopicoChamadoRequest request) {
        TopicoChamado topico = buscarEntity(id);
        exigirCodigoUnico(request.codigo(), id);
        EquipeAtendimento equipe = equipeUseCases.buscarEntity(request.equipePadraoId());
        topico.atualizar(request.codigo(), request.nome(), request.descricao(),
                request.prioridadePadrao(), equipe, request.slaPrimeiraRespostaHoras(),
                request.slaResolucaoHoras(), request.pausarSlaAguardandoSolicitante(),
                request.prazoReaberturaHoras(), request.ordemExibicao());
        return mapper.toResponse(repository.save(topico));
    }

    @Transactional
    @CacheEvict(value = CacheNames.CHAMADO_TOPICOS, allEntries = true)
    public void desativar(UUID id) {
        TopicoChamado topico = buscarEntity(id);
        topico.desativar();
        repository.save(topico);
    }

    @Transactional
    @CacheEvict(value = CacheNames.CHAMADO_TOPICOS, allEntries = true)
    public void ativar(UUID id) {
        TopicoChamado topico = buscarEntity(id);
        topico.getEquipePadrao().exigirDisponivel();
        topico.ativar();
        repository.save(topico);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CHAMADO_TOPICOS, key = "'ativos'")
    public List<TopicoChamadoResponse> listarDisponiveis() {
        return repository.findAllByAtivoTrueOrderByOrdemExibicaoAscNomeAsc().stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TopicoChamadoResponse> listarAdministracao(
            TopicoChamadoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                TopicoChamadoSpecification.comFiltros(filtro), pageable).map(mapper::toResponse));
    }

    public TopicoChamado buscarEntity(UUID id) {
        if (id == null) {
            throw new InvarianteVioladaException("Selecione um tópico para o chamado.");
        }
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tópico de chamado não encontrado."));
    }

    private TopicoChamado novoTopico(TopicoChamadoRequest request, EquipeAtendimento equipe) {
        return new TopicoChamado(request.codigo(), request.nome(), request.descricao(),
                request.prioridadePadrao(), equipe, request.slaPrimeiraRespostaHoras(),
                request.slaResolucaoHoras(), request.pausarSlaAguardandoSolicitante(),
                request.prazoReaberturaHoras(), request.ordemExibicao());
    }

    private void exigirCodigoUnico(String codigo, UUID idIgnorado) {
        repository.findByCodigoIgnoreCase(codigo).ifPresent(existente -> {
            if (!existente.getId().equals(idIgnorado)) {
                throw new InvarianteVioladaException("Já existe um tópico com o código informado.");
            }
        });
    }
}
