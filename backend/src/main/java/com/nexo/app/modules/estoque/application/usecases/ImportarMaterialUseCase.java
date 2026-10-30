package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.MaterialImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoMaterialRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoMaterialResponse;
import com.nexo.app.modules.estoque.infrastructure.persistence.AvaliacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.CentroInvestimentoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
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
public class ImportarMaterialUseCase {

    private final MaterialRepository repository;
    private final AvaliacaoMaterialRepository avaliacaoRepository;
    private final CentroInvestimentoRepository centroRepository;
    private final ProcessoNegocioApi processoApi;
    private final ImportarMaterialAsyncService asyncService;

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 500;

    public ImportacaoMaterialResponse executarValidacao(MultipartFile arquivo) {
        List<MaterialImportacaoDTO> registros = new ArrayList<>();
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

                MaterialImportacaoDTO dto = processarLinhaCSV(linha, numeroLinha - 1);
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

        return new ImportacaoMaterialResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoMaterialResponse confirmarImportacao(ConfirmarImportacaoMaterialRequest request) {
        List<MaterialImportacaoDTO> registrosProcessados = new ArrayList<>();
        int falhas = 0;
        
        for (MaterialImportacaoDTO dto : request.materiais()) {
            MaterialImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            }
            registrosProcessados.add(validado);
        }

        if (falhas > 0) {
            return new ImportacaoMaterialResponse(
                registrosProcessados, 
                registrosProcessados.size(), 
                registrosProcessados.size() - falhas, 
                falhas
            );
        }

        List<MaterialImportacaoDTO> registrosEmProcessamento = registrosProcessados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        asyncService.processarPersistencia(registrosEmProcessamento);

        return new ImportacaoMaterialResponse(
            registrosEmProcessamento,
            registrosEmProcessamento.size(),
            registrosEmProcessamento.size(),
            0
        );
    }

    private MaterialImportacaoDTO processarLinhaCSV(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        
        String codigo = colunas.length > 0 ? colunas[0].trim() : "";
        String descricao = colunas.length > 1 ? colunas[1].trim() : "";
        String unidadeMedida = colunas.length > 2 ? colunas[2].trim() : "";
        String suprMatrFlag = colunas.length > 3 ? colunas[3].trim() : "";
        String nomeAvaliacao = colunas.length > 4 ? colunas[4].trim() : "";
        String codigoCentroInvestimento = colunas.length > 5 ? colunas[5].trim() : "";
        boolean detalhaSerie = colunas.length > 6 && "SIM".equalsIgnoreCase(colunas[6].trim());
        String nomeProcesso = colunas.length > 7 && !colunas[7].isBlank() ? colunas[7].trim() : "COMERCIAL";
        return new MaterialImportacaoDTO(
                indice, codigo, descricao, unidadeMedida, suprMatrFlag,
                nomeProcesso, nomeAvaliacao, codigoCentroInvestimento, detalhaSerie,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private MaterialImportacaoDTO validarRegistro(MaterialImportacaoDTO dto) {
        try {
            validarCamposObrigatorios(dto);
            validarLimites(dto);
            validarDuplicidade(dto.codigo());
            validarReferencias(dto);
            
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarCamposObrigatorios(MaterialImportacaoDTO dto) {
        if (dto.codigo().isBlank()) throw new IllegalArgumentException("Código do material é obrigatório");
        if (dto.descricao().isBlank()) throw new IllegalArgumentException("Descrição do material é obrigatória");
    }

    private void validarLimites(MaterialImportacaoDTO dto) {
        if (dto.unidadeMedida().length() > 10) throw new IllegalArgumentException("Unidade de medida excede 10 caracteres");
        if (dto.suprMatrFlag().length() > 4) throw new IllegalArgumentException("SuprMatrFlag excede 4 caracteres");
    }

    private void validarDuplicidade(String codigo) {
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Material já cadastrado");
        }
    }

    private void validarReferencias(MaterialImportacaoDTO dto) {
        if (dto.nomeAvaliacao() != null && !dto.nomeAvaliacao().isBlank()) {
            if (!avaliacaoRepository.existsByNomeIgnoreCase(dto.nomeAvaliacao())) {
                throw new IllegalArgumentException("Avaliação técnica não encontrada: " + dto.nomeAvaliacao());
            }
        }
        if (dto.codigoCentroInvestimento() != null && !dto.codigoCentroInvestimento().isBlank()) {
            if (!centroRepository.existsByCodigoIgnoreCase(dto.codigoCentroInvestimento())) {
                throw new IllegalArgumentException("Centro de investimento não encontrado: " + dto.codigoCentroInvestimento());
            }
        }
        if (dto.nomeProcesso() != null && !dto.nomeProcesso().isBlank()) {
            if (processoApi.buscarIdPorNome(dto.nomeProcesso()).isEmpty()) {
                throw new IllegalArgumentException("Processo de negócio não encontrado: " + dto.nomeProcesso());
            }
        }
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Código do Material;Descrição;Unidade de Medida;Suprimento/Material (S/M);Avaliação Técnica;Centro de Investimento;Detalha Série (SIM/NAO);Processo de Negócio\n";
        String exemplo = "# Exemplo: MAT-001;Cabo de Cobre 10mm;METRO;M;Aprovado para Uso Geral;CI-100;NAO;COMERCIAL\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }
}
