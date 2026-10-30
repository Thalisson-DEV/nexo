package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.domain.Supervisor;
import com.nexo.app.modules.rh.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Realiza a importação em lote de equipes via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarEquipesUseCase {

    private final EquipeRepository repository;
    private final TipoEquipeRepository tipoEquipeRepository;
    private final BaseOperacionalRepository baseRepository;
    private final VeiculoRepository veiculoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final CoordenadorRepository coordenadorRepository;
    private final SupervisorRepository supervisorRepository;
    private final ProcessoNegocioRepository processoRepository;
    private final ImportarEquipesAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoEquipeResponse executarValidacao(MultipartFile arquivo) {
        List<EquipeImportacaoDTO> registros = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            int numeroLinha = 0;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                
                // Ignorar cabeçalho
                if (numeroLinha == 1) continue;
                if (linha.isBlank() || linha.trim().startsWith("#")) continue;

                if (numeroLinha > LIMITE_LINHAS + 1) {
                    log.warn("Arquivo excede o limite de {} linhas", LIMITE_LINHAS);
                    break;
                }

                EquipeImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
                dto = validarRegistro(dto);
                
                if (dto.status() == ImportacaoStatus.COM_ERRO) {
                    falhas++;
                } else {
                    sucessos++;
                }
                registros.add(dto);
            }
        } catch (Exception e) {
            log.error("Falha crítica ao ler arquivo de importação", e);
            throw new RuntimeException("Falha ao ler o arquivo CSV: " + e.getMessage());
        }

        return new ImportacaoEquipeResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoEquipeResponse confirmarImportacao(ConfirmarImportacaoEquipeRequest request) {
        List<EquipeImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        // Re-validar todos os registros
        for (EquipeImportacaoDTO dto : request.equipes()) {
            EquipeImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        // Se houver qualquer falha, retorna a lista com os erros para correção
        if (falhas > 0) {
            return new ImportacaoEquipeResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        // Se tudo estiver ok, inicia processamento assíncrono
        List<EquipeImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoEquipeResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private EquipeImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String codigo = colunas.length > 0 ? colunas[0].trim() : "";
        String nomeTipoEquipe = colunas.length > 1 ? colunas[1].trim() : "";
        String nomeBase = colunas.length > 2 ? colunas[2].trim() : "";
        String placaVeiculo = colunas.length > 3 ? colunas[3].trim() : "";
        String nomeCoordenador = colunas.length > 4 ? colunas[4].trim() : "";
        String nomeSupervisor = colunas.length > 5 ? colunas[5].trim() : "";
        String nomeResponsavel = colunas.length > 6 ? colunas[6].trim() : "";
        String nomeProcesso = colunas.length > 7 ? colunas[7].trim() : "";

        return new EquipeImportacaoDTO(
                indice, codigo, nomeTipoEquipe, nomeBase, placaVeiculo,
                nomeCoordenador, nomeSupervisor, nomeResponsavel, nomeProcesso,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private EquipeImportacaoDTO validarRegistro(EquipeImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarCodigo(dto.codigo());
            validarReferencias(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(EquipeImportacaoDTO dto) {
        if (dto.codigo().length() > 50 || dto.codigo().length() < 3) {
            throw new IllegalArgumentException("Codigo deve ter entre 3 e 50 caracteres");
        }
    }

    private void validarCamposObrigatorios(EquipeImportacaoDTO dto) {
        if (dto.codigo().isBlank()) throw new IllegalArgumentException("Código é obrigatório");
        if (dto.nomeTipoEquipe().isBlank()) throw new IllegalArgumentException("Tipo de equipe é obrigatório");
        if (dto.nomeBase().isBlank()) throw new IllegalArgumentException("Nome da base é obrigatório");
        if (dto.placaVeiculo().isBlank()) throw new IllegalArgumentException("Placa do veículo é obrigatória");
        if (dto.nomeCoordenador().isBlank()) throw new IllegalArgumentException("Nome do coordenador é obrigatório");
        if (dto.nomeSupervisor().isBlank()) throw new IllegalArgumentException("Nome do supervisor é obrigatório");
        if (dto.nomeResponsavel().isBlank()) throw new IllegalArgumentException("Nome do responsável é obrigatório (A equipe deve possuir pelo menos um colaborador alocado)");
        if (dto.nomeProcesso().isBlank()) throw new IllegalArgumentException("Nome do processo é obrigatório");
    }

    private void validarCodigo(String codigo) {
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Código da equipe já cadastrado: " + codigo);
        }
    }

    private void validarReferencias(EquipeImportacaoDTO dto) {
        if (!tipoEquipeRepository.existsByNomeIgnoreCase(dto.nomeTipoEquipe())) {
            throw new IllegalArgumentException("Tipo de equipe não encontrado: " + dto.nomeTipoEquipe());
        }
        if (!baseRepository.existsByNomeIgnoreCase(dto.nomeBase())) {
            throw new IllegalArgumentException("Base Operacional não encontrada: " + dto.nomeBase());
        }
        if (!veiculoRepository.existsByPlacaIgnoreCase(dto.placaVeiculo())) {
            throw new IllegalArgumentException("Veículo não encontrado: " + dto.placaVeiculo());
        }

        String coordenadorHash = HashUtils.gerarBlindIndex(dto.nomeCoordenador().toUpperCase());
        Coordenador coordenador = coordenadorRepository.findByColaboradorNomeCompletoHashAndAtivoTrue(coordenadorHash)
                .orElseThrow(() -> new IllegalArgumentException("Coordenador ativo não encontrado: " + dto.nomeCoordenador()));

        String supervisorHash = HashUtils.gerarBlindIndex(dto.nomeSupervisor().toUpperCase());
        Supervisor supervisor = supervisorRepository.findByColaboradorNomeCompletoHashAndAtivoTrue(supervisorHash)
                .orElseThrow(() -> new IllegalArgumentException("Supervisor ativo não encontrado: " + dto.nomeSupervisor()));

        if (supervisor.getCoordenador() != null && !supervisor.getCoordenador().getId().equals(coordenador.getId())) {
            throw new IllegalArgumentException("O supervisor " + dto.nomeSupervisor() + " não responde ao coordenador " + dto.nomeCoordenador());
        }

        String responsavelHash = HashUtils.gerarBlindIndex(dto.nomeResponsavel().toUpperCase());
        if (!colaboradorRepository.existsByNomeCompletoHash(responsavelHash)) {
            throw new IllegalArgumentException("Responsável não encontrado: " + dto.nomeResponsavel());
        }

        if (!processoRepository.existsByNomeIgnoreCase(dto.nomeProcesso())) {
            throw new IllegalArgumentException("Processo não encontrado: " + dto.nomeProcesso());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Código da Equipe;Tipo de Equipe;Nome da Base Operacional;Placa do Veículo;Nome do Coordenador;Nome do Supervisor;Nome do Responsável;Nome do Processo\n";
        String exemplo = "# Exemplo: EQ-001;Linha Viva;Base Sul;ABC-1234;José (Coordenador);Maria (Supervisora);Pedro (Responsável);COMERCIAL\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
