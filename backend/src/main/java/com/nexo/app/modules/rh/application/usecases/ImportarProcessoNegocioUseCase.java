package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
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
 * Realiza a importação em lote de processos de negócio via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarProcessoNegocioUseCase {

    private final ProcessoNegocioRepository repository;
    private final ImportarProcessoNegocioAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoProcessoNegocioResponse executarValidacao(MultipartFile arquivo) {
        List<ProcessoNegocioImportacaoDTO> registros = new ArrayList<>();
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

                ProcessoNegocioImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoProcessoNegocioResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoProcessoNegocioResponse confirmarImportacao(ConfirmarImportacaoProcessoNegocioRequest request) {
        List<ProcessoNegocioImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (ProcessoNegocioImportacaoDTO dto : request.processos()) {
            ProcessoNegocioImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoProcessoNegocioResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<ProcessoNegocioImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoProcessoNegocioResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private ProcessoNegocioImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nome = colunas.length > 0 ? colunas[0].trim() : "";

        return new ProcessoNegocioImportacaoDTO(
                indice, nome,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private ProcessoNegocioImportacaoDTO validarRegistro(ProcessoNegocioImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarDuplicidade(dto.nome());
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(ProcessoNegocioImportacaoDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do processo é obrigatório");
        }
        if (dto.nome().length() > 50 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome do processo deve ter entre 3 e 50 caracteres");
        }
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Processo de negócio já cadastrado no sistema");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome do Processo de Negócio\n";
        String exemplo = "# Exemplo: COMERCIAL\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
