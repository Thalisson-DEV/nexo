package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.AvaliacaoMaterialImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoAvaliacaoMaterialRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoAvaliacaoMaterialResponse;
import com.nexo.app.modules.estoque.infrastructure.persistence.AvaliacaoMaterialRepository;
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
public class ImportarAvaliacaoMaterialUseCase {

    private final AvaliacaoMaterialRepository repository;
    private final ImportarAvaliacaoMaterialAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoAvaliacaoMaterialResponse executarValidacao(MultipartFile arquivo) {
        List<AvaliacaoMaterialImportacaoDTO> registros = new ArrayList<>();
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

                AvaliacaoMaterialImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoAvaliacaoMaterialResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoAvaliacaoMaterialResponse confirmarImportacao(ConfirmarImportacaoAvaliacaoMaterialRequest request) {
        List<AvaliacaoMaterialImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (AvaliacaoMaterialImportacaoDTO dto : request.avaliacoes()) {
            AvaliacaoMaterialImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoAvaliacaoMaterialResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<AvaliacaoMaterialImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoAvaliacaoMaterialResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private AvaliacaoMaterialImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        String nome = colunas.length > 0 ? colunas[0].trim() : "";

        return new AvaliacaoMaterialImportacaoDTO(
                indice, nome,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private AvaliacaoMaterialImportacaoDTO validarRegistro(AvaliacaoMaterialImportacaoDTO dto) {
        try {
            if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome da avaliação é obrigatório");
            if (dto.nome().length() > 50) throw new IllegalArgumentException("Nome excede 50 caracteres");
            if (repository.existsByNomeIgnoreCase(dto.nome())) {
                throw new IllegalArgumentException("Avaliação técnica já cadastrada");
            }
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome da Avaliação Técnica\n";
        String exemplo = "# Exemplo: Aprovado para Uso Geral\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
