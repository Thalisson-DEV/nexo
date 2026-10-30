package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorDetalheResponse;
import com.nexo.app.modules.estoque.application.dto.ConsumoAnteriorSolicitacaoResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.NotaResumida;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Para cada material de uma solicitação comercial: quanto o estoque de destino recebeu na última
 * solicitação comercial daquele material e quanto aplicou em campo (e devolveu) desde aquela entrega.
 * A janela termina na primeira entrega desta solicitação; enquanto ela não foi entregue, vai até agora.
 */
@Service
@RequiredArgsConstructor
public class ConsumoAnteriorSolicitacaoUseCase {

    /** Solicitações anteriores avaliadas por consulta: cobre anos de pedidos de uma equipe. */
    private static final int LIMITE_ITENS_ANTERIORES = 2000;
    /** Sem solicitação anterior, o detalhe mostra como o saldo chegou ao que é nestes últimos dias. */
    private static final int DIAS_SEM_SOLICITACAO_ANTERIOR = 90;

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final ItemSolicitacaoRepository itemRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final SaldoRepository saldoRepository;
    private final ExecucaoComercialApi execucaoApi;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsumoAnteriorSolicitacaoResponse consultar(UUID idSolicitacao) {
        SolicitacaoMaterial atual = buscarComercial(idSolicitacao);
        UUID idEstoque = atual.getEstoqueDestino().getId();
        Set<UUID> materiais = new LinkedHashSet<>();
        for (ItemSolicitacao item : atual.getItens()) {
            if (item.isAtivo()) materiais.add(item.getMaterial().getId());
        }
        if (materiais.isEmpty()) {
            return new ConsumoAnteriorSolicitacaoResponse(atual.getId(), idEstoque, OffsetDateTime.now(clock), List.of());
        }
        Janelas janelas = janelas(atual, materiais);

        Map<UUID, BigDecimal> aplicada = new HashMap<>();
        Map<UUID, BigDecimal> devolvida = new HashMap<>();
        OffsetDateTime desdeMaisAntigo = janelas.inicioPorMaterial().values().stream().filter(Objects::nonNull).min(OffsetDateTime::compareTo).orElse(null);
        if (desdeMaisAntigo != null) {
            for (Object[] linha : movimentacaoRepository.saidasDoEstoqueDesde(
                    idEstoque, janelas.inicioPorMaterial().keySet(), List.of(TipoMovimentacao.APLICACAO, TipoMovimentacao.DEVOLUCAO), desdeMaisAntigo)) {
                UUID idMaterial = (UUID) linha[0];
                OffsetDateTime quando = (OffsetDateTime) linha[2];
                if (!janelas.contem(idMaterial, quando)) continue;
                Map<UUID, BigDecimal> alvo = linha[1] == TipoMovimentacao.APLICACAO ? aplicada : devolvida;
                alvo.merge(idMaterial, (BigDecimal) linha[3], BigDecimal::add);
            }
            // Nota alterada depois de sincronizada: o que saiu dela não foi aplicado de verdade.
            for (Object[] linha : movimentacaoRepository.estornosDeNotaNoEstoqueDesde(
                    idEstoque, janelas.inicioPorMaterial().keySet(), desdeMaisAntigo)) {
                UUID idMaterial = (UUID) linha[0];
                if (!janelas.contem(idMaterial, (OffsetDateTime) linha[1])) continue;
                aplicada.merge(idMaterial, ((BigDecimal) linha[2]).negate(), BigDecimal::add);
            }
            // A nota corrigida pode ter sido aplicada antes da janela: o consumo nunca fica negativo.
            aplicada.replaceAll((idMaterial, quantidade) -> quantidade.max(BigDecimal.ZERO));
        }

        List<ConsumoAnteriorSolicitacaoResponse.Item> itens = materiais.stream()
                .map(idMaterial -> {
                    boolean teveAnterior = janelas.ultimaPorMaterial().containsKey(idMaterial);
                    BigDecimal saldoAtual = saldoNovo(idEstoque, idMaterial);
                    BigDecimal saldoAntes = teveAnterior
                            ? contaDoSaldo(idEstoque, idMaterial, janelas.inicioPorMaterial().get(idMaterial), janelas.ate(), saldoAtual).inicial()
                            : null;
                    return new ConsumoAnteriorSolicitacaoResponse.Item(
                            idMaterial,
                            janelas.ultimaPorMaterial().get(idMaterial),
                            teveAnterior ? aplicada.getOrDefault(idMaterial, BigDecimal.ZERO) : null,
                            teveAnterior ? devolvida.getOrDefault(idMaterial, BigDecimal.ZERO) : null,
                            saldoAntes,
                            saldoAtual);
                })
                .toList();
        return new ConsumoAnteriorSolicitacaoResponse(atual.getId(), idEstoque, janelas.ate(), itens);
    }

