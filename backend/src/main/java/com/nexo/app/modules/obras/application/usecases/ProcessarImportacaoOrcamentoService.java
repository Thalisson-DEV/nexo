package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.application.usecases.LeitorPlanilhaImportacao.Linha;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.ItemOrcamentoObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemOrcamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa a planilha de serviços/orçamento previstos: uma linha por serviço, agrupada pelo número da obra.
 *
 * <p>Para cada obra da planilha, os serviços previstos anteriores são desativados e substituídos pelos da
 * planilha (linhas inválidas ficam no relatório e não entram). Colunas de valor de material/serviço previsto,
 * quando informadas, atualizam os totais do orçamento na obra.</p>
 */
@Slf4j
@Service
public class ProcessarImportacaoOrcamentoService {

    static final String[] COL_OBRA = ProcessarImportacaoCarteiraService.COL_NUMERO;
    static final String[] COL_CODIGO = {"CODIGO_SERVICO", "CODIGO", "SERVICO", "COD_SERVICO"};
    static final String[] COL_DESCRICAO = {"DESCRICAO", "DESCRICAO_SERVICO"};
    static final String[] COL_UNIDADE = {"UNIDADE", "UN", "UND"};
    static final String[] COL_QUANTIDADE = {"QUANTIDADE", "QTD", "QTDE"};
    static final String[] COL_VALOR_UNITARIO = {"VALOR_UNITARIO", "PRECO", "PRECO_UNITARIO"};
    static final String[] COL_VALOR_TOTAL = {"VALOR_TOTAL", "TOTAL"};

    private final ImportacaoObraRepository importacaoRepository;
    private final ObraRepository obraRepository;
    private final ItemOrcamentoObraRepository itemRepository;
    private final ContratoApi contratoApi;
    private final NormalizadorObraService normalizador;
    private final TransactionTemplate transacao;

    public ProcessarImportacaoOrcamentoService(ImportacaoObraRepository importacaoRepository,
                                               ObraRepository obraRepository,
                                               ItemOrcamentoObraRepository itemRepository,
                                               ContratoApi contratoApi,
                                               NormalizadorObraService normalizador,
                                               PlatformTransactionManager transacoes) {
        this.importacaoRepository = importacaoRepository;
        this.obraRepository = obraRepository;
        this.itemRepository = itemRepository;
        this.contratoApi = contratoApi;
        this.normalizador = normalizador;
        this.transacao = new TransactionTemplate(transacoes);
    }

    @Async
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public void processar(UUID idImportacao, List<Linha> linhas) {
        ImportacaoObra importacao = importacaoRepository.findById(idImportacao).orElse(null);
        if (importacao == null) {
            log.warn("Importação de orçamento {} não encontrada para processamento", idImportacao);
            return;
        }
        try {
            Map<String, List<Linha>> porObra = new LinkedHashMap<>();
            for (Linha linha : linhas) {
                String numero = normalizador.normalizarNumero(linha.valor(COL_OBRA));
                if (numero == null) {
                    importacao.registrarErro(linha.numero(), null, "Número da obra não informado.");
                } else {
                    porObra.computeIfAbsent(numero, k -> new ArrayList<>()).add(linha);
                }
            }
            Map<String, Optional<UUID>> acoes = new HashMap<>();
            porObra.forEach((numero, grupo) -> processarObra(importacao, numero, grupo, acoes));
            importacao.concluir(linhas.size());
        } catch (InvarianteVioladaException e) {
            importacao.falhar(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Falha inesperada na importação de orçamento {}", idImportacao, e);
            importacao.falhar("Falha inesperada ao processar a planilha. Tente novamente ou contate o suporte.");
        }
        importacaoRepository.save(importacao);
    }

    private void processarObra(ImportacaoObra importacao, String numero, List<Linha> linhas,
                               Map<String, Optional<UUID>> acoes) {
        List<Runnable> contagens = new ArrayList<>();
        try {
            transacao.executeWithoutResult(status -> {
                contagens.clear();
                Obra obra = obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(numero)
                        .orElseThrow(() -> new InvarianteVioladaException("Obra " + numero + " não encontrada."));
                List<ItemOrcamentoObra> novos = new ArrayList<>();
                for (Linha linha : linhas) {
                    try {
                        String codigo = linha.valor(COL_CODIGO);
                        if (codigo == null) {
                            throw new InvarianteVioladaException("Código do serviço não informado.");
                        }
                        BigDecimal quantidade = LeitorPlanilhaImportacao.interpretarDecimal(linha.valor(COL_QUANTIDADE));
                        UUID idAcao = acoes.computeIfAbsent(codigo.trim().toUpperCase(Locale.ROOT),
                                c -> contratoApi.buscarAcaoAtivaPorCodigo(c).map(AcaoResponse::id)).orElse(null);
                        novos.add(new ItemOrcamentoObra(obra.getId(), importacao.getId(), codigo,
                                linha.valor(COL_DESCRICAO), linha.valor(COL_UNIDADE), quantidade,
                                LeitorPlanilhaImportacao.interpretarDecimal(linha.valor(COL_VALOR_UNITARIO)),
                                LeitorPlanilhaImportacao.interpretarDecimal(linha.valor(COL_VALOR_TOTAL)), idAcao));
                        contagens.add(importacao::registrarInserido);
                    } catch (InvarianteVioladaException e) {
                        importacao.registrarErro(linha.numero(), numero, e.getMessage());
                    }
                }
                if (novos.isEmpty()) {
                    return;
                }
                List<ItemOrcamentoObra> anteriores = itemRepository.findByIdObraAndAtivoTrueOrderByCodigoAsc(obra.getId());
                anteriores.forEach(ItemOrcamentoObra::desativar);
                itemRepository.saveAll(anteriores);
                itemRepository.saveAll(novos);
                atualizarTotais(obra, linhas);
            });
            contagens.forEach(Runnable::run);
        } catch (InvarianteVioladaException e) {
            linhas.forEach(l -> importacao.registrarErro(l.numero(), numero, e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Falha ao gravar o orçamento da obra {} na importação {}", numero, importacao.getId(), e);
            linhas.forEach(l -> importacao.registrarErro(l.numero(), numero, "Falha ao gravar o orçamento."));
        }
    }

    private void atualizarTotais(Obra obra, List<Linha> linhas) {
        BigDecimal material = linhas.stream()
                .map(l -> normalizador.parseValor(l.valor(ProcessarImportacaoCarteiraService.COL_VALOR_MATERIAL)))
                .filter(v -> v != null).findFirst().orElse(null);
        BigDecimal servico = linhas.stream()
                .map(l -> normalizador.parseValor(l.valor(ProcessarImportacaoCarteiraService.COL_VALOR_SERVICO)))
                .filter(v -> v != null).findFirst().orElse(null);
        if (material == null && servico == null) {
            return;
        }
        obra.informarCarteira(obra.getIdContrato(), obra.getPrazoRegulado(), obra.getPrioridade(),
                obra.getLotePrevisto(), obra.getSituacaoCarteira(), null,
                material != null ? material : obra.getValorMaterialPrevisto(),
                servico != null ? servico : obra.getValorServicoPrevisto());
        obraRepository.save(obra);
    }
}
