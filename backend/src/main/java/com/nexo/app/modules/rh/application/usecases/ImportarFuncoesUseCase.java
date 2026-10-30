package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Realiza a importação em lote de funções via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarFuncoesUseCase {

    private final FuncaoRepository repository;
    private final ImportarFuncoesAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoFuncaoResponse executarValidacao(MultipartFile arquivo) {
        List<FuncaoImportacaoDTO> registros = new ArrayList<>();
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

                FuncaoImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoFuncaoResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoFuncaoResponse confirmarImportacao(ConfirmarImportacaoFuncaoRequest request) {
        List<FuncaoImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (FuncaoImportacaoDTO dto : request.funcoes()) {
            FuncaoImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoFuncaoResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<FuncaoImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoFuncaoResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private FuncaoImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nome = colunas.length > 0 ? colunas[0].trim() : "";
        String descricao = colunas.length > 1 ? colunas[1].trim() : "";

        return new FuncaoImportacaoDTO(
                indice, nome, descricao,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private FuncaoImportacaoDTO validarRegistro(FuncaoImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.nome());
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(FuncaoImportacaoDTO dto) {
        if (dto.nome().length() > 100 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome deve ter entre 3 e 100 caracteres");
        }

        if (!dto.descricao().isBlank() && dto.descricao().length() > 255 || dto.descricao().length() < 3) {
            throw new IllegalArgumentException("A descrição deve ter entre 3 e 255 caracteres");
        }
    }

    private void validarCamposObrigatorios(FuncaoImportacaoDTO dto) {
        if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome da função é obrigatório");
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Função já cadastrada no sistema");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome da Função;Descrição\n";
        String exemplo = "# Exemplo: ELETRICISTA I;Responsável por instalações elétricas de baixa tensão\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
