package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.EventoSstImportacaoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarEventosSstAsyncService {

    private final MontadorEventoSstImportacao montador;
    private final EventoSstUseCases eventoUseCases;

    @Async
    public void processarPersistencia(List<EventoSstImportacaoDTO> eventos) {
        log.info("Iniciando persistência assíncrona de {} eventos de SST", eventos.size());
        for (EventoSstImportacaoDTO dto : eventos) {
            try {
                eventoUseCases.registrar(montador.montarEValidar(dto));
            } catch (Exception e) {
                log.error("Erro ao persistir evento de SST da linha {}: {}", dto.linha(), e.getMessage(), e);
            }
        }
        log.info("Finalizada persistência assíncrona de eventos de SST");
    }
}
