package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.shared.domain.events.VeiculoCadastradoEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.nexo.app.modules.rh.application.dto.VeiculoImportacaoDTO;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Veiculo;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.VeiculoRepository;
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
@CacheConfig(cacheNames = "veiculos")
public class ImportarVeiculosAsyncService {

    private final VeiculoRepository repository;
    private final BaseOperacionalRepository baseOperacionalRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<VeiculoImportacaoDTO> veiculos) {
        log.info("Iniciando persistência assíncrona de {} veículos", veiculos.size());
        
        for (VeiculoImportacaoDTO dto : veiculos) {
            try {
                BaseOperacional baseOperacional = baseOperacionalRepository.findByNomeIgnoreCase(dto.nomeBaseOperacional())
                        .orElseThrow();

                Veiculo veiculo = new Veiculo(
                        dto.placa(),
                        dto.modelo(),
                        dto.fabricante(),
                        baseOperacional
                );

                Veiculo salvo = repository.save(veiculo);
                eventPublisher.publishEvent(new VeiculoCadastradoEvent(salvo.getId(), salvo.getPlaca(), baseOperacional.getId()));
            } catch (Exception e) {
                log.error("Erro ao persistir veículo da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de veículos");
    }
}
