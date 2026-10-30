package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.*;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
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
 * Realiza a importação em lote de perfis de acesso via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarPerfilAcessoUseCase {

    private final PerfilAcessoRepository repository;
    private final ImportarPerfilAcessoAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoPerfilAcessoResponse executarValidacao(MultipartFile arquivo) {
        List<PerfilAcessoImportacaoDTO> registros = new ArrayList<>();
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

                PerfilAcessoImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoPerfilAcessoResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoPerfilAcessoResponse confirmarImportacao(ConfirmarImportacaoPerfilAcessoRequest request) {
        List<PerfilAcessoImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (PerfilAcessoImportacaoDTO dto : request.perfis()) {
            PerfilAcessoImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoPerfilAcessoResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<PerfilAcessoImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoPerfilAcessoResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private PerfilAcessoImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nome = colunas.length > 0 ? colunas[0].trim() : "";

        return new PerfilAcessoImportacaoDTO(
                indice, nome,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private PerfilAcessoImportacaoDTO validarRegistro(PerfilAcessoImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.nome());
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(PerfilAcessoImportacaoDTO dto) {
        if (dto.nome().length() > 50 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome do perfil deve ter entre 3 e 50 caracteres");
        }
    }

    private void validarCamposObrigatorios(PerfilAcessoImportacaoDTO dto) {
        if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome do perfil é obrigatório");
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Perfil de acesso já cadastrado no sistema");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome do Perfil de Acesso\n";
        String exemplo = "# Exemplo: GERENTE_OPERACIONAL\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
