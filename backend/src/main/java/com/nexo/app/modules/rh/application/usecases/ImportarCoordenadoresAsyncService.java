package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.CoordenadorImportacaoDTO;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorRepository;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import com.nexo.app.shared.infrastructure.security.HashUtils;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = CacheNames.COORDENADORES)
public class ImportarCoordenadoresAsyncService {

    private final CoordenadorRepository repository;
    private final ColaboradorRepository colaboradorRepository;

    private static final DateTimeFormatter FORMATTER_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATTER_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<CoordenadorImportacaoDTO> coordenadores) {
        log.info("Iniciando persistência assíncrona de {} coordenadores", coordenadores.size());

        for (CoordenadorImportacaoDTO dto : coordenadores) {
            try {
                Optional<Colaborador> colaboradorOpt = buscarColaborador(dto.cpfColaborador(), dto.nomeColaborador());
                if (colaboradorOpt.isEmpty()) {
                    log.warn("Colaborador não encontrado na linha {}: CPF={}, Nome={}", dto.linha(), dto.cpfColaborador(), dto.nomeColaborador());
                    continue;
                }

                Colaborador colaborador = colaboradorOpt.get();
                if (!colaborador.isAtivo()) {
                    log.warn("Colaborador inativo na linha {}: {}", dto.linha(), colaborador.getNomeCompleto());
                    continue;
                }

                if (repository.existsByColaboradorIdAndAtivoTrue(colaborador.getId())) {
                    log.warn("Colaborador já é coordenador ativo na linha {}: {}", dto.linha(), colaborador.getNomeCompleto());
                    continue;
                }

                LocalDate dataInicio = parseData(dto.dataInicio());
                LocalDate dataFim = parseData(dto.dataFim());

                Coordenador coordenador = new Coordenador(colaborador, dataInicio, dataFim);
                repository.save(coordenador);
            } catch (Exception e) {
                log.error("Erro ao persistir coordenador da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de coordenadores");
    }

    private Optional<Colaborador> buscarColaborador(String cpf, String nome) {
        if (cpf != null && !cpf.isBlank()) {
            String cpfFormatado = cpf.replace("-", "").replace(".", "").trim();
            String hash = HashUtils.gerarBlindIndex(cpfFormatado);
            Optional<Colaborador> porCpf = colaboradorRepository.findByCpfHash(hash);
            if (porCpf.isPresent()) {
                return porCpf;
            }
        }

        if (nome != null && !nome.isBlank()) {
            String hash = HashUtils.gerarBlindIndex(nome.trim().toUpperCase());
            return colaboradorRepository.findByNomeCompletoHash(hash);
        }

        return Optional.empty();
    }

    private LocalDate parseData(String dataStr) {
        if (dataStr == null || dataStr.isBlank()) {
            return null;
        }
        String limpo = dataStr.trim();
        if (limpo.contains("/")) {
            return LocalDate.parse(limpo, FORMATTER_BR);
        }
        return LocalDate.parse(limpo, FORMATTER_ISO);
    }
}
