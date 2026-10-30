package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.CoordenadorImportacaoDTO;
import com.nexo.app.modules.rh.application.dto.ConfirmarImportacaoCoordenadorRequest;
import com.nexo.app.modules.rh.application.dto.ImportacaoCoordenadorResponse;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarCoordenadoresUseCase {

    private final CoordenadorRepository repository;
    private final ColaboradorRepository colaboradorRepository;
    private final ImportarCoordenadoresAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;
    private static final DateTimeFormatter FORMATTER_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATTER_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ImportacaoCoordenadorResponse executarValidacao(MultipartFile arquivo) {
        List<CoordenadorImportacaoDTO> registros = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            int numeroLinha = 0;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;

                // Ignorar cabeçalho e comentários
                if (numeroLinha == 1) continue;
                if (linha.isBlank() || linha.trim().startsWith("#")) continue;

                if (numeroLinha > LIMITE_LINHAS + 1) {
                    log.warn("Arquivo excede o limite de {} linhas", LIMITE_LINHAS);
                    break;
                }

                CoordenadorImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
                dto = validarRegistro(dto);

                if (dto.status() == ImportacaoStatus.COM_ERRO) {
                    falhas++;
                } else {
                    sucessos++;
                }
                registros.add(dto);
            }
        } catch (Exception e) {
            log.error("Falha crítica ao ler arquivo de importação de coordenadores", e);
            throw new RuntimeException("Falha ao ler o arquivo CSV: " + e.getMessage());
        }

        return new ImportacaoCoordenadorResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoCoordenadorResponse confirmarImportacao(ConfirmarImportacaoCoordenadorRequest request) {
        List<CoordenadorImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;

        for (CoordenadorImportacaoDTO dto : request.coordenadores()) {
            CoordenadorImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoCoordenadorResponse(
                    registrosProcessados,
                    registrosProcessados.size(),
                    registrosProcessados.size() - falhas,
                    falhas
            );
        }

        List<CoordenadorImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoCoordenadorResponse(
                registrosEmProcessamento,
                registrosEmProcessamento.size(),
                registrosEmProcessamento.size(),
                0
        );
    }

    private CoordenadorImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);

        String cpf = colunas.length > 0 ? colunas[0].trim() : "";
        String nome = colunas.length > 1 ? colunas[1].trim() : "";
        String dataInicio = colunas.length > 2 ? colunas[2].trim() : "";
        String dataFim = colunas.length > 3 ? colunas[3].trim() : "";

        return new CoordenadorImportacaoDTO(
                indice, cpf, nome, dataInicio, dataFim,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private CoordenadorImportacaoDTO validarRegistro(CoordenadorImportacaoDTO dto) {
        try {
            validarObrigatorios(dto);
            Colaborador colaborador = validarColaborador(dto.cpfColaborador(), dto.nomeColaborador());
            validarDuplicidade(colaborador.getId());
            validarDatas(dto.dataInicio(), dto.dataFim());

            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarObrigatorios(CoordenadorImportacaoDTO dto) {
        if ((dto.cpfColaborador() == null || dto.cpfColaborador().isBlank()) &&
                (dto.nomeColaborador() == null || dto.nomeColaborador().isBlank())) {
            throw new IllegalArgumentException("Informe o CPF ou o Nome Completo do colaborador");
        }
        if (dto.dataInicio() == null || dto.dataInicio().isBlank()) {
            throw new IllegalArgumentException("Data de início é obrigatória");
        }
    }

    private Colaborador validarColaborador(String cpf, String nome) {
        Optional<Colaborador> colaboradorOpt = Optional.empty();

        if (cpf != null && !cpf.isBlank()) {
            String cpfFormatado = cpf.replace("-", "").replace(".", "").trim();
            String hash = HashUtils.gerarBlindIndex(cpfFormatado);
            colaboradorOpt = colaboradorRepository.findByCpfHash(hash);
        }

        if (colaboradorOpt.isEmpty() && nome != null && !nome.isBlank()) {
            String hash = HashUtils.gerarBlindIndex(nome.trim().toUpperCase());
            colaboradorOpt = colaboradorRepository.findByNomeCompletoHash(hash);
        }

        if (colaboradorOpt.isEmpty()) {
            throw new IllegalArgumentException("Colaborador não encontrado no sistema");
        }

        Colaborador colaborador = colaboradorOpt.get();
        if (!colaborador.isAtivo()) {
            throw new IllegalArgumentException("Colaborador informado está inativo");
        }

        return colaborador;
    }

    private void validarDuplicidade(java.util.UUID idColaborador) {
        if (repository.existsByColaboradorIdAndAtivoTrue(idColaborador)) {
            throw new IllegalArgumentException("Este colaborador já é um coordenador ativo");
        }
    }

    private void validarDatas(String dataInicioStr, String dataFimStr) {
        LocalDate inicio = parseData(dataInicioStr);
        if (inicio == null) {
            throw new IllegalArgumentException("Data de início inválida. Use dd/MM/yyyy ou yyyy-MM-dd");
        }

        if (dataFimStr != null && !dataFimStr.isBlank()) {
            LocalDate fim = parseData(dataFimStr);
            if (fim == null) {
                throw new IllegalArgumentException("Data final inválida. Use dd/MM/yyyy ou yyyy-MM-dd");
            }
            if (fim.isBefore(inicio)) {
                throw new IllegalArgumentException("A data final não pode ser anterior à data inicial");
            }
        }
    }

    private LocalDate parseData(String dataStr) {
        if (dataStr == null || dataStr.isBlank()) {
            return null;
        }
        try {
            String limpo = dataStr.trim();
            if (limpo.contains("/")) {
                return LocalDate.parse(limpo, FORMATTER_BR);
            }
            return LocalDate.parse(limpo, FORMATTER_ISO);
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "CPF do Colaborador;Nome do Colaborador;Data de Início;Data de Fim\n";
        String exemplo = "# Exemplo: 123.456.789-00;JOSÉ DA SILVA;01/01/2026;\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
