package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.ConfirmarImportacaoSupervisorRequest;
import com.nexo.app.modules.rh.application.dto.ImportacaoSupervisorResponse;
import com.nexo.app.modules.rh.application.dto.SupervisorImportacaoDTO;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.SupervisorRepository;
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
public class ImportarSupervisoresUseCase {

    private final SupervisorRepository repository;
    private final CoordenadorRepository coordenadorRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final ImportarSupervisoresAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;
    private static final DateTimeFormatter FORMATTER_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATTER_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ImportacaoSupervisorResponse executarValidacao(MultipartFile arquivo) {
        List<SupervisorImportacaoDTO> registros = new ArrayList<>();
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

                SupervisorImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
                dto = validarRegistro(dto);

                if (dto.status() == ImportacaoStatus.COM_ERRO) {
                    falhas++;
                } else {
                    sucessos++;
                }
                registros.add(dto);
            }
        } catch (Exception e) {
            log.error("Falha crítica ao ler arquivo de importação de supervisores", e);
            throw new RuntimeException("Falha ao ler o arquivo CSV: " + e.getMessage());
        }

        return new ImportacaoSupervisorResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoSupervisorResponse confirmarImportacao(ConfirmarImportacaoSupervisorRequest request) {
        List<SupervisorImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;

        for (SupervisorImportacaoDTO dto : request.supervisores()) {
            SupervisorImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoSupervisorResponse(
                    registrosProcessados,
                    registrosProcessados.size(),
                    registrosProcessados.size() - falhas,
                    falhas
            );
        }

        List<SupervisorImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoSupervisorResponse(
                registrosEmProcessamento,
                registrosEmProcessamento.size(),
                registrosEmProcessamento.size(),
                0
        );
    }

    private SupervisorImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);

        String cpfColaborador = colunas.length > 0 ? colunas[0].trim() : "";
        String nomeColaborador = colunas.length > 1 ? colunas[1].trim() : "";
        String cpfCoordenador = colunas.length > 2 ? colunas[2].trim() : "";
        String nomeCoordenador = colunas.length > 3 ? colunas[3].trim() : "";
        String dataInicio = colunas.length > 4 ? colunas[4].trim() : "";
        String dataFim = colunas.length > 5 ? colunas[5].trim() : "";

        return new SupervisorImportacaoDTO(
                indice, cpfColaborador, nomeColaborador, cpfCoordenador, nomeCoordenador,
                dataInicio, dataFim, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private SupervisorImportacaoDTO validarRegistro(SupervisorImportacaoDTO dto) {
        try {
            validarObrigatorios(dto);
            Colaborador supervisorColab = validarColaborador(dto.cpfColaborador(), dto.nomeColaborador(), "Supervisor");
            validarDuplicidadeSupervisor(supervisorColab.getId());

            Colaborador coordColab = validarColaborador(dto.cpfCoordenador(), dto.nomeCoordenador(), "Coordenador");
            Coordenador coordenador = validarCoordenadorAtivo(coordColab.getId());

            if (supervisorColab.getId().equals(coordenador.getColaborador().getId())) {
                throw new IllegalArgumentException("Supervisor não pode responder a si mesmo");
            }

            validarDatas(dto.dataInicio(), dto.dataFim());

            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarObrigatorios(SupervisorImportacaoDTO dto) {
        if ((dto.cpfColaborador() == null || dto.cpfColaborador().isBlank()) &&
                (dto.nomeColaborador() == null || dto.nomeColaborador().isBlank())) {
            throw new IllegalArgumentException("Informe o CPF ou o Nome Completo do supervisor");
        }
        if ((dto.cpfCoordenador() == null || dto.cpfCoordenador().isBlank()) &&
                (dto.nomeCoordenador() == null || dto.nomeCoordenador().isBlank())) {
            throw new IllegalArgumentException("Informe o CPF ou o Nome Completo do coordenador");
        }
        if (dto.dataInicio() == null || dto.dataInicio().isBlank()) {
            throw new IllegalArgumentException("Data de início é obrigatória");
        }
    }

    private Colaborador validarColaborador(String cpf, String nome, String papel) {
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
            throw new IllegalArgumentException("Colaborador (" + papel + ") não encontrado no sistema");
        }

        Colaborador colaborador = colaboradorOpt.get();
        if (!colaborador.isAtivo()) {
            throw new IllegalArgumentException("Colaborador (" + papel + ") está inativo");
        }

        return colaborador;
    }

    private Coordenador validarCoordenadorAtivo(java.util.UUID idColaboradorCoordenador) {
        return coordenadorRepository.listarAtivos().stream()
                .filter(c -> c.getColaborador().getId().equals(idColaboradorCoordenador))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("O colaborador indicado não possui registro ativo de Coordenador"));
    }

    private void validarDuplicidadeSupervisor(java.util.UUID idColaborador) {
        if (repository.existsByColaboradorIdAndAtivoTrue(idColaborador)) {
            throw new IllegalArgumentException("Este colaborador já possui registro ativo como supervisor");
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
        String cabecalho = "CPF do Supervisor;Nome do Supervisor;CPF do Coordenador;Nome do Coordenador;Data de Início;Data de Fim\n";
        String exemplo = "# Exemplo: 111.222.333-44;MARIA DE SOUZA;555.666.777-88;JOSÉ DA SILVA;01/01/2026;\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
