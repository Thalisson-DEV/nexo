package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.obras.application.usecases.LeitorPlanilhaImportacao.Linha;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Processa a planilha de kits em segundo plano: uma linha por item, agrupada pelo código do kit.
 *
 * <p>Cada kit é gravado na própria transação, então um kit com problema não desfaz os demais. Dentro do kit,
 * linhas com Ação/Material inexistente ou dados inválidos são rejeitadas e registradas no relatório sem
 * impedir as outras linhas. Uma linha sem código de item só cria/atualiza os dados do kit.</p>
 */
@Slf4j
@Service
public class ProcessarImportacaoKitsService {

    private static final String[] COL_CODIGO_KIT = {"CODIGO_KIT", "KIT", "COD_KIT"};
    private static final String[] COL_DESCRICAO_KIT = {"DESCRICAO_KIT", "DESCRICAO", "NOME_KIT"};
    private static final String[] COL_GRUPO = {"GRUPO", "GRUPO_KIT"};
    private static final String[] COL_TENSOES = {"TENSOES", "TENSAO", "CLASSE_TENSAO", "CLASSES_TENSAO"};
    private static final String[] COL_TIPO = {"TIPO_ITEM", "TIPO"};
    private static final String[] COL_CODIGO_ITEM = {"CODIGO_ITEM", "COD_ITEM", "ITEM", "CODIGO"};
    private static final String[] COL_OPERACAO = {"OPERACAO", "ACAO", "INSTALAR_RETIRAR"};
    private static final String[] COL_QUANTIDADE = {"QUANTIDADE", "QTD", "QTDE"};
    private static final String[] COL_UNIDADE = {"UNIDADE", "UN", "UND", "UNIDADE_MEDIDA"};

    private final ImportacaoObraRepository importacaoRepository;
    private final KitConstrutivoRepository kitRepository;
    private final ContratoApi contratoApi;
    private final EstoqueApi estoqueApi;
    private final LeitorPlanilhaImportacao leitor;
    private final TransactionTemplate transacao;

    public ProcessarImportacaoKitsService(ImportacaoObraRepository importacaoRepository,
                                          KitConstrutivoRepository kitRepository,
                                          ContratoApi contratoApi,
                                          EstoqueApi estoqueApi,
                                          LeitorPlanilhaImportacao leitor,
                                          PlatformTransactionManager transacoes) {
        this.importacaoRepository = importacaoRepository;
        this.kitRepository = kitRepository;
        this.contratoApi = contratoApi;
        this.estoqueApi = estoqueApi;
        this.leitor = leitor;
        this.transacao = new TransactionTemplate(transacoes);
    }

