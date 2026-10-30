package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.ReferenciaEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Produz os mesmos diffs legiveis do fluxo tecnico para comandos comerciais. */
@Service
public class AlteracoesSolicitacaoComercialService {

    public Snapshot capturar(SolicitacaoMaterial solicitacao) {
        Map<UUID, ItemSnapshot> itens = new LinkedHashMap<>();
        solicitacao.getItens().forEach(item -> itens.put(item.getId(), itemSnapshot(item)));
        return new Snapshot(
                solicitacao.getStatus(),
                solicitacao.getMotivoRejeicao() == null ? null : new ReferenciaEventoSolicitacao(
                        solicitacao.getMotivoRejeicao().getId(), solicitacao.getMotivoRejeicao().getNome()),
                solicitacao.getJustificativaRejeicao(),
                solicitacao.getEstoqueOrigem() == null ? null : new ReferenciaEventoSolicitacao(
                        solicitacao.getEstoqueOrigem().getId(), solicitacao.getEstoqueOrigem().getNomeIdentificador()),
                solicitacao.getDataPrevistaRetirada(),
                itens);
    }

    public List<AlteracaoEventoSolicitacao> comparar(Snapshot anterior, SolicitacaoMaterial solicitacao) {
        Snapshot atual = capturar(solicitacao);
        List<AlteracaoEventoSolicitacao> alteracoes = new ArrayList<>();

        adicionarSeDiferente(alteracoes, "status", anterior.status(), atual.status());
        adicionarSeDiferente(alteracoes, "motivo", anterior.motivoRejeicao(), atual.motivoRejeicao());
        adicionarSeDiferente(
                alteracoes, "justificativa", anterior.justificativaRejeicao(), atual.justificativaRejeicao());
        adicionarSeDiferente(alteracoes, "estoqueOrigem", anterior.estoqueOrigem(), atual.estoqueOrigem());
        adicionarSeDiferente(alteracoes, "dataPrevista", anterior.dataPrevista(), atual.dataPrevista());

        anterior.itens().forEach((id, itemAnterior) -> {
            ItemSnapshot itemAtual = atual.itens().get(id);
            String raiz = "itens[" + id + "]";
            if (itemAtual == null) {
                alteracoes.add(new AlteracaoEventoSolicitacao(raiz, itemAnterior.comoPayload(), null));
                return;
            }
            adicionarSeDiferente(
                    alteracoes, raiz + ".quantidade", itemAnterior.quantidade(), itemAtual.quantidade());
            adicionarSeDiferente(alteracoes, raiz + ".quantidadeEntregaLiquida",
                    itemAnterior.quantidadeEntregaLiquida(), itemAtual.quantidadeEntregaLiquida());
            adicionarSeDiferente(alteracoes, raiz + ".quantidadePendente",
                    itemAnterior.quantidadePendente(), itemAtual.quantidadePendente());
        });

        atual.itens().forEach((id, itemAtual) -> {
            if (!anterior.itens().containsKey(id)) {
                alteracoes.add(new AlteracaoEventoSolicitacao(
                        "itens[" + id + "]", null, itemAtual.comoPayload()));
            }
        });
        return List.copyOf(alteracoes);
    }

    private ItemSnapshot itemSnapshot(ItemSolicitacao item) {
        return new ItemSnapshot(
                item.getId(),
                new ReferenciaEventoSolicitacao(
                        item.getMaterial().getId(),
                        item.getMaterial().getCodigo() + " - " + item.getMaterial().getDescricao()),
                item.getQuantidade(),
                item.getQuantidadeEntregaLiquida(),
                item.getQuantidadePendente(),
                item.getNumSerie());
    }

    private void adicionarSeDiferente(
            List<AlteracaoEventoSolicitacao> alteracoes,
            String caminho,
            Object anterior,
            Object atual
    ) {
        if (!Objects.equals(anterior, atual)) {
            alteracoes.add(new AlteracaoEventoSolicitacao(caminho, anterior, atual));
        }
    }

    public record Snapshot(
            SolicitacaoMaterial.StatusSolicitacao status,
            ReferenciaEventoSolicitacao motivoRejeicao,
            String justificativaRejeicao,
            ReferenciaEventoSolicitacao estoqueOrigem,
            LocalDate dataPrevista,
            Map<UUID, ItemSnapshot> itens
    ) {
        public Snapshot {
            itens = Map.copyOf(itens);
        }
    }

    public record ItemSnapshot(
            UUID id,
            ReferenciaEventoSolicitacao material,
            BigDecimal quantidade,
            BigDecimal quantidadeEntregaLiquida,
            BigDecimal quantidadePendente,
            String numeroSerie
    ) {
        Map<String, Object> comoPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("material", material);
            payload.put("quantidade", quantidade);
            payload.put("quantidadeEntregaLiquida", quantidadeEntregaLiquida);
            payload.put("quantidadePendente", quantidadePendente);
            if (numeroSerie != null && !numeroSerie.isBlank()) {
                payload.put("numeroSerie", numeroSerie);
            }
            return payload;
        }
    }
}
