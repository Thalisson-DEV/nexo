package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.KitImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import com.nexo.app.modules.obras.application.usecases.LeitorPlanilhaImportacao.Linha;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.nexo.app.modules.obras.application.usecases.ProcessarImportacaoKitsService.*;

/**
 * Importação de kits em duas etapas, como as demais importações de planilha: a validação devolve cada linha com a
 * situação (pronta ou com erro) para conferência e correção na tela; a confirmação revalida as linhas e processa as
 * válidas em segundo plano, registrando a importação no histórico.
 */
@Service
@RequiredArgsConstructor
public class ImportarKitsUseCase {

    static final String[] CABECALHO_MODELO = {
            "CODIGO_KIT", "DESCRICAO_KIT", "GRUPO", "TENSOES",
            "TIPO_ITEM", "CODIGO_ITEM", "OPERACAO", "QUANTIDADE", "UNIDADE"
    };

    private final ImportacaoObraRepository importacaoRepository;
    private final KitConstrutivoRepository kitRepository;
    private final ContratoApi contratoApi;
    private final EstoqueApi estoqueApi;
    private final ProcessarImportacaoKitsService processador;
    private final LeitorPlanilhaImportacao leitor;

    public PreviaImportacaoObraResponse<KitImportacaoLinha> validar(UUID idContrato, MultipartFile arquivo) {
        exigirContrato(idContrato);
        List<KitImportacaoLinha> registros = leitor.ler(ArquivoImportacao.bytes(arquivo), arquivo.getOriginalFilename())
                .stream().map(ImportarKitsUseCase::deLinha).toList();
        return resumo(validarLote(idContrato, registros), null);
    }

    /** Revalida as linhas da pré-visualização depois de uma correção, sem gravar. */
    public PreviaImportacaoObraResponse<KitImportacaoLinha> revalidar(ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha> request) {
        exigirContrato(request.idContrato());
        return resumo(validarLote(request.idContrato(), request.registros()), null);
    }

