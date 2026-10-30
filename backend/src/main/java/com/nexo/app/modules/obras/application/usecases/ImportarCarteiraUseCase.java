package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.application.dto.HistoricoImportacaoResponse;
import com.nexo.app.modules.obras.application.mappers.ImportacaoObraMapper;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Recebe as planilhas da carteira de obras e dos serviços/orçamento previstos, registra a importação e dispara o
 * processamento assíncrono. O andamento e o relatório por linha ficam no histórico de importações.
 */
@Service
@RequiredArgsConstructor
public class ImportarCarteiraUseCase {

    static final String[] CABECALHO_CARTEIRA = {
            "NUMERO", "TITULO", "TENSAO", "PRAZO", "PRAZO_REGULADO", "PRIORIDADE", "LOTE_PREVISTO",
            "SITUACAO_CARTEIRA", "VALOR_PROJETO", "VALOR_MATERIAL_PREVISTO", "VALOR_SERVICO_PREVISTO",
            "LATITUDE", "LONGITUDE", "PROCESSO_NEGOCIO", "BASE_OPERACIONAL", "MUNICIPIO", "CENTRO_CUSTO",
            "SUPERVISOR", "COORDENADOR", "STATUS", "DATA_INICIO", "TIPO_OBRA"
    };

    static final String[] CABECALHO_ORCAMENTO = {
            "NUMERO", "CODIGO_SERVICO", "DESCRICAO", "UNIDADE", "QUANTIDADE", "VALOR_UNITARIO", "VALOR_TOTAL",
            "VALOR_MATERIAL_PREVISTO", "VALOR_SERVICO_PREVISTO"
    };

    private final ImportacaoObraRepository importacaoRepository;
    private final ImportacaoObraMapper mapper;
    private final ContratoApi contratoApi;
    private final ProcessarImportacaoCarteiraService processadorCarteira;
    private final ProcessarImportacaoOrcamentoService processadorOrcamento;
    private final AuditorAware<String> auditor;

    public HistoricoImportacaoResponse iniciarCarteira(UUID idContrato, MultipartFile arquivo) {
        if (idContrato == null || !contratoApi.existeContrato(idContrato)) {
            throw new InvarianteVioladaException("Selecione um contrato ativo para importar a carteira.");
        }
        byte[] conteudo = ler(arquivo);
        ImportacaoObra importacao = importacaoRepository.save(
                new ImportacaoObra(ImportacaoObra.Tipo.CARTEIRA, idContrato, arquivo.getOriginalFilename()));
        // O processamento roda fora da requisição: o autor da classe de tensão é capturado aqui.
        processadorCarteira.processar(importacao.getId(), conteudo, auditor.getCurrentAuditor().orElse("SYSTEM"));
        return mapper.toResponse(importacao);
    }

    public HistoricoImportacaoResponse iniciarOrcamento(MultipartFile arquivo) {
        byte[] conteudo = ler(arquivo);
        ImportacaoObra importacao = importacaoRepository.save(
                new ImportacaoObra(ImportacaoObra.Tipo.ORCAMENTO, null, arquivo.getOriginalFilename()));
        processadorOrcamento.processar(importacao.getId(), conteudo);
        return mapper.toResponse(importacao);
    }

    public byte[] gerarModeloCarteira() {
        return gerarModelo("Carteira", CABECALHO_CARTEIRA, new Object[]{
                "1234567", "Extensão de rede BT", "15 kV", "30/06/2027", "15/06/2027", "Alta", "LOTE-01",
                "Liberada", "150000,00", "60000,00", "90000,00", "-9,4111", "-40,5030", "Expansão", "Base Exemplo",
                "Município Exemplo", "Centro de Custo Exemplo", "Nome do Supervisor", "Nome do Coordenador",
                "PLANEJADA", "01/11/2026", "OBRA"});
    }

    public byte[] gerarModeloOrcamento() {
        return gerarModelo("Orçamento", CABECALHO_ORCAMENTO, new Object[]{
                "1234567", "COD-ACAO", "Instalar estrutura BT", "UN", 2, "350,00", "", "60000,00", "90000,00"});
    }

    private static byte[] ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Selecione uma planilha preenchida.");
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private static byte[] gerarModelo(String aba, String[] cabecalho, Object[] exemplo) {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx(aba, cabecalho);
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            planilha.linha(exemplo);
            planilha.escrever(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o modelo de importação.", e);
        }
    }
}
