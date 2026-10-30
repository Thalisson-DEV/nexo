package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.AcaoValorImportacaoDTO;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.modules.contrato.domain.AcaoValor;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoValorRepository;
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
@CacheConfig(cacheNames = "acoes-valores")
public class ImportarAcaoValoresAsyncService {

    private final AcaoValorRepository repository;
    private final AcaoRepository acaoRepository;
    private final ContratoRepository contratoRepository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<AcaoValorImportacaoDTO> registros) {
        log.info("Iniciando persistência assíncrona de {} valores de ações", registros.size());
        
        for (AcaoValorImportacaoDTO dto : registros) {
            try {
                Acao acao = acaoRepository.findByCodigoIgnoreCase(dto.codigoAcao())
                        .orElseThrow(() -> new IllegalArgumentException("Ação não encontrada: " + dto.codigoAcao()));
                
                Contrato contrato = contratoRepository.findByNumeroContratoIgnoreCase(dto.numeroContrato())
                        .orElseThrow(() -> new IllegalArgumentException("Contrato não encontrado: " + dto.numeroContrato()));

                AcaoValor acaoValor = new AcaoValor(
                        acao,
                        contrato,
                        dto.valorBase(),
                        dto.dataVigencia()
                );

                repository.save(acaoValor);
            } catch (Exception e) {
                log.error("Erro ao persistir valor da ação na linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de valores de ações");
    }
}
