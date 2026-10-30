package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.MunicipioImportacaoDTO;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Municipio;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.MunicipioRepository;
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
@CacheConfig(cacheNames = "municipios")
public class ImportarMunicipiosAsyncService {

    private final MunicipioRepository repository;
    private final BaseOperacionalRepository baseOperacionalRepository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<MunicipioImportacaoDTO> municipios) {
        log.info("Iniciando persistência assíncrona de {} municípios", municipios.size());
        
        for (MunicipioImportacaoDTO dto : municipios) {
            try {
                BaseOperacional base = baseOperacionalRepository.findByNomeIgnoreCase(dto.nomeBaseOperacional())
                        .orElseThrow(() -> new IllegalArgumentException("Base operacional não encontrada: " + dto.nomeBaseOperacional()));

                Municipio municipio = new Municipio(
                        dto.nome(),
                        base
                );

                repository.save(municipio);
            } catch (Exception e) {
                log.error("Erro ao persistir município da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de municípios");
    }
}
