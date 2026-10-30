package com.nexo.app.modules.estoque.application.events;

import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.*;
import com.nexo.app.modules.estoque.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import com.nexo.app.shared.domain.events.ExecucaoAlteradaEvent;
import com.nexo.app.shared.domain.events.ExecucaoConcluidaEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

/**
 * Listener que processa a conclusão de uma execução comercial para atualizar o estoque.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExecucaoConcluidaListener {

    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository serieRepository;
    private final DebitoSaldoService debitoSaldoService;

    /**
     * Reage ao evento de execução concluída.
     * Atualiza o saldo do estoque móvel da equipe e registra a auditoria.
     */
    @ApplicationModuleListener
    @CacheEvict(value = SALDOS, allEntries = true)
    public void onExecucaoConcluida(ExecucaoConcluidaEvent event) {
        log.info("Processando materiais da execução concluída: {}", event.idExecucao());

        // 1. Localizar o estoque móvel da equipe
        Estoque estoqueEquipe = estoqueRepository.findByIdEquipe(event.idEquipe())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque móvel não encontrado para a equipe: " + event.idEquipe()));

        UUID idOperador = event.idUsuarioOperador();

        for (ExecucaoConcluidaEvent.ItemMaterialEvent item : event.itens()) {
            Material material = materialRepository.findById(item.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + item.idMaterial()));

            if (item.operacao() == OperacaoMaterial.APLICADO) {
                processarAplicacao(event.idExecucao(), estoqueEquipe, material, item, idOperador);
            } else if (item.operacao() == OperacaoMaterial.RETIRADO) {
                processarRetirada(event.idExecucao(), estoqueEquipe, material, item, idOperador);
            }
        }
    }

    /**
     * Reage à correção dos materiais de uma nota já sincronizada. Primeiro desfaz o que
     * saiu da nota e depois lança o que entrou, como se a nota tivesse vindo assim.
     */
    @ApplicationModuleListener
    @CacheEvict(value = SALDOS, allEntries = true)
    public void onExecucaoAlterada(ExecucaoAlteradaEvent event) {
        log.info("Ajustando o estoque da execução alterada: {}", event.idExecucao());

        Estoque estoqueEquipe = estoqueRepository.findByIdEquipe(event.idEquipe())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque móvel não encontrado para a equipe: " + event.idEquipe()));
        UUID idOperador = event.idUsuarioOperador();

        for (ExecucaoConcluidaEvent.ItemMaterialEvent item : event.estornos()) {
            Material material = buscarMaterial(item.idMaterial());
            if (item.operacao() == OperacaoMaterial.APLICADO) {
                estornarAplicacao(event.idExecucao(), estoqueEquipe, material, item, idOperador);
            } else if (item.operacao() == OperacaoMaterial.RETIRADO) {
                estornarRetirada(event.idExecucao(), estoqueEquipe, material, item, idOperador);
            }
        }
        for (ExecucaoConcluidaEvent.ItemMaterialEvent item : event.lancamentos()) {
            Material material = buscarMaterial(item.idMaterial());
            if (item.operacao() == OperacaoMaterial.APLICADO) {
                processarAplicacao(event.idExecucao(), estoqueEquipe, material, item, idOperador);
            } else if (item.operacao() == OperacaoMaterial.RETIRADO) {
                processarRetirada(event.idExecucao(), estoqueEquipe, material, item, idOperador);
            }
        }
    }

    private Material buscarMaterial(UUID idMaterial) {
        return materialRepository.findById(idMaterial)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + idMaterial));
    }

    /**
     * O material deixou de constar como aplicado na nota: volta ao saldo NOVO da equipe.
     * A série, que tinha saído com a aplicação, volta ao caminhão se não estiver ativa
     * em outro estoque.
     */
    private void estornarAplicacao(UUID idExecucao, Estoque estoque, Material material,
                                   ExecucaoConcluidaEvent.ItemMaterialEvent item, UUID idOperador) {
        movimentacaoRepository.save(new MovimentacaoEstoque(
                null, estoque, material, item.quantidade(),
                MovimentacaoEstoque.TipoMovimentacao.ESTORNO, CondicaoMaterial.NOVO, idOperador, idExecucao, item.numSerie()
        ));

        Saldo saldo = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(estoque.getId(), material.getId(), CondicaoMaterial.NOVO)
                .orElseGet(() -> new Saldo(estoque, material, CondicaoMaterial.NOVO));
        saldo.adicionarQuantidade(item.quantidade());
        saldoRepository.save(saldo);

        if (material.isDetalhaSerie() && item.numSerie() != null && !item.numSerie().isBlank()) {
            if (serieRepository.findByNumSerieAndAtivoTrue(item.numSerie()).isPresent()) {
                log.warn("Série {} estornada da execução {} já consta ativa em outro estoque; saldo serial não duplicado.",
                        item.numSerie(), idExecucao);
            } else {
                serieRepository.save(new SaldoSerie(estoque, material, item.numSerie(), CondicaoMaterial.NOVO));
            }
        }
    }

    /**
     * O material deixou de constar como retirado na nota: a sucata que tinha entrado no
     * caminhão sai do saldo da equipe (débito permissivo, como na aplicação).
     */
    private void estornarRetirada(UUID idExecucao, Estoque estoque, Material material,
                                  ExecucaoConcluidaEvent.ItemMaterialEvent item, UUID idOperador) {
        movimentacaoRepository.save(new MovimentacaoEstoque(
                estoque, null, material, item.quantidade(),
                MovimentacaoEstoque.TipoMovimentacao.ESTORNO, CondicaoMaterial.SUCATA, idOperador, idExecucao, item.numSerie()
        ));

        debitoSaldoService.debitar(estoque, material, CondicaoMaterial.SUCATA, item.quantidade());

        if (material.isDetalhaSerie() && item.numSerie() != null && !item.numSerie().isBlank()) {
            serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                            estoque.getId(), material.getId(), item.numSerie(), CondicaoMaterial.SUCATA)
                    .ifPresentOrElse(serie -> {
                        serie.liberarReservas();
                        serie.desativar();
                        serieRepository.save(serie);
                    }, () -> log.info("Série {} estornada da execução {} não constava como sucata no estoque da equipe.",
                            item.numSerie(), idExecucao));
        }
    }

    /**
     * Aplicação em campo consome saldo NOVO da equipe. O lançamento nunca é recusado:
     * se faltar saldo, o débito é permissivo e o saldo da equipe fica negativo. O
     * almoxarifado vê esse saldo negativo como aviso ao entregar material à equipe.
     */
    private void processarAplicacao(UUID idExecucao, Estoque estoque, Material material,
                                    ExecucaoConcluidaEvent.ItemMaterialEvent item, UUID idOperador) {
        // 1. Registrar Movimentação (Baixa por Aplicação em Campo - Somente material NOVO)
        MovimentacaoEstoque mov = new MovimentacaoEstoque(
                estoque, null, material, item.quantidade(),
                MovimentacaoEstoque.TipoMovimentacao.APLICACAO, CondicaoMaterial.NOVO, idOperador, idExecucao, item.numSerie()
        );
        movimentacaoRepository.save(mov);

        // 2. Decrementar Saldo da Equipe (débito permissivo: pode ficar negativo)
        debitoSaldoService.debitar(estoque, material, CondicaoMaterial.NOVO, item.quantidade());

        // 3. Atualizar Saldo Serial (Se aplicável)
        if (material.isDetalhaSerie()) {
            serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                            estoque.getId(), material.getId(), item.numSerie(), CondicaoMaterial.NOVO)
                    .ifPresentOrElse(serie -> {
                        // Aplicação em campo é fato consumado: nenhuma reserva impede o registro.
                        serie.liberarReservas();
                        serie.desativar();
                        serieRepository.save(serie);
                    }, () -> log.info("Série {} aplicada na execução {} não constava no estoque da equipe; saldo debitado.",
                            item.numSerie(), idExecucao));
        }
    }

    /**
     * Retirada de campo: o material que estava instalado em campo vai para o veículo
     * da equipe e entra no estoque dela como SUCATA. A equipe devolve ao almoxarifado
     * quando puder, pelo fluxo normal de devolução.
     */
    private void processarRetirada(UUID idExecucao, Estoque estoque, Material material,
                                   ExecucaoConcluidaEvent.ItemMaterialEvent item, UUID idOperador) {
        // 1. Registrar Movimentação (Entrada por Retirada de Campo - Sempre gera SUCATA)
        MovimentacaoEstoque mov = new MovimentacaoEstoque(
                null, estoque, material, item.quantidade(),
                MovimentacaoEstoque.TipoMovimentacao.RETIRADA, CondicaoMaterial.SUCATA, idOperador, idExecucao, item.numSerie()
        );
        movimentacaoRepository.save(mov);

        // 2. Incrementar Saldo da Equipe (Material retirado entra como SUCATA)
        Saldo saldo = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(estoque.getId(), material.getId(), CondicaoMaterial.SUCATA)
                .orElseGet(() -> new Saldo(estoque, material, CondicaoMaterial.SUCATA));

        saldo.adicionarQuantidade(item.quantidade());
        saldoRepository.save(saldo);

        // 3. Atualizar Saldo Serial (Se aplicável)
        if (material.isDetalhaSerie() && item.numSerie() != null && !item.numSerie().isBlank()) {
            if (serieRepository.findByNumSerieAndAtivoTrue(item.numSerie()).isPresent()) {
                // A série já está ativa em outro estoque: preserva o lançamento (a
                // movimentação acima registra a série) em vez de falhar o processamento.
                log.warn("Série {} retirada na execução {} já consta ativa em outro estoque; saldo serial não duplicado.",
                        item.numSerie(), idExecucao);
            } else {
                serieRepository.save(new SaldoSerie(estoque, material, item.numSerie(), CondicaoMaterial.SUCATA));
            }
        }
    }
}
