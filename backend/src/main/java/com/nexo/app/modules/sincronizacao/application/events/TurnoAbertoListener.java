package com.nexo.app.modules.sincronizacao.application.events;

import com.nexo.app.modules.sincronizacao.application.usecases.SincronizacaoUseCases;
import com.nexo.app.shared.domain.events.TurnoAbertoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Nota recusada por falta de turno é a mesma nota que a equipe faria depois de abrir
 * o turno: quando a equipe abre o turno do dia, as notas daquele dia que esperavam
 * por ele são reenviadas sozinhas e o backoffice não precisa tratá-las.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TurnoAbertoListener {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final SincronizacaoUseCases useCases;

    @ApplicationModuleListener
    public void onTurnoAberto(TurnoAbertoEvent event) {
        var abertura = event.dataInicio().atZoneSameInstant(ZONA);
        String justificativa = "Reenviado automaticamente: a equipe abriu o turno às " + abertura.format(HORA) + ".";
        for (var idCaso : useCases.aguardandoTurnoDaEquipe(event.idEquipe(), abertura.toLocalDate())) {
            try {
                useCases.reenviarAutomaticamente(idCaso, justificativa);
                log.info("Caso de sincronização {} reenviado na abertura do turno {}", idCaso, event.idTurno());
            } catch (RuntimeException e) {
                // A falha fica registrada no caso, com o novo motivo, para o backoffice tratar.
                log.warn("Reenvio automático do caso {} falhou: {}", idCaso, e.getMessage());
            }
        }
    }
}
