package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ImportacaoTipoEstoqueResponse;
import com.nexo.app.modules.estoque.application.dto.TipoEstoqueImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoTipoEstoqueRequest;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
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
public class ImportarTipoEstoqueUseCase {

    private final TipoEstoqueRepository repository;
    private final ImportarTipoEstoqueAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoTipoEstoqueResponse executarValidacao(MultipartFile arquivo) {
        List<TipoEstoqueImportacaoDTO> registros = new ArrayList<>();
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

                TipoEstoqueImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoTipoEstoqueResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoTipoEstoqueResponse confirmarImportacao(ConfirmarImportacaoTipoEstoqueRequest request) {
        List<TipoEstoqueImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (TipoEstoqueImportacaoDTO dto : request.tipos()) {
            TipoEstoqueImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoTipoEstoqueResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<TipoEstoqueImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoTipoEstoqueResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private TipoEstoqueImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        String nome = colunas.length > 0 ? colunas[0].trim() : "";

        return new TipoEstoqueImportacaoDTO(
                indice, nome,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private TipoEstoqueImportacaoDTO validarRegistro(TipoEstoqueImportacaoDTO dto) {
        try {
            if (dto.nome().isBlank()) throw new IllegalArgumentException("Nome do tipo de estoque é obrigatório");
            if (dto.nome().length() > 20) throw new IllegalArgumentException("Nome excede 20 caracteres");
            if (repository.existsByNomeIgnoreCase(dto.nome())) {
                throw new IllegalArgumentException("Tipo de estoque já cadastrado");
            }
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome do Tipo de Estoque\n";
        String exemplo = "# Exemplo: PRINCIPAL\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
