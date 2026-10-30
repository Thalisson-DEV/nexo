package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.CentroInvestimentoImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoCentroInvestimentoRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoCentroInvestimentoResponse;
import com.nexo.app.modules.estoque.infrastructure.persistence.CentroInvestimentoRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarCentroInvestimentoUseCase {

    private final CentroInvestimentoRepository repository;
    private final ImportarCentroInvestimentoAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoCentroInvestimentoResponse executarValidacao(MultipartFile arquivo) {
        List<CentroInvestimentoImportacaoDTO> registros = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            int numeroLinha = 0;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                if (numeroLinha == 1) continue;
                if (linha.isBlank() || linha.trim().startsWith("#")) continue;

                if (numeroLinha > LIMITE_LINHAS + 1) {
                    log.warn("Arquivo excede o limite de {} linhas", LIMITE_LINHAS);
                    break;
                }

                CentroInvestimentoImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoCentroInvestimentoResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoCentroInvestimentoResponse confirmarImportacao(ConfirmarImportacaoCentroInvestimentoRequest request) {
        List<CentroInvestimentoImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (CentroInvestimentoImportacaoDTO dto : request.centros()) {
            CentroInvestimentoImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoCentroInvestimentoResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<CentroInvestimentoImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoCentroInvestimentoResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private CentroInvestimentoImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        String codigo = colunas.length > 0 ? colunas[0].trim() : "";

        return new CentroInvestimentoImportacaoDTO(
                indice, codigo,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private CentroInvestimentoImportacaoDTO validarRegistro(CentroInvestimentoImportacaoDTO dto) {
        try {
            if (dto.codigo().isBlank()) throw new IllegalArgumentException("Código do centro de investimento é obrigatório");
            if (dto.codigo().length() > 10) throw new IllegalArgumentException("Código excede 10 caracteres");
            if (repository.existsByCodigoIgnoreCase(dto.codigo())) {
                throw new IllegalArgumentException("Centro de investimento já cadastrado");
            }
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Código do Centro de Investimento\n";
        String exemplo = "# Exemplo: CI-100\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