    @Async
    @CacheEvict(cacheNames = CacheNames.OBRAS_KITS, allEntries = true)
    public void processar(UUID idImportacao, byte[] conteudo) {
        ImportacaoObra importacao = importacaoRepository.findById(idImportacao).orElse(null);
        if (importacao == null) {
            log.warn("Importação de kits {} não encontrada para processamento", idImportacao);
            return;
        }
        try {
            List<Linha> linhas = leitor.ler(conteudo, importacao.getNomeArquivo());
            Referencias referencias = new Referencias();
            for (Map.Entry<String, List<Linha>> grupo : agruparPorKit(linhas, importacao).entrySet()) {
                processarKit(importacao, grupo.getKey(), grupo.getValue(), referencias);
            }
            importacao.concluir(linhas.size());
        } catch (InvarianteVioladaException e) {
            importacao.falhar(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Falha inesperada na importação de kits {}", idImportacao, e);
            importacao.falhar("Falha inesperada ao processar a planilha. Tente novamente ou contate o suporte.");
        }
        importacaoRepository.save(importacao);
    }

    private Map<String, List<Linha>> agruparPorKit(List<Linha> linhas, ImportacaoObra importacao) {
        Map<String, List<Linha>> grupos = new LinkedHashMap<>();
        for (Linha linha : linhas) {
            String codigo = linha.valor(COL_CODIGO_KIT);
            if (codigo == null) {
                importacao.registrarErro(linha.numero(), null, "Código do kit não informado.");
                continue;
            }
            grupos.computeIfAbsent(codigo.trim().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(linha);
        }
        return grupos;
    }

    private void processarKit(ImportacaoObra importacao, String codigo, List<Linha> linhas, Referencias referencias) {
        List<Runnable> contagens = new ArrayList<>();
        try {
            transacao.executeWithoutResult(status -> {
                contagens.clear();
                aplicarKit(importacao.getIdContrato(), codigo, linhas, referencias, importacao, contagens);
            });
            contagens.forEach(Runnable::run);
        } catch (InvarianteVioladaException e) {
            linhas.forEach(l -> importacao.registrarErro(l.numero(), codigo, e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Falha ao gravar o kit {} na importação {}", codigo, importacao.getId(), e);
            linhas.forEach(l -> importacao.registrarErro(l.numero(), codigo, "Falha ao gravar o kit."));
        }
    }

    /**
     * As contagens de inseridos/atualizados só valem se a transação do kit for confirmada, por isso são
     * acumuladas e aplicadas depois do commit. Erros de linha ficam registrados mesmo se o kit falhar.
     */
    private void aplicarKit(UUID idContrato, String codigo, List<Linha> linhas, Referencias referencias,
                            ImportacaoObra importacao, List<Runnable> contagens) {
        String descricao = primeiroValor(linhas, COL_DESCRICAO_KIT);
        String grupo = primeiroValor(linhas, COL_GRUPO);
        String tensoesTexto = primeiroValor(linhas, COL_TENSOES);
        Set<ClasseTensao> tensoes = tensoesTexto != null ? interpretarTensoes(tensoesTexto) : null;

        Optional<KitConstrutivo> existente = kitRepository.findByIdContratoAndCodigoAndAtivoTrue(idContrato, codigo);
        boolean novo = existente.isEmpty();
        KitConstrutivo kit;
        if (novo) {
            if (descricao == null) {
                throw new InvarianteVioladaException("Kit novo sem descrição: informe DESCRICAO_KIT em ao menos uma linha.");
            }
            kit = new KitConstrutivo(idContrato, codigo, descricao, grupo, tensoes);
        } else {
            kit = existente.get();
            kit.atualizarDados(
                    descricao != null ? descricao : kit.getDescricao(),
                    grupo != null ? grupo : kit.getGrupo(),
                    tensoes != null ? tensoes : kit.getClassesTensao());
        }

        for (Linha linha : linhas) {
            String codigoItem = linha.valor(COL_CODIGO_ITEM);
            if (codigoItem == null) {
                contagens.add(novo ? importacao::registrarInserido : importacao::registrarAtualizado);
                continue;
            }
            try {
                TipoItemKit tipo = interpretarTipo(linha.valor(COL_TIPO));
                UUID idReferencia = referencias.resolver(tipo, codigoItem);
                OperacaoItemKit operacao = interpretarOperacao(linha.valor(COL_OPERACAO));
                BigDecimal quantidade = LeitorPlanilhaImportacao.interpretarDecimal(linha.valor(COL_QUANTIDADE));
                if (quantidade == null) {
                    throw new InvarianteVioladaException("Quantidade não informada.");
                }
                boolean criado = kit.adicionarOuAtualizarItem(tipo, idReferencia, operacao,
                        linha.valor(COL_UNIDADE), quantidade);
                contagens.add(criado ? importacao::registrarInserido : importacao::registrarAtualizado);
            } catch (InvarianteVioladaException e) {
                importacao.registrarErro(linha.numero(), codigo + " / " + codigoItem, e.getMessage());
            }
        }
        kitRepository.save(kit);
    }

    private static String primeiroValor(List<Linha> linhas, String... colunas) {
        return linhas.stream().map(l -> l.valor(colunas)).filter(v -> v != null).findFirst().orElse(null);
    }

    static Set<ClasseTensao> interpretarTensoes(String texto) {
        Set<ClasseTensao> tensoes = EnumSet.noneOf(ClasseTensao.class);
        for (String parte : texto.split("[;,/|]")) {
            if (parte.isBlank() || parte.trim().equalsIgnoreCase("TODAS")) {
                continue;
            }
            tensoes.add(ClasseTensao.interpretar(parte)
                    .orElseThrow(() -> new InvarianteVioladaException("Classe de tensão inválida: " + parte.trim())));
        }
        return tensoes;
    }

    static TipoItemKit interpretarTipo(String valor) {
        String v = valor != null ? LeitorPlanilhaImportacao.normalizarCabecalho(valor) : "";
        return switch (v) {
            case "SERVICO", "S", "SERV" -> TipoItemKit.SERVICO;
            case "MATERIAL", "M", "MAT" -> TipoItemKit.MATERIAL;
            default -> throw new InvarianteVioladaException("Tipo do item inválido: use SERVICO ou MATERIAL.");
        };
    }

    static OperacaoItemKit interpretarOperacao(String valor) {
        String v = valor != null ? LeitorPlanilhaImportacao.normalizarCabecalho(valor) : "";
        return switch (v) {
            case "", "INSTALAR", "I", "INST", "INSTALACAO" -> OperacaoItemKit.INSTALAR;
            case "RETIRAR", "R", "RET", "RETIRADA" -> OperacaoItemKit.RETIRAR;
            default -> throw new InvarianteVioladaException("Operação inválida: use INSTALAR ou RETIRAR.");
        };
    }

    /**
     * Cache das referências por código durante uma importação, para não consultar a mesma Ação/Material a cada linha.
     */
    private final class Referencias {
        private final Map<String, Optional<UUID>> acoes = new HashMap<>();
        private final Map<String, Optional<UUID>> materiais = new HashMap<>();

        UUID resolver(TipoItemKit tipo, String codigo) {
            String chave = codigo.trim().toUpperCase(Locale.ROOT);
            if (tipo == TipoItemKit.SERVICO) {
                return acoes.computeIfAbsent(chave, c -> contratoApi.buscarAcaoAtivaPorCodigo(c).map(AcaoResponse::id))
                        .orElseThrow(() -> new InvarianteVioladaException("Ação " + codigo + " não encontrada ou inativa."));
            }
            return materiais.computeIfAbsent(chave, c -> estoqueApi.buscarMaterialAtivoPorCodigo(c).map(MaterialResponse::id))
                    .orElseThrow(() -> new InvarianteVioladaException("Material " + codigo + " não encontrado ou inativo."));
        }
    }
}