    public PreviaImportacaoObraResponse<KitImportacaoLinha> confirmar(ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha> request) {
        exigirContrato(request.idContrato());
        List<KitImportacaoLinha> revalidadas = validarLote(request.idContrato(), request.registros());
        List<KitImportacaoLinha> validas = revalidadas.stream().filter(r -> r.status() != ImportacaoStatus.COM_ERRO).toList();
        if (validas.isEmpty()) {
            return resumo(revalidadas, null);
        }
        ImportacaoObra importacao = importacaoRepository.save(
                new ImportacaoObra(ImportacaoObra.Tipo.KITS, request.idContrato(), request.nomeArquivo()));
        processador.processar(importacao.getId(), validas.stream().map(ImportarKitsUseCase::paraLinha).toList());
        return resumo(revalidadas.stream()
                .map(r -> r.status() == ImportacaoStatus.COM_ERRO ? r : r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList(), importacao.getId());
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

    List<KitImportacaoLinha> validarLote(UUID idContrato, List<KitImportacaoLinha> registros) {
        Map<String, Optional<UUID>> acoes = new HashMap<>();
        Map<String, Optional<UUID>> materiais = new HashMap<>();
        List<KitImportacaoLinha> validadas = new ArrayList<>();
        for (KitImportacaoLinha r : registros) {
            validadas.add(validarLinha(r, acoes, materiais));
        }
        // Kit novo precisa de descrição em ao menos uma das suas linhas.
        Map<String, List<Integer>> porKit = new LinkedHashMap<>();
        for (int i = 0; i < validadas.size(); i++) {
            String codigo = texto(validadas.get(i).codigoKit());
            if (codigo != null) {
                porKit.computeIfAbsent(codigo.toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(i);
            }
        }
        porKit.forEach((codigo, indices) -> {
            boolean temDescricao = indices.stream().anyMatch(i -> texto(validadas.get(i).descricaoKit()) != null);
            if (!temDescricao && kitRepository.findByIdContratoAndCodigoAndAtivoTrue(idContrato, codigo).isEmpty()) {
                indices.forEach(i -> validadas.set(i, erro(validadas.get(i),
                        "Kit novo sem descrição: informe a descrição em ao menos uma linha do kit.")));
            }
        });
        return validadas;
    }

    private KitImportacaoLinha validarLinha(KitImportacaoLinha r, Map<String, Optional<UUID>> acoes,
                                            Map<String, Optional<UUID>> materiais) {
        List<String> erros = new ArrayList<>();
        if (texto(r.codigoKit()) == null) {
            erros.add("Código do kit não informado.");
        }
        tentar(erros, () -> {
            if (texto(r.tensoes()) != null) {
                interpretarTensoes(r.tensoes());
            }
        });
        String codigoItem = texto(r.codigoItem());
        if (codigoItem != null) {
            tentar(erros, () -> {
                TipoItemKit tipo = interpretarTipo(r.tipoItem());
                String chave = codigoItem.toUpperCase(Locale.ROOT);
                boolean existe = tipo == TipoItemKit.SERVICO
                        ? acoes.computeIfAbsent(chave, c -> contratoApi.buscarAcaoAtivaPorCodigo(c).map(AcaoResponse::id)).isPresent()
                        : materiais.computeIfAbsent(chave, c -> estoqueApi.buscarMaterialAtivoPorCodigo(c).map(MaterialResponse::id)).isPresent();
                if (!existe) {
                    throw new InvarianteVioladaException((tipo == TipoItemKit.SERVICO ? "Ação " : "Material ")
                            + codigoItem + " não encontrado ou inativo.");
                }
            });
            tentar(erros, () -> interpretarOperacao(r.operacao()));
            tentar(erros, () -> {
                BigDecimal quantidade = LeitorPlanilhaImportacao.interpretarDecimal(r.quantidade());
                if (quantidade == null || quantidade.signum() <= 0) {
                    throw new InvarianteVioladaException("Informe a quantidade maior que zero.");
                }
            });
        }
        return erros.isEmpty()
                ? r.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null)
                : r.withStatus(ImportacaoStatus.COM_ERRO, String.join(" ", erros));
    }

    private static KitImportacaoLinha erro(KitImportacaoLinha r, String motivo) {
        String atual = r.status() == ImportacaoStatus.COM_ERRO ? r.mensagemErro() + " " : "";
        return r.withStatus(ImportacaoStatus.COM_ERRO, atual + motivo);
    }

    static KitImportacaoLinha deLinha(Linha l) {
        return new KitImportacaoLinha(l.numero(), l.valor(COL_CODIGO_KIT), l.valor(COL_DESCRICAO_KIT), l.valor(COL_GRUPO),
                l.valor(COL_TENSOES), l.valor(COL_TIPO), l.valor(COL_CODIGO_ITEM), l.valor(COL_OPERACAO),
                l.valor(COL_QUANTIDADE), l.valor(COL_UNIDADE), null, null);
    }

    static Linha paraLinha(KitImportacaoLinha r) {
        Map<String, String> v = new HashMap<>();
        v.put(COL_CODIGO_KIT[0], r.codigoKit());
        v.put(COL_DESCRICAO_KIT[0], r.descricaoKit());
        v.put(COL_GRUPO[0], r.grupo());
        v.put(COL_TENSOES[0], r.tensoes());
        v.put(COL_TIPO[0], r.tipoItem());
        v.put(COL_CODIGO_ITEM[0], r.codigoItem());
        v.put(COL_OPERACAO[0], r.operacao());
        v.put(COL_QUANTIDADE[0], r.quantidade());
        v.put(COL_UNIDADE[0], r.unidade());
        return new Linha(r.linha() != null ? r.linha() : 0, v);
    }

    private void exigirContrato(UUID idContrato) {
        if (idContrato == null || !contratoApi.existeContrato(idContrato)) {
            throw new InvarianteVioladaException("Selecione um contrato ativo para importar os kits.");
        }
    }

    private static <T> PreviaImportacaoObraResponse<T> resumo(List<T> registros, UUID idImportacao) {
        int falhas = (int) registros.stream().filter(ArquivoImportacao::comErro).count();
        return new PreviaImportacaoObraResponse<>(registros, registros.size(), registros.size() - falhas, falhas, idImportacao);
    }

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static void tentar(List<String> erros, Runnable validacao) {
        try {
            validacao.run();
        } catch (InvarianteVioladaException | IllegalArgumentException e) {
            erros.add(e.getMessage());
        }
    }
}
