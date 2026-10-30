package com.nexo.app.modules.rh.application.usecases;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nexo.app.modules.rh.application.dto.ColaboradorImportacaoDTO;
import com.nexo.app.modules.rh.application.dto.ConfirmarImportacaoRequest;
import com.nexo.app.modules.rh.application.dto.ImportacaoColaboradorResponse;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.infrastructure.security.HashUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Realiza a importação em lote de colaboradores via CSV com fluxo de validação e confirmação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarColaboradoresUseCase {

    private final ColaboradorRepository repository;
    private final ProcessoNegocioRepository processoRepository;
    private final BaseOperacionalRepository baseRepository;
    private final FuncaoRepository funcaoRepository;
    private final ImportarColaboradoresAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LIMITE_LINHAS = 2000;

    public ImportacaoColaboradorResponse executarValidacao(MultipartFile arquivo) {
        List<ColaboradorImportacaoDTO> registros = new ArrayList<>();
        Set<String> cpfsDoLote = new HashSet<>();
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

                ColaboradorImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
                dto = validarRegistro(dto);
                if (dto.status() != ImportacaoStatus.COM_ERRO
                        && !cpfsDoLote.add(normalizarCpf(dto.cpf()))) {
                    dto = dto.withStatus(
                            ImportacaoStatus.COM_ERRO,
                            "CPF duplicado no arquivo de importação"
                    );
                }
                
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

        return new ImportacaoColaboradorResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoColaboradorResponse confirmarImportacao(ConfirmarImportacaoRequest request) {
        List<ColaboradorImportacaoDTO> registrosProcessados = new ArrayList<>();
        Set<String> cpfsDoLote = new HashSet<>();
        int falhas = 0;
        
        // Re-validar todos os registros
        for (ColaboradorImportacaoDTO dto : request.colaboradores()) {
            ColaboradorImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() != ImportacaoStatus.COM_ERRO) {
                String cpfNormalizado = normalizarCpf(validado.cpf());
                if (!cpfsDoLote.add(cpfNormalizado)) {
                    validado = validado.withStatus(
                            ImportacaoStatus.COM_ERRO,
                            "CPF duplicado no arquivo de importação"
                    );
                }
            }
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        // Apenas os registros válidos seguem para persistência. Os registros com erro
        // permanecem na resposta para que o usuário possa corrigi-los em outro lote.
        List<ColaboradorImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .filter(r -> r.status() != ImportacaoStatus.COM_ERRO)
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        if (!registrosEmProcessamento.isEmpty()) {
            asyncService.processarPersistencia(registrosEmProcessamento);
        }

        List<ColaboradorImportacaoDTO> resposta = registrosProcessados.stream()
                .map(r -> r.status() == ImportacaoStatus.COM_ERRO
                        ? r
                        : r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        return new ImportacaoColaboradorResponse(
            resposta,
            resposta.size(),
            registrosEmProcessamento.size(),
            falhas
        );
    }

    private ColaboradorImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String primeiroNome = colunas.length > 0 ? colunas[0].trim() : "";
        String ultimoNome = colunas.length > 1 ? colunas[1].trim() : "";
        String nomeCompleto = colunas.length > 2 ? colunas[2].trim() : "";
        String cpf = colunas.length > 3 ? colunas[3].trim() : "";
        String nomeProcesso = colunas.length > 4 ? colunas[4].trim() : "";
        String nomeBase = colunas.length > 5 ? colunas[5].trim() : "";
        String nomeFuncao = colunas.length > 6 ? colunas[6].trim() : "";
        String dataContratacaoStr = colunas.length > 7 ? colunas[7].trim() : "";

        return new ColaboradorImportacaoDTO(
                indice, primeiroNome, ultimoNome, nomeCompleto, cpf,
                nomeProcesso, nomeBase, nomeFuncao, dataContratacaoStr,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private ColaboradorImportacaoDTO validarRegistro(ColaboradorImportacaoDTO dto) {
        try {
            validarInputs(dto);
            validarCamposObrigatorios(dto);
            validarCpf(dto.cpf());
            validarReferencias(dto);
            validarData(dto.dataContratacao());
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarInputs(ColaboradorImportacaoDTO dto) {
        if (dto.primeiroNome().length() > 255 || dto.primeiroNome().length() < 3) {
            throw new IllegalArgumentException("Primeiro nome deve ter entre 3 e 255 caracteres");
        }

        if (dto.ultimoNome().length() > 255 || dto.ultimoNome().length() < 3) {
            throw new IllegalArgumentException("Primeiro nome deve ter entre 3 e 255 caracteres");
        }

        if (dto.nomeCompleto().length() > 512 || dto.nomeCompleto().length() < 3) {
            throw new IllegalArgumentException("Primeiro nome deve ter entre 3 e 255 caracteres");
        }

        if (dto.cpf().length() > 255 || dto.cpf().length() < 3) {
            throw new IllegalArgumentException("Primeiro nome deve ter entre 3 e 255 caracteres");
        }
    }

    private void validarCamposObrigatorios(ColaboradorImportacaoDTO dto) {
        if (dto.primeiroNome().isBlank()) throw new IllegalArgumentException("Primeiro nome é obrigatório");
        if (dto.ultimoNome().isBlank()) throw new IllegalArgumentException("Último nome é obrigatório");
        if (dto.nomeCompleto().isBlank()) throw new IllegalArgumentException("Nome completo é obrigatório");
        if (dto.cpf().isBlank()) throw new IllegalArgumentException("CPF é obrigatório");
        if (dto.nomeProcesso().isBlank()) throw new IllegalArgumentException("Nome do processo é obrigatório");
        if (dto.nomeBase().isBlank()) throw new IllegalArgumentException("Nome da base é obrigatório");
        if (dto.nomeFuncao().isBlank()) throw new IllegalArgumentException("Nome da função é obrigatório");
    }

    private void validarCpf(String cpf) {
        String cpfLimpo = normalizarCpf(cpf);
        if (cpfLimpo.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos");
        }
        
        String cpfHash = HashUtils.gerarBlindIndex(cpfLimpo);
        if (repository.existsByCpfHash(cpfHash)) {
            throw new IllegalArgumentException("CPF já cadastrado no sistema");
        }
    }

    private String normalizarCpf(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("[^0-9]", "");
    }

    private void validarReferencias(ColaboradorImportacaoDTO dto) {
        if (!processoRepository.existsByNomeIgnoreCase(dto.nomeProcesso())) {
            throw new IllegalArgumentException("Processo não encontrado: " + dto.nomeProcesso());
        }
        if (!baseRepository.existsByNomeIgnoreCase(dto.nomeBase())) {
            throw new IllegalArgumentException("Base Operacional não encontrada: " + dto.nomeBase());
        }
        if (!funcaoRepository.existsByNomeIgnoreCase(dto.nomeFuncao())) {
            throw new IllegalArgumentException("Função não encontrada: " + dto.nomeFuncao());
        }
    }

    private void validarData(String dataStr) {
        if (dataStr != null && !dataStr.isBlank()) {
            try {
                LocalDate.parse(dataStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Data de contratação inválida (esperado dd/MM/yyyy)");
            }
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Primeiro Nome;Último Nome;Nome Completo;CPF;Nome do Processo;Nome da Base;Nome da Função;Data de Contratação (dd/MM/yyyy)\n";
        String exemplo = "# Exemplo: João;Silva;João da Silva;123.456.789-00;COMERCIAL;Base Sul;Eletricista;15/01/2026\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
