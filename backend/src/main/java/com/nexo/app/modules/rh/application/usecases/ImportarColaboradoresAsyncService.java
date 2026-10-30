package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.ColaboradorImportacaoDTO;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Funcao;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "colaboradores")
public class ImportarColaboradoresAsyncService {

    private final ColaboradorRepository repository;
    private final ProcessoNegocioRepository processoRepository;
    private final BaseOperacionalRepository baseRepository;
    private final FuncaoRepository funcaoRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<ColaboradorImportacaoDTO> colaboradores) {
        log.info("Iniciando persistência assíncrona de {} colaboradores", colaboradores.size());
        
        for (ColaboradorImportacaoDTO dto : colaboradores) {
            try {
                // Re-busca as entidades para garantir que estão no contexto de persistência
                ProcessoNegocio processo = processoRepository.findByNomeIgnoreCase(dto.nomeProcesso())
                        .orElseThrow();
                BaseOperacional base = baseRepository.findByNomeIgnoreCase(dto.nomeBase())
                        .orElseThrow();
                Funcao funcao = funcaoRepository.findByNomeIgnoreCase(dto.nomeFuncao())
                        .orElseThrow();

                LocalDate dataContratacao = null;
                if (dto.dataContratacao() != null && !dto.dataContratacao().isBlank()) {
                    dataContratacao = LocalDate.parse(dto.dataContratacao(), DATE_FORMATTER);
                }

                Colaborador colaborador = new Colaborador(
                        dto.primeiroNome(),
                        dto.ultimoNome(),
                        dto.nomeCompleto(),
                        dto.cpf().replaceAll("[^0-9]", ""),
                        processo,
                        base,
                        funcao,
                        dataContratacao
                );

                repository.save(colaborador);
            } catch (Exception e) {
                log.error("Erro ao persistir colaborador da linha {}: {}", dto.linha(), e.getMessage());
                // Em um cenário real, poderíamos notificar o usuário via WebSocket ou salvar um log de erro de processamento assíncrono
            }
        }
        log.info("Finalizada persistência assíncrona de colaboradores");
    }
}
