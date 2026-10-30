package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.application.dto.HistoricoImportacaoResponse;
import com.nexo.app.modules.obras.application.mappers.ImportacaoObraMapper;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Recebe a planilha de kits, registra a importação e dispara o processamento assíncrono. O usuário acompanha
 * o andamento e o relatório por linha pelo histórico de importações.
 */
@Service
@RequiredArgsConstructor
public class ImportarKitsUseCase {

    static final String[] CABECALHO_MODELO = {
            "CODIGO_KIT", "DESCRICAO_KIT", "GRUPO", "TENSOES",
            "TIPO_ITEM", "CODIGO_ITEM", "OPERACAO", "QUANTIDADE", "UNIDADE"
    };

    private final ImportacaoObraRepository importacaoRepository;
    private final ImportacaoObraMapper mapper;
    private final ContratoApi contratoApi;
    private final ProcessarImportacaoKitsService processador;

    public HistoricoImportacaoResponse iniciar(UUID idContrato, MultipartFile arquivo) {
        if (idContrato == null || !contratoApi.existeContrato(idContrato)) {
            throw new InvarianteVioladaException("Selecione um contrato ativo para importar os kits.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Selecione uma planilha preenchida.");
        }
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
        ImportacaoObra importacao = importacaoRepository.save(
                new ImportacaoObra(ImportacaoObra.Tipo.KITS, idContrato, arquivo.getOriginalFilename()));
        processador.processar(importacao.getId(), conteudo);
        return mapper.toResponse(importacao);
    }

    public byte[] gerarModelo() {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Kits", CABECALHO_MODELO);
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            planilha.linha("KIT-EXEMPLO-01", "Estrutura BT com poste 11m", "ESTRUTURA", "BT; 15 kV",
                    "SERVICO", "COD-ACAO", "INSTALAR", 1, "UN");
            planilha.linha("KIT-EXEMPLO-01", "", "", "", "MATERIAL", "COD-MATERIAL", "INSTALAR", 2, "UN");
            planilha.escrever(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o modelo de importação de kits.", e);
        }
    }
}
