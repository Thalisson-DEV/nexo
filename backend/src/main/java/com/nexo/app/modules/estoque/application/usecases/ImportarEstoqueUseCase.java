package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.EstoqueImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoEstoqueRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoEstoqueResponse;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.modules.rh.EquipeApi;
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
public class ImportarEstoqueUseCase {

    private final EstoqueRepository repository;
    private final TipoEstoqueRepository tipoEstoqueRepository;
    private final EquipeApi equipeApi;
    private final ImportarEstoqueAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoEstoqueResponse executarValidacao(MultipartFile arquivo) {
        List<EstoqueImportacaoDTO> registros = new ArrayList<>();
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

                EstoqueImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoEstoqueResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoEstoqueResponse confirmarImportacao(ConfirmarImportacaoEstoqueRequest request) {
        List<EstoqueImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (EstoqueImportacaoDTO dto : request.estoques()) {
            EstoqueImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoEstoqueResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<EstoqueImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoEstoqueResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private EstoqueImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String nomeIdentificador = colunas.length > 0 ? colunas[0].trim() : "";
        String nomeTipoEstoque = colunas.length > 1 ? colunas[1].trim() : "";
        String codigoEquipe = colunas.length > 2 ? colunas[2].trim() : "";

        return new EstoqueImportacaoDTO(
                indice, nomeIdentificador, nomeTipoEstoque, codigoEquipe,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private EstoqueImportacaoDTO validarRegistro(EstoqueImportacaoDTO dto) {
        try {
            validarCamposObrigatorios(dto);
            validarDuplicidade(dto.nomeIdentificador());
            validarReferencias(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarCamposObrigatorios(EstoqueImportacaoDTO dto) {
        if (dto.nomeIdentificador().isBlank()) throw new IllegalArgumentException("Nome identificador do estoque é obrigatório");
        if (dto.nomeTipoEstoque().isBlank()) throw new IllegalArgumentException("Tipo de estoque é obrigatório");
    }

    private void validarDuplicidade(String nome) {
        if (repository.existsByNomeIdentificadorIgnoreCase(nome)) {
            throw new IllegalArgumentException("Estoque já cadastrado");
        }
    }

    private void validarReferencias(EstoqueImportacaoDTO dto) {
        if (!tipoEstoqueRepository.existsByNomeIgnoreCase(dto.nomeTipoEstoque())) {
            throw new IllegalArgumentException("Tipo de estoque não encontrado: " + dto.nomeTipoEstoque());
        }
        
        if (dto.codigoEquipe() != null && !dto.codigoEquipe().isBlank()) {
            if (!equipeApi.existePorCodigo(dto.codigoEquipe())) {
                throw new IllegalArgumentException("Equipe não encontrada: " + dto.codigoEquipe());
            }
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Nome Identificador do Estoque;Tipo de Estoque;Código da Equipe\n";
        String exemplo = "# Exemplo: Almoxarifado Central;PRINCIPAL;EQ-001\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