    /**
     * As movimentações por trás do consumo de um material: tudo o que entrou e saiu do estoque de
     * destino na janela, e a conta do saldo NOVO do início ao fim dela. O saldo inicial é reconstruído
     * a partir do saldo atual, desfazendo as movimentações NOVO desde o início da janela.
     */
    @Transactional(readOnly = true)
    public ConsumoAnteriorDetalheResponse detalhar(UUID idSolicitacao, UUID idMaterial) {
        SolicitacaoMaterial atual = buscarComercial(idSolicitacao);
        boolean pedido = atual.getItens().stream()
                .anyMatch(i -> i.isAtivo() && i.getMaterial().getId().equals(idMaterial));
        if (!pedido) {
            throw new RecursoNaoEncontradoException("Material não faz parte da solicitação: " + idMaterial);
        }
        UUID idEstoque = atual.getEstoqueDestino().getId();
        BigDecimal saldoAtual = saldoNovo(idEstoque, idMaterial);
        Janelas janelas = janelas(atual, Set.of(idMaterial));
        boolean semSolicitacaoAnterior = !janelas.inicioPorMaterial().containsKey(idMaterial);
        // Sem solicitação anterior a conta ainda explica o saldo: tudo o que mexeu nele nos últimos dias.
        OffsetDateTime inicio = semSolicitacaoAnterior
                ? janelas.ate().minusDays(DIAS_SEM_SOLICITACAO_ANTERIOR)
                : janelas.inicioPorMaterial().get(idMaterial);

        ContaSaldo conta = contaDoSaldo(idEstoque, idMaterial, inicio, janelas.ate(), saldoAtual);

        List<MovimentacaoEstoque> naJanela = conta.desdeInicio().stream()
                .filter(m -> !m.getDataHora().isBefore(inicio) && m.getDataHora().isBefore(janelas.ate()))
                .toList();
        Set<UUID> execucoes = naJanela.stream()
                .filter(m -> m.getTipo() == TipoMovimentacao.APLICACAO || m.getTipo() == TipoMovimentacao.RETIRADA
                        || (m.getTipo() == TipoMovimentacao.ESTORNO && m.getSolicitacao() == null))
                .map(MovimentacaoEstoque::getIdReferenciaOperacao)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, NotaResumida> notas = execucoes.isEmpty() ? Map.of()
                : execucaoApi.resumirNotas(execucoes).stream()
                        .collect(Collectors.toMap(NotaResumida::idExecucao, Function.identity(), (a, b) -> a));

        List<ConsumoAnteriorDetalheResponse.Movimentacao> movimentacoes = naJanela.stream()
                .map(m -> paraMovimentacao(m, idEstoque, notas))
                .toList();
        return new ConsumoAnteriorDetalheResponse(
                idMaterial, inicio, janelas.ate(), semSolicitacaoAnterior, conta.inicial(), conta.fim(), saldoAtual, movimentacoes);
    }

