package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.application.dto.TurnoObraRequests;
import com.nexo.app.modules.obras.domain.ApontamentoExecucao;
import com.nexo.app.modules.obras.domain.AtividadeTurno;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.SituacaoLiberacao;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AprTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AtividadeTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.OcorrenciaTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.shared.domain.events.ItemExecutadoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Apontamento da execução pelo web. Só entra com a APR da obra concluída no turno. O primeiro apontamento coloca a
 * obra em execução e cada um deles entra na atividade do turno em que foi feito.
 */
@Service
@RequiredArgsConstructor
public class ApontamentoExecucaoUseCases {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String STATUS_EM_EXECUCAO = "EM EXECUÇÃO";

    private final ApontamentoExecucaoRepository repository;
    private final ProgramacaoRepository programacaoRepository;
    private final AtividadeTurnoRepository atividadeRepository;
    private final AprTurnoRepository aprRepository;
    private final OcorrenciaTurnoRepository ocorrenciaRepository;
    private final ObraRepository obraRepository;
    private final StatusObraRepository statusObraRepository;
    private final ProgramacaoObraUseCases programacaoUseCases;
    private final TurnoApi turnoApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public ProgramacaoResponses.ProgramacaoDetalhe apontar(UUID idProgramacao, TurnoObraRequests.Apontar request) {
        Programacao programacao = buscarProgramacao(idProgramacao);
        if (request.data().isAfter(LocalDate.now(FUSO))) {
            throw new InvarianteVioladaException("A data do apontamento não pode ser no futuro.");
        }
        UUID idTurno = turnoDoApontamento(programacao, request.idTurno(), request.data());
        exigirLiberacao(programacao, idTurno, request.data());
        boolean primeiroDaObra = !repository.existsByIdObraAndAtivoTrue(programacao.getIdObra());

        for (TurnoObraRequests.LinhaApontamento linha : request.itens()) {
            ApontamentoExecucao apontamento = repository.save(programacao.apontar(linha.idItem(), linha.quantidade(),
                    request.data(), idTurno, linha.justificativa()));
            eventPublisher.publishEvent(new ItemExecutadoEvent(apontamento.getId(), linha.idItem(), programacao.getIdObra(),
                    programacao.getId(), programacao.getIdEquipe(), idTurno, request.data(), linha.quantidade(), apontamento.getValorTotal()));
        }
        if (idTurno != null && !atividadeRepository.existsByIdTurnoAndIdProgramacaoAndAtivoTrue(idTurno, programacao.getId())) {
            atividadeRepository.save(new AtividadeTurno(idTurno, programacao.getIdObra(), programacao.getId(), null));
        }
        if (primeiroDaObra) {
            colocarObraEmExecucao(programacao.getIdObra());
        }
        repository.flush();
        return programacaoUseCases.buscarPorId(idProgramacao);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public void estornar(UUID idApontamento) {
        ApontamentoExecucao apontamento = repository.findByIdAndAtivoTrue(idApontamento)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Apontamento não encontrado."));
        buscarProgramacao(apontamento.getIdProgramacao()).estornar(apontamento);
        repository.save(apontamento);
    }

    private UUID turnoDoApontamento(Programacao programacao, UUID informado, LocalDate data) {
        if (informado != null) {
            TurnoResponse turno = turnoApi.buscarPorId(informado);
            if (!Objects.equals(turno.idEquipe(), programacao.getIdEquipe())) {
                throw new InvarianteVioladaException("O turno informado é de outra equipe, não da equipe da programação #" + programacao.getNumero() + ".");
            }
            return informado;
        }
        OffsetDateTime inicio = data.atStartOfDay(FUSO).toOffsetDateTime();
        return turnoApi.listarSobrepostosAoPeriodo(inicio, inicio.plusDays(1), List.of(programacao.getIdEquipe())).stream()
                .max(Comparator.comparing(TurnoResponse::dataInicio))
                .map(TurnoResponse::id)
                .orElse(null);
    }

    /** A APR pode ter sido feita em outro turno do mesmo dia: vale o dia da equipe, não só o turno do apontamento. */
    private void exigirLiberacao(Programacao programacao, UUID idTurno, LocalDate data) {
        if (idTurno == null) {
            throw new InvarianteVioladaException("A equipe não tem turno nessa data. Abra o turno e conclua a APR antes de apontar.");
        }
        OffsetDateTime inicio = data.atStartOfDay(FUSO).toOffsetDateTime();
        Set<UUID> turnosDoDia = new HashSet<>(Set.of(idTurno));
        turnoApi.listarSobrepostosAoPeriodo(inicio, inicio.plusDays(1), List.of(programacao.getIdEquipe()))
                .forEach(turno -> turnosDoDia.add(turno.id()));
        SituacaoLiberacao situacao = SituacaoLiberacao.avaliar(programacao.getIdObra(), programacao.getId(), data, FUSO,
                aprRepository.findByIdTurnoInAndAtivoTrue(turnosDoDia), ocorrenciaRepository.findByIdTurnoInAndAtivoTrue(turnosDoDia));
        if (situacao == SituacaoLiberacao.IMPEDIDA) {
            throw new InvarianteVioladaException("Foi registrado que o serviço nessa obra não tem condição de ser feito. Conclua uma nova APR ou retire o impedimento antes de apontar.");
        }
        if (situacao != SituacaoLiberacao.LIBERADA) {
            throw new InvarianteVioladaException("A APR deste serviço precisa estar concluída no dia antes de apontar a execução.");
        }
    }

    /** Obra encerrada não reabre por apontamento; sem o status cadastrado, ela fica como está. */
    private void colocarObraEmExecucao(UUID idObra) {
        Obra obra = obraRepository.findById(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));
        if (obra.getStatusObra().isConcluida()) {
            return;
        }
        statusObraRepository.findByNomeIgnoreCase(STATUS_EM_EXECUCAO)
                .filter(status -> !status.equals(obra.getStatusObra()))
                .ifPresent(status -> obra.informarStatusOperacional(status, "Primeiro apontamento de execução.", null));
    }

    private Programacao buscarProgramacao(UUID id) {
        return programacaoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Programação não encontrada."));
    }
}
