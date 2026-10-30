package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.*;
import com.nexo.app.modules.execucao.application.mappers.TurnoMapper;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.Turno;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoSpecification;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ComposicaoEquipeApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.events.TurnoAbertoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "turnos")
public class TurnoUseCases implements TurnoApi {

    private final TurnoRepository repository;
    private final TurnoMapper mapper;
    private final UsuarioApi usuarioApi;
    private final ComposicaoEquipeApi composicaoEquipeApi;
    private final EquipeApi equipeApi;
    private final ContratoApi contratoApi;
    private final MetaDoCicloDaEquipe metaDoCicloDaEquipe;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @CacheEvict(allEntries = true)
    public TurnoResponse abrir(AbrirTurnoRequest request) {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();
        
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Usuário deve estar logado para abrir um turno.");
        }

        if (!usuarioApi.isUsuarioCampo(idUsuario)) {
            throw new InvarianteVioladaException("Apenas usuários de campo podem abrir turnos.");
        }

        // Verifica se a equipe já possui um turno aberto
        if (repository.existsByIdEquipeAndStatusAndAtivoTrue(request.idEquipe(), StatusTurno.ABERTO)) {
            throw new InvarianteVioladaException("Esta equipe já possui um turno aberto.");
        }

        // Conferência da equipe: o responsável confirma/corrige veículo e membros
        // no mesmo passo da abertura, sem depender do administrativo.
        boolean alterouComposicao = request.idVeiculo() != null || request.idsColaboradores() != null;
        ComposicaoEquipeApi.ComposicaoEquipe composicao = alterouComposicao
                ? composicaoEquipeApi.atualizarPeloResponsavel(
                        request.idEquipe(), idUsuario, request.idVeiculo(), request.idsColaboradores())
                : composicaoEquipeApi.buscarComposicao(request.idEquipe()).orElse(null);

        Turno turno = new Turno(request.idEquipe(), idUsuario);
        if (composicao != null) {
            turno.registrarComposicao(
                    composicao.veiculo() != null ? composicao.veiculo().id() : null,
                    composicao.membros().stream().map(ComposicaoEquipeApi.MembroComposicao::idColaborador).toList());
        }
        Turno salvo = repository.save(turno);
        eventPublisher.publishEvent(new TurnoAbertoEvent(salvo.getId(), salvo.getIdEquipe(), salvo.getDataInicio()));
        return mapper.toResponse(salvo);
    }

    /**
     * Composição atual da equipe do usuário logado e opções para a conferência
     * na abertura do turno.
     */
    @Transactional(readOnly = true)
    public ComposicaoTurnoResponse buscarComposicaoMinhaEquipe() {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Usuário deve estar logado.");
        }
        UUID idEquipe = equipeApi.buscarIdEquipePorUsuario(idUsuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada para o usuário logado."));
        ComposicaoEquipeApi.ComposicaoEquipe composicao = composicaoEquipeApi.buscarComposicao(idEquipe)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada para o usuário logado."));

        List<ContratoApi.AcaoValorItem> acoes = equipeApi.buscarContratoDaEquipe(idEquipe)
                .map(idContrato -> contratoApi.listarAcoesComValor(idContrato, LocalDate.now()))
                .orElse(List.of());

        return new ComposicaoTurnoResponse(
                composicao,
                composicaoEquipeApi.listarOpcoes(idEquipe),
                idUsuario.equals(composicao.idUsuarioResponsavel()),
                acoes,
                metaDoCicloDaEquipe.apurar(idEquipe).orElse(null)
        );
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void fechar(UUID id) {
        Turno turno = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turno não encontrado"));
        
        if (turno.isAberto()) {
            turno.fechar();
            repository.save(turno);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(key = "'ativo:' + #idEquipe", unless = "#result == null")
    public Optional<TurnoResponse> buscarAtivoPorEquipe(UUID idEquipe) {
        return repository.findFirstByIdEquipeAndStatusAndAtivoTrue(idEquipe, StatusTurno.ABERTO)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TurnoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turno não encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoResponse> listarSobrepostosAoPeriodo(OffsetDateTime inicio, OffsetDateTime fim,
                                                         Collection<UUID> idsEquipes) {
        if (idsEquipes == null || idsEquipes.isEmpty()) {
            return List.of();
        }
        return repository.listarSobrepostosAoPeriodo(inicio, fim, idsEquipes).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TurnoResponse> consultar(ConsultaTurnos consulta, Pageable pageable) {
        return repository.findAll(TurnoSpecification.filtrarPor(consulta), pageable).map(mapper::toResponse);
    }
}