    /**
     * O saldo NOVO no início e no fim da janela, reconstruído a partir do saldo atual desfazendo as
     * movimentações NOVO desde o início (as que vieram depois do fim só pesam no saldo do fim).
     */
    private ContaSaldo contaDoSaldo(UUID idEstoque, UUID idMaterial, OffsetDateTime inicio, OffsetDateTime ate, BigDecimal saldoAtual) {
        List<MovimentacaoEstoque> desdeInicio = movimentacaoRepository.doMaterialNoEstoqueDesde(idEstoque, idMaterial, inicio);
        BigDecimal saldoInicial = saldoAtual;
        BigDecimal saldoFinal = saldoAtual;
        for (MovimentacaoEstoque m : desdeInicio) {
            BigDecimal efeito = m.getCondicao() == CondicaoMaterial.NOVO ? comSinal(m, idEstoque) : BigDecimal.ZERO;
            saldoInicial = saldoInicial.subtract(efeito);
            if (!m.getDataHora().isBefore(ate)) saldoFinal = saldoFinal.subtract(efeito);
        }
        return new ContaSaldo(desdeInicio, saldoInicial, saldoFinal);
    }

    private record ContaSaldo(List<MovimentacaoEstoque> desdeInicio, BigDecimal inicial, BigDecimal fim) {}

    /** Início da janela de cada material (a entrega da última solicitação) e o fim comum a todos. */
    private Janelas janelas(SolicitacaoMaterial atual, Set<UUID> materiais) {
        UUID idEstoque = atual.getEstoqueDestino().getId();
        OffsetDateTime agora = OffsetDateTime.now(clock);

        // A mais recente de cada material (a consulta vem ordenada da mais nova para a mais antiga).
        // Material com série sai desmembrado em várias linhas de uma unidade: todas as linhas do
        // material naquela solicitação somam o que foi pedido e recebido.
        Map<UUID, ItemSolicitacao> anteriorPorMaterial = new HashMap<>();
        Map<UUID, BigDecimal> pedidaPorMaterial = new HashMap<>();
        Map<UUID, BigDecimal> recebidaPorMaterial = new HashMap<>();
        for (ItemSolicitacao item : itemRepository.findEntreguesAnteriores(
                idEstoque, atual.getId(), atual.getDataSolicitacao(), materiais, PageRequest.of(0, LIMITE_ITENS_ANTERIORES))) {
            UUID idMaterial = item.getMaterial().getId();
            ItemSolicitacao primeiro = anteriorPorMaterial.putIfAbsent(idMaterial, item);
            if (primeiro != null && !primeiro.getSolicitacao().getId().equals(item.getSolicitacao().getId())) continue;
            pedidaPorMaterial.merge(idMaterial, valor(item.getQuantidade()), BigDecimal::add);
            recebidaPorMaterial.merge(idMaterial, valor(item.getQuantidadeEntregaLiquida()), BigDecimal::add);
        }

        Set<UUID> solicitacoes = new LinkedHashSet<>();
        solicitacoes.add(atual.getId());
        anteriorPorMaterial.values().forEach(i -> solicitacoes.add(i.getSolicitacao().getId()));
        Map<String, OffsetDateTime> primeiraEntrega = new HashMap<>();
        for (Object[] linha : movimentacaoRepository.primeiraDataPorSolicitacaoEMaterial(solicitacoes, TipoMovimentacao.ENTREGA)) {
            primeiraEntrega.put(chave((UUID) linha[0], (UUID) linha[1]), (OffsetDateTime) linha[2]);
        }
        OffsetDateTime entregaAtual = primeiraEntrega.entrySet().stream()
                .filter(e -> e.getKey().startsWith(atual.getId() + ":"))
                .map(Map.Entry::getValue)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        OffsetDateTime ate = entregaAtual != null ? entregaAtual : agora;

        Map<UUID, OffsetDateTime> inicioPorMaterial = new HashMap<>();
        Map<UUID, ConsumoAnteriorSolicitacaoResponse.UltimaSolicitacao> ultimaPorMaterial = new HashMap<>();
        anteriorPorMaterial.forEach((idMaterial, item) -> {
            SolicitacaoMaterial anterior = item.getSolicitacao();
            OffsetDateTime inicio = primeiraEntrega.get(chave(anterior.getId(), idMaterial));
            if (inicio == null) inicio = anterior.getDataEntrega() != null ? anterior.getDataEntrega() : anterior.getDataSolicitacao();
            inicioPorMaterial.put(idMaterial, inicio);
            ultimaPorMaterial.put(idMaterial, new ConsumoAnteriorSolicitacaoResponse.UltimaSolicitacao(
                    anterior.getId(), anterior.getDataSolicitacao(), inicio,
                    pedidaPorMaterial.get(idMaterial), recebidaPorMaterial.get(idMaterial)));
        });
        return new Janelas(ate, inicioPorMaterial, ultimaPorMaterial);
    }

