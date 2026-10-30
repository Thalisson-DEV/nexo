package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.AcaoImportacaoDTO;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
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
@CacheConfig(cacheNames = "acoes")
public class ImportarAcoesAsyncService {

    private final AcaoRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<AcaoImportacaoDTO> acoes) {
        log.info("Iniciando persistência assíncrona de {} ações", acoes.size());
        
        for (AcaoImportacaoDTO dto : acoes) {
            try {
                Acao acao = new Acao(
                        dto.codigo(),
                        dto.nome()
                );

                repository.save(acao);
            } catch (Exception e) {
                log.error("Erro ao persistir ação da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de ações");
    }
}
