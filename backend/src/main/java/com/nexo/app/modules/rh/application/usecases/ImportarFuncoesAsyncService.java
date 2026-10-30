package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.FuncaoImportacaoDTO;
import com.nexo.app.modules.rh.domain.Funcao;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
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
@CacheConfig(cacheNames = "funcoes")
public class ImportarFuncoesAsyncService {

    private final FuncaoRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<FuncaoImportacaoDTO> funcoes) {
        log.info("Iniciando persistência assíncrona de {} funções", funcoes.size());
        
        for (FuncaoImportacaoDTO dto : funcoes) {
            try {
                if (repository.existsByNomeIgnoreCase(dto.nome())) {
                    log.warn("Função já cadastrada, ignorando linha {}: {}", dto.linha(), dto.nome());
                    continue;
                }

                Funcao funcao = new Funcao(
                        dto.nome(),
                        dto.descricao()
                );

                repository.save(funcao);
            } catch (Exception e) {
                log.error("Erro ao persistir função da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de funções");
    }
}