    private ConsumoAnteriorDetalheResponse.Movimentacao paraMovimentacao(
            MovimentacaoEstoque m, UUID idEstoque, Map<UUID, NotaResumida> notas) {
        boolean entrou = entrou(m, idEstoque);
        Estoque outro = entrou ? m.getEstoqueOrigem() : m.getEstoqueDestino();
        SolicitacaoMaterial solicitacao = m.getSolicitacao();
        NotaResumida nota = m.getIdReferenciaOperacao() == null ? null : notas.get(m.getIdReferenciaOperacao());
        return new ConsumoAnteriorDetalheResponse.Movimentacao(
                m.getId(), m.getTipo(), m.getDataHora(), comSinal(m, idEstoque), m.getCondicao(), m.getNumSerie(),
                m.getIdReferenciaOperacao(),
                solicitacao == null ? null : new ConsumoAnteriorDetalheResponse.SolicitacaoRef(solicitacao.getId(), solicitacao.getContexto()),
                outro == null ? null : new ConsumoAnteriorDetalheResponse.EstoqueRef(outro.getId(), outro.getNomeIdentificador()),
                nota == null ? null : new ConsumoAnteriorDetalheResponse.NotaRef(nota.idExecucao(), nota.referenciaNota(), nota.tipoServico()));
    }

    private static boolean entrou(MovimentacaoEstoque m, UUID idEstoque) {
        return m.getEstoqueDestino() != null && idEstoque.equals(m.getEstoqueDestino().getId());
    }

    private static BigDecimal comSinal(MovimentacaoEstoque m, UUID idEstoque) {
        return entrou(m, idEstoque) ? m.getQuantidade() : m.getQuantidade().negate();
    }

    private BigDecimal saldoNovo(UUID idEstoque, UUID idMaterial) {
        return saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(idEstoque, idMaterial, CondicaoMaterial.NOVO)
                .filter(Saldo::isAtivo)
                .map(Saldo::getQuantidade)
                .orElse(BigDecimal.ZERO);
    }

    private SolicitacaoMaterial buscarComercial(UUID idSolicitacao) {
        SolicitacaoMaterial atual = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada: " + idSolicitacao));
        if (atual.getContexto() != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException("O consumo anterior só se aplica a solicitações comerciais.");
        }
        return atual;
    }

    private static BigDecimal valor(BigDecimal quantidade) {
        return quantidade == null ? BigDecimal.ZERO : quantidade;
    }

    private static String chave(UUID idSolicitacao, UUID idMaterial) {
        return idSolicitacao + ":" + idMaterial;
    }

    private record Janelas(
            OffsetDateTime ate,
            Map<UUID, OffsetDateTime> inicioPorMaterial,
            Map<UUID, ConsumoAnteriorSolicitacaoResponse.UltimaSolicitacao> ultimaPorMaterial
    ) {
        /** A movimentação cai no período do material: a partir da entrega anterior e antes do fim. */
        boolean contem(UUID idMaterial, OffsetDateTime quando) {
            OffsetDateTime inicio = inicioPorMaterial.get(idMaterial);
            return inicio != null && !quando.isBefore(inicio) && quando.isBefore(ate);
        }
    }
}
