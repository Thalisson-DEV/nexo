package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.AcaoImportacaoDTO;
import com.nexo.app.modules.contrato.application.dto.ConfirmarImportacaoAcaoRequest;
import com.nexo.app.modules.contrato.application.dto.ImportacaoAcaoResponse;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
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
 * Realiza a importação em lote de ações via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarAcoesUseCase {

    private final AcaoRepository repository;
    private final ImportarAcoesAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoAcaoResponse executarValidacao(MultipartFile arquivo) {
        List<AcaoImportacaoDTO> registros = new ArrayList<>();
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

                AcaoImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoAcaoResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoAcaoResponse confirmarImportacao(ConfirmarImportacaoAcaoRequest request) {
        List<AcaoImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (AcaoImportacaoDTO dto : request.acoes()) {
            AcaoImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoAcaoResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<AcaoImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoAcaoResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private AcaoImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String codigo = colunas.length > 0 ? colunas[0].trim() : "";
        String nome = colunas.length > 1 ? colunas[1].trim() : "";

        return new AcaoImportacaoDTO(
                indice, codigo, nome,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private AcaoImportacaoDTO validarRegistro(AcaoImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.codigo());
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(AcaoImportacaoDTO dto) {
        if (dto.codigo().length() > 20 || dto.codigo().length() < 3) {
            throw new IllegalArgumentException("Código da ação deve ter entre 3 e 20 caracteres");
        }
        if (dto.nome().length() > 255 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome da ação deve ter entre 3 e 255 caracteres");
        }
    }

    private void validarCamposObrigatorios(AcaoImportacaoDTO dto) {
        if (dto.codigo().isBlank()) throw new IllegalArgumentException("Código da ação é obrigatório");
        if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome da ação é obrigatório");
    }

    private void validarDuplicidade(String codigo) {
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ação com este código já cadastrada no sistema");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Código da Ação;Nome da Ação\n";
        String exemplo = "# Exemplo: AC-001;Instalação de Equipamento\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
