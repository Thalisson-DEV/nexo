package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.AcaoValorImportacaoDTO;
import com.nexo.app.modules.contrato.application.dto.ConfirmarImportacaoAcaoValorRequest;
import com.nexo.app.modules.contrato.application.dto.ImportacaoAcaoValorResponse;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoValorRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.infrastructure.validators.ValidadorIntervaloData;
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
import java.util.Optional;

/**
 * Realiza a importação em lote de valores de ações via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarAcaoValoresUseCase {

    private final AcaoValorRepository repository;
    private final AcaoRepository acaoRepository;
    private final ContratoRepository contratoRepository;
    private final ImportarAcaoValoresAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ImportacaoAcaoValorResponse executarValidacao(MultipartFile arquivo) {
        List<AcaoValorImportacaoDTO> registros = new ArrayList<>();
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

                AcaoValorImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoAcaoValorResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoAcaoValorResponse confirmarImportacao(ConfirmarImportacaoAcaoValorRequest request) {
        List<AcaoValorImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (AcaoValorImportacaoDTO dto : request.registros()) {
            AcaoValorImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoAcaoValorResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<AcaoValorImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoAcaoValorResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private AcaoValorImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String codigoAcao = colunas.length > 0 ? colunas[0].trim() : "";
        String numeroContrato = colunas.length > 1 ? colunas[1].trim() : "";
        String valorStr = colunas.length > 2 ? colunas[2].trim() : "";
        String dataVigenciaStr = colunas.length > 3 ? colunas[3].trim() : "";

        BigDecimal valorBase = null;
        LocalDate dataVigencia = null;
        String erroParsing = null;

        try {
            if (!valorStr.isBlank()) {
                valorBase = new BigDecimal(valorStr.replace(",", "."));
            }
            if (!dataVigenciaStr.isBlank()) {
                dataVigencia = LocalDate.parse(dataVigenciaStr, DATE_FORMATTER);
            }
        } catch (Exception e) {
            erroParsing = "Erro no formato de valor ou data (esperado: valor numérico e data dd/MM/yyyy)";
        }

        return new AcaoValorImportacaoDTO(
                indice, codigoAcao, numeroContrato, valorBase, dataVigencia,
                erroParsing != null ? ImportacaoStatus.COM_ERRO : ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, erroParsing
        );
    }

    private AcaoValorImportacaoDTO validarRegistro(AcaoValorImportacaoDTO dto) {
        if (dto.status() == ImportacaoStatus.COM_ERRO) return dto;

        try {
            validarCamposObrigatorios(dto);
            validarValorPositivo(dto.valorBase());
            validarIntervaloDataVigencia(dto.dataVigencia());
            validarReferenciasEDuplicidade(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarCamposObrigatorios(AcaoValorImportacaoDTO dto) {
        if (dto.codigoAcao().isBlank()) throw new IllegalArgumentException("Código da ação é obrigatório");
        if (dto.numeroContrato().isBlank()) throw new IllegalArgumentException("Número do contrato é obrigatório");
        if (dto.valorBase() == null) throw new IllegalArgumentException("Valor base é obrigatório");
        if (dto.dataVigencia() == null) throw new IllegalArgumentException("Data de vigência é obrigatória");
    }

    private void validarValorPositivo(BigDecimal valor) {
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O valor base não pode ser negativo");
        }
    }

    private void validarIntervaloDataVigencia(LocalDate dataVigencia) {
        // Mesma regra do @ValidarIntervaloData aplicado na entidade AcaoValor, antecipada aqui
        // para reportar o erro por linha no fluxo de importação em vez de falhar no commit.
        if (!new ValidadorIntervaloData().isValid(dataVigencia, null)) {
            throw new IllegalArgumentException(
                "Data de vigência fora do intervalo permitido (mínimo 01/01/2000, máximo hoje + 5 anos)");
        }
    }

    private void validarReferenciasEDuplicidade(AcaoValorImportacaoDTO dto) {
        Optional<Acao> acao = acaoRepository.findByCodigoIgnoreCase(dto.codigoAcao());
        if (acao.isEmpty()) throw new IllegalArgumentException("Ação não encontrada: " + dto.codigoAcao());

        Optional<Contrato> contrato = contratoRepository.findByNumeroContratoIgnoreCase(dto.numeroContrato());
        if (contrato.isEmpty()) throw new IllegalArgumentException("Contrato não encontrado: " + dto.numeroContrato());

        if (repository.existsByAcaoIdAndContratoIdAndDataVigencia(acao.get().getId(), contrato.get().getId(), dto.dataVigencia())) {
            throw new IllegalArgumentException("Já existe um valor definido para esta ação, contrato e data");
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Código da Ação;Número do Contrato;Valor Base;Data de Vigência (dd/MM/yyyy)\n";
        String exemplo = "# Exemplo: AC-001;CT-2026-001;150.00;01/01/2026\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
