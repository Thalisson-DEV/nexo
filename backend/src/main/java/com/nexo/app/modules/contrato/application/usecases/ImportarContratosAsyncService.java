package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.ContratoImportacaoDTO;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "contratos")
public class ImportarContratosAsyncService {

    private final ContratoRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<ContratoImportacaoDTO> contratos) {
        log.info("Iniciando persistência assíncrona de {} contratos", contratos.size());

        for (ContratoImportacaoDTO dto : contratos) {
            try {
                Contrato contrato = new Contrato(
                        dto.numeroContrato(),
                        dto.cliente(),
                        dto.dataVigencia(),
                        dto.dataEncerramento(),
                        dto.coeficienteFaturamento()
                );

                repository.save(contrato);
            } catch (Exception e) {
                log.error("Erro ao persistir contrato da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de contratos");
    }
}
