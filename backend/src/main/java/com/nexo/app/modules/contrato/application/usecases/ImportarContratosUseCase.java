package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.ConfirmarImportacaoContratoRequest;
import com.nexo.app.modules.contrato.application.dto.ContratoImportacaoDTO;
import com.nexo.app.modules.contrato.application.dto.ImportacaoContratoResponse;
import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Realiza a importação em lote de contratos via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarContratosUseCase {

    private final ContratoRepository repository;
    private final ImportarContratosAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ImportacaoContratoResponse executarValidacao(MultipartFile arquivo) {
        List<ContratoImportacaoDTO> registros = new ArrayList<>();
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

                ContratoImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoContratoResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoContratoResponse confirmarImportacao(ConfirmarImportacaoContratoRequest request) {
        List<ContratoImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;

        for (ContratoImportacaoDTO dto : request.contratos()) {
            ContratoImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoContratoResponse(
                registrosProcessados,
                registrosProcessados.size(),
                registrosProcessados.size() - falhas,
                falhas
            );
        }

        List<ContratoImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoContratoResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private ContratoImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);

        String numeroContrato = colunas.length > 0 ? colunas[0].trim() : "";
        String cliente = colunas.length > 1 ? colunas[1].trim() : "";
        String dataVigenciaStr = colunas.length > 2 ? colunas[2].trim() : "";
        String dataEncerramentoStr = colunas.length > 3 ? colunas[3].trim() : "";
        String coeficienteStr = colunas.length > 4 ? colunas[4].trim() : "";

        LocalDate dataVigencia = null;
        LocalDate dataEncerramento = null;
        BigDecimal coeficiente = null;
        String erroParsing = null;

        try {
            if (!dataVigenciaStr.isBlank()) dataVigencia = LocalDate.parse(dataVigenciaStr, DATE_FORMATTER);
            if (!dataEncerramentoStr.isBlank()) dataEncerramento = LocalDate.parse(dataEncerramentoStr, DATE_FORMATTER);
        } catch (Exception e) {
            erroParsing = "Formato de data inválido. Use dd/MM/yyyy";
        }

        if (erroParsing == null) {
            try {
                if (!coeficienteStr.isBlank()) {
                    coeficiente = new BigDecimal(coeficienteStr.replace(",", "."));
                }
            } catch (NumberFormatException e) {
                erroParsing = "Coeficiente de faturamento inválido: " + coeficienteStr;
            }
        }

        return new ContratoImportacaoDTO(
                indice, numeroContrato, cliente, dataVigencia, dataEncerramento, coeficiente,
                erroParsing != null ? ImportacaoStatus.COM_ERRO : ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, erroParsing
        );
    }

    private ContratoImportacaoDTO validarRegistro(ContratoImportacaoDTO dto) {
        if (dto.status() == ImportacaoStatus.COM_ERRO) return dto;

        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarDatas(dto);
            validarCoeficiente(dto);
            validarDuplicidade(dto.numeroContrato());

            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(ContratoImportacaoDTO dto) {
        if (dto.numeroContrato().length() > 100 || dto.numeroContrato().length() < 3) {
            throw new IllegalArgumentException("Número do contrato deve ter entre 3 e 100 caracteres");
        }

        if (dto.cliente().length() > 100 || dto.cliente().length() < 3) {
            throw new IllegalArgumentException("Nome do cliente deve ter entre 3 e 100 caracteres");
        }
    }

    private void validarCamposObrigatorios(ContratoImportacaoDTO dto) {
        if (dto.numeroContrato().isBlank()) throw new IllegalArgumentException("Número do contrato é obrigatório");
        if (dto.cliente().isBlank()) throw new IllegalArgumentException("Cliente é obrigatório");
        if (dto.dataVigencia() == null) throw new IllegalArgumentException("Data de vigência é obrigatória");
        if (dto.dataEncerramento() == null) throw new IllegalArgumentException("Data de encerramento é obrigatória");
        if (dto.coeficienteFaturamento() == null) throw new IllegalArgumentException("Coeficiente de faturamento é obrigatório");
    }

    private void validarDatas(ContratoImportacaoDTO dto) {
        if (dto.dataVigencia().isAfter(dto.dataEncerramento())) {
            throw new IllegalArgumentException("A data de vigência não pode ser posterior ao encerramento");
        }
    }

    private void validarCoeficiente(ContratoImportacaoDTO dto) {
        BigDecimal coef = dto.coeficienteFaturamento();
        if (coef.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O coeficiente de faturamento deve ser maior que zero");
        }
        if (coef.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new IllegalArgumentException("O coeficiente de faturamento deve ser menor ou igual a 1000");
        }
    }

    private void validarDuplicidade(String numeroContrato) {
        if (repository.existsByNumeroContratoIgnoreCase(numeroContrato)) {
            throw new IllegalArgumentException("Número de contrato já cadastrado no sistema");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Número do Contrato;Cliente;Data de Vigência (dd/MM/yyyy);Data de Encerramento (dd/MM/yyyy);Coeficiente de Faturamento\n";
        String exemplo = "# Exemplo: CT-2026-001;Empresa Alpha S/A;01/01/2026;31/12/2026;1.15\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
