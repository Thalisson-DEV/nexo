package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.VeiculoRepository;
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
 * Realiza a importação em lote de veículos via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarVeiculosUseCase {

    private final VeiculoRepository repository;
    private final BaseOperacionalRepository baseOperacionalRepository;
    private final ImportarVeiculosAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoVeiculoResponse executarValidacao(MultipartFile arquivo) {
        List<VeiculoImportacaoDTO> registros = new ArrayList<>();
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

                VeiculoImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoVeiculoResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoVeiculoResponse confirmarImportacao(ConfirmarImportacaoVeiculoRequest request) {
        List<VeiculoImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (VeiculoImportacaoDTO dto : request.veiculos()) {
            VeiculoImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoVeiculoResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<VeiculoImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoVeiculoResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private VeiculoImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String placa = colunas.length > 0 ? colunas[0].trim() : "";
        String modelo = colunas.length > 1 ? colunas[1].trim() : "";
        String fabricante = colunas.length > 2 ? colunas[2].trim() : "";
        String nomeBaseOperacional = colunas.length > 3 ? colunas[3].trim() : "";

        return new VeiculoImportacaoDTO(
                indice, placa, modelo, fabricante, nomeBaseOperacional,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private VeiculoImportacaoDTO validarRegistro(VeiculoImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.placa());
            validarReferencias(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(VeiculoImportacaoDTO dto) {
        if (dto.placa().length() > 10 || dto.placa().length() < 3) {
            throw new IllegalArgumentException("Placa deve ter entre 3 e 10 caracteres");
        }

        if (dto.modelo().length() > 100 || dto.modelo().length() < 3) {
            throw new IllegalArgumentException("Modelo deve ter entre 3 e 100 caracteres");
        }

        if (dto.fabricante().length() > 50 || dto.fabricante().length() < 3) {
            throw new IllegalArgumentException("Fabricante deve ter entre 3 e 50 caracteres");
        }
    }

    private void validarCamposObrigatorios(VeiculoImportacaoDTO dto) {
        if (dto.placa().isBlank()) throw new IllegalArgumentException("Placa do veículo é obrigatória");
        if (dto.modelo().isBlank()) throw new IllegalArgumentException("Modelo do veículo é obrigatório");
        if (dto.nomeBaseOperacional().isBlank()) throw new IllegalArgumentException("Base operacional é obrigatória");
    }

    private void validarDuplicidade(String placa) {
        if (repository.existsByPlacaIgnoreCase(placa.toUpperCase().replace("-", "").trim())) {
            throw new IllegalArgumentException("Veículo já cadastrado no sistema com esta placa");
        }
    }

    private void validarReferencias(VeiculoImportacaoDTO dto) {
        if (!baseOperacionalRepository.existsByNomeIgnoreCase(dto.nomeBaseOperacional())) {
            throw new IllegalArgumentException("Base operacional não encontrada: " + dto.nomeBaseOperacional());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Placa;Modelo;Fabricante;Nome da Base Operacional\n";
        String exemplo = "# Exemplo: ABC-1234;Strada;Fiat;Base Sul\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
