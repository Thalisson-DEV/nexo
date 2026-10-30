package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.execucao.application.dto.UltimoLancamentoDoTurno;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.Turno;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Encerra os turnos que a equipe esqueceu abertos depois do horário de corte do dia.
 *
 * <p>O fim registrado é o do último lançamento do turno — ou a própria abertura, sem
 * lançamentos —, nunca o momento do encerramento: a jornada não passa do que a equipe
 * trabalhou e as análises de duração e ritmo não são distorcidas. O turno guarda que foi
 * encerrado assim e o motivo, como informativo.</p>
 *
 * <p>Pega também turnos de cortes anteriores (servidor fora do ar no horário), então pode
 * rodar a qualquer momento e quantas vezes for preciso.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncerrarTurnosEsquecidosUseCase {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final TurnoRepository turnoRepository;
    private final ExecucaoComercialRepository execucaoRepository;
    private final CorteDiarioDoTurno corteDiario;

    @Transactional
    @CacheEvict(cacheNames = "turnos", allEntries = true)
    public int executar(OffsetDateTime agora) {
        OffsetDateTime corte = corteDiario.ultimoCorteAte(agora);
        List<Turno> esquecidos = turnoRepository.findByStatusAndAtivoTrueAndDataInicioBefore(StatusTurno.ABERTO, corte);
        if (esquecidos.isEmpty()) {
            return 0;
        }

        Map<UUID, OffsetDateTime> ultimos = execucaoRepository
                .buscarUltimosLancamentos(esquecidos.stream().map(Turno::getId).toList())
                .stream()
                .collect(Collectors.toMap(UltimoLancamentoDoTurno::idTurno, UltimoLancamentoDoTurno::dataLancamento));

        for (Turno turno : esquecidos) {
            OffsetDateTime ultimo = ultimos.get(turno.getId());
            // Relógio adiantado do aparelho não estende o turno além de agora.
            OffsetDateTime fim = ultimo == null ? turno.getDataInicio() : (ultimo.isAfter(agora) ? agora : ultimo);
            turno.encerrarAutomaticamente(fim, motivo(ultimo != null, fim));
            log.info("Turno {} da equipe {} encerrado automaticamente (aberto em {}, fim {}).",
                    turno.getId(), turno.getIdEquipe(), turno.getDataInicio(), turno.getDataFim());
        }
        turnoRepository.saveAll(esquecidos);
        return esquecidos.size();
    }

    private String motivo(boolean teveLancamento, OffsetDateTime fim) {
        String corte = corteDiario.horario().format(HORA);
        String quando = fim.atZoneSameInstant(corteDiario.zona()).format(DATA_HORA);
        return teveLancamento
                ? "Turno não encerrado pela equipe. Encerrado automaticamente no corte das " + corte
                        + "; o fim foi ajustado para o último lançamento (" + quando + ")."
                : "Turno não encerrado pela equipe. Encerrado automaticamente no corte das " + corte
                        + "; sem lançamentos, o fim foi igualado à abertura.";
    }
}
