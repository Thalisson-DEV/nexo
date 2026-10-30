package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.infrastructure.persistence.TipoEquipeRepository;
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
 * Realiza a importação em lote de tipos de equipe via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarTipoEquipeUseCase {

    private final TipoEquipeRepository repository;
    private final ImportarTipoEquipeAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoTipoEquipeResponse executarValidacao(MultipartFile arquivo) {
        List<TipoEquipeImportacaoDTO> registros = new ArrayList<>();
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

                TipoEquipeImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoTipoEquipeResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoTipoEquipeResponse confirmarImportacao(ConfirmarImportacaoTipoEquipeRequest request) {
        List<TipoEquipeImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (TipoEquipeImportacaoDTO dto : request.tiposEquipe()) {
            TipoEquipeImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoTipoEquipeResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<TipoEquipeImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoTipoEquipeResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private TipoEquipeImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nome = colunas.length > 0 ? colunas[0].trim() : "";

        return new TipoEquipeImportacaoDTO(
                indice, nome,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private TipoEquipeImportacaoDTO validarRegistro(TipoEquipeImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.nome());
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(TipoEquipeImportacaoDTO dto) {
        if (dto.nome().length() > 50 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome do tipo de equipe deve ter entre 3 e 50 caracteres");
        }
    }

    private void validarCamposObrigatorios(TipoEquipeImportacaoDTO dto) {
        if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome do tipo de equipe é obrigatório");
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Tipo de equipe já cadastrado no sistema");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome do Tipo de Equipe\n";
        String exemplo = "# Exemplo: LINHA VIVA\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
