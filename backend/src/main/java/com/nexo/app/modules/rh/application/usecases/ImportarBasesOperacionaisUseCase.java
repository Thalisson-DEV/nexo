package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.domain.UnidadeFederativa;
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
 * Realiza a importação em lote de bases operacionais via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarBasesOperacionaisUseCase {

    private final BaseOperacionalRepository repository;
    private final CentroFaturamentoRepository centroFaturamentoRepository;
    private final ImportarBasesOperacionaisAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoBaseOperacionalResponse executarValidacao(MultipartFile arquivo) {
        List<BaseOperacionalImportacaoDTO> registros = new ArrayList<>();
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

                BaseOperacionalImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoBaseOperacionalResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoBaseOperacionalResponse confirmarImportacao(ConfirmarImportacaoBaseRequest request) {
        List<BaseOperacionalImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (BaseOperacionalImportacaoDTO dto : request.bases()) {
            BaseOperacionalImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoBaseOperacionalResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<BaseOperacionalImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoBaseOperacionalResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private BaseOperacionalImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nome = colunas.length > 0 ? colunas[0].trim() : "";
        String nomeCentroFaturamento = colunas.length > 1 ? colunas[1].trim() : "";
        String uf = colunas.length > 2 ? colunas[2].trim() : "";
        String endereco = colunas.length > 3 ? colunas[3].trim() : "";

        return new BaseOperacionalImportacaoDTO(
                indice, nome, nomeCentroFaturamento, uf, endereco,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private BaseOperacionalImportacaoDTO validarRegistro(BaseOperacionalImportacaoDTO dto) {
        try {
            validarCamposObrigatorios(dto);
            validarInputs(dto);
            validarDuplicidade(dto.nome());
            validarReferencias(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(BaseOperacionalImportacaoDTO dto) {
        if (dto.nome().length() > 100 || dto.nome().length() < 3) {
            throw new IllegalArgumentException("Nome da base deve ter entre 3 e 100 caracteres");
        }
        if (!UnidadeFederativa.isValida(dto.uf())) {
            throw new IllegalArgumentException("Informe uma UF brasileira válida");
        }
        if (dto.endereco().length() > 300) {
            throw new IllegalArgumentException("Endereço da base deve ter no máximo 300 caracteres");
        }
    }

    private void validarCamposObrigatorios(BaseOperacionalImportacaoDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank())
            throw new IllegalArgumentException("Nome da base é obrigatório");
        if (dto.nomeCentroFaturamento() == null || dto.nomeCentroFaturamento().isBlank())
            throw new IllegalArgumentException("Nome do centro de faturamento é obrigatório");
        if (dto.uf() == null || dto.uf().isBlank())
            throw new IllegalArgumentException("UF da base é obrigatória");
        if (dto.endereco() == null || dto.endereco().isBlank())
            throw new IllegalArgumentException("Endereço da base é obrigatório");
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Base operacional já cadastrada no sistema");
        }
    }

    private void validarReferencias(BaseOperacionalImportacaoDTO dto) {
        if (!centroFaturamentoRepository.existsByNomeIgnoreCase(dto.nomeCentroFaturamento())) {
            throw new IllegalArgumentException("Centro de Faturamento não encontrado: " + dto.nomeCentroFaturamento());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome da Base Operacional;Nome do Centro de Faturamento;UF;Endereço\n";
        String exemplo = "# Exemplo: Base Sul;Centro Principal;BA;Rua Exemplo, 100\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
