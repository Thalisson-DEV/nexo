package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.ConfirmarImportacaoMunicipioRequest;
import com.nexo.app.modules.rh.application.dto.ImportacaoMunicipioResponse;
import com.nexo.app.modules.rh.application.dto.MunicipioImportacaoDTO;
import com.nexo.app.modules.rh.infrastructure.persistence.MunicipioRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
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
 * Realiza a importação em lote de municípios via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarMunicipiosUseCase {

    private final MunicipioRepository repository;
    private final BaseOperacionalApi baseOperacionalApi;
    private final ImportarMunicipiosAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoMunicipioResponse executarValidacao(MultipartFile arquivo) {
        List<MunicipioImportacaoDTO> registros = new ArrayList<>();
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

                MunicipioImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoMunicipioResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoMunicipioResponse confirmarImportacao(ConfirmarImportacaoMunicipioRequest request) {
        List<MunicipioImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (MunicipioImportacaoDTO dto : request.municipios()) {
            MunicipioImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoMunicipioResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<MunicipioImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoMunicipioResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private MunicipioImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nome = colunas.length > 0 ? colunas[0].trim() : "";
        String nomeBaseOperacional = colunas.length > 1 ? colunas[1].trim() : "";

        return new MunicipioImportacaoDTO(
                indice, nome, nomeBaseOperacional,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private MunicipioImportacaoDTO validarRegistro(MunicipioImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.nome());
            validarReferencias(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(MunicipioImportacaoDTO dto) {
        if (dto.nome().length() > 100 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome do município deve ter entre 3 e 100 caracteres");
        }
    }

    private void validarCamposObrigatorios(MunicipioImportacaoDTO dto) {
        if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome do município é obrigatório");
        if (dto.nomeBaseOperacional().isBlank()) throw new IllegalArgumentException("Nome da base operacional é obrigatório");
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Município já cadastrado no sistema");
        }
    }

    private void validarReferencias(MunicipioImportacaoDTO dto) {
        if (!baseOperacionalApi.existePorNome(dto.nomeBaseOperacional())) {
            throw new IllegalArgumentException("Base Operacional não encontrada: " + dto.nomeBaseOperacional());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome do Município;Nome da Base Operacional\n";
        String exemplo = "# Exemplo: Florianópolis;Base Sul\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
