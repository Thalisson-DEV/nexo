package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.application.dto.EntregaSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.EstornoParcialRequest;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

/** Operacoes fisicas legadas, exclusivas do contexto comercial. */
@Service
@RequiredArgsConstructor
public class OperacaoSolicitacaoComercialService {

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository serieRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final MaterialRepository materialRepository;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final AlteracoesSolicitacaoComercialService alteracoesComerciais;
    private final AssinaturaSolicitacaoMaterialService assinaturas;
    private final ColaboradorApi colaboradorApi;
    private final EquipeApi equipeApi;
    private final DebitoSaldoService debitoSaldoService;
    private final EstoqueRepository estoqueRepository;

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID registrarEntrega(UUID solicitacaoId, UUID idEntregador, EntregaSolicitacaoRequest request) {
        SolicitacaoMaterial solicitacao = buscarSolicitacaoComercial(solicitacaoId);
        if (request == null) {
            return null;
        }
        solicitacao.exigirSaldoAberto();
        AlteracoesSolicitacaoComercialService.Snapshot estadoAnterior = alteracoesComerciais.capturar(solicitacao);
        validarItensUnicosEntrega(request);
        validarResponsavelSaque(solicitacao, request);
        if (request.idEstoqueOrigem() != null) {
            solicitacao.trocarEstoqueOrigemComercial(estoqueRepository.findById(request.idEstoqueOrigem())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de origem nao encontrado")));
        }
        UUID idOperacao = UUID.randomUUID();
        if (request.itens() != null && request.itensRemovidos() != null) {
            request.itensRemovidos().forEach(solicitacao::removerItem);
        }

        Map<UUID, SolicitacaoMaterial.ItemEntregaInfo> entregas = new HashMap<>();
        if (request.itens() != null) {
            for (EntregaSolicitacaoRequest.ItemEntregaRequest itemReq : request.itens()) {
                boolean itemExiste = solicitacao.getItens().stream()
                        .anyMatch(item -> item.getId().equals(itemReq.idItemSolicitacao()));
                if (!itemExiste) {
                    continue;
                }
                entregas.put(itemReq.idItemSolicitacao(), new SolicitacaoMaterial.ItemEntregaInfo(
                        itemReq.quantidade(), itemReq.novaQuantidadeSolicitada(), itemReq.numSerie()));
                if (itemReq.quantidade() != null && itemReq.quantidade().signum() > 0) {
                    ItemSolicitacao item = buscarItem(solicitacao, itemReq.idItemSolicitacao());
                    movimentar(solicitacao, item.getMaterial(), itemReq.quantidade(), idEntregador,
                            false, itemReq.numSerie(), idOperacao);
                }
            }
        }

        List<SolicitacaoMaterial.NovoItemEntregaInfo> novosItens = new ArrayList<>();
        if (request.itensAdicionados() != null) {
            for (EntregaSolicitacaoRequest.NovoItemEntregaRequest novoReq : request.itensAdicionados()) {
                Material material = materialRepository.findById(novoReq.idMaterial())
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Material nao encontrado: " + novoReq.idMaterial()));
                BigDecimal quantidadeSolicitada = novoReq.quantidadeSolicitada() != null
                        ? novoReq.quantidadeSolicitada()
                        : novoReq.quantidade();
                novosItens.add(new SolicitacaoMaterial.NovoItemEntregaInfo(
                        material, quantidadeSolicitada, novoReq.quantidade(), novoReq.numSerie()));
                movimentar(solicitacao, material, novoReq.quantidade(), idEntregador,
                        false, novoReq.numSerie(), idOperacao);
            }
        }

        var assinaturasArmazenadas = prepararAssinaturas(
                solicitacaoId, request.assinaturaAlmox(), request.assinaturaEquipe());
        if (request.idColaboradorSaque() != null) {
            solicitacao.registrarResponsavelSaqueComercial(
                    request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()));
        }
        solicitacao.registrarEntrega(idEntregador, assinaturasArmazenadas.almox(),
                assinaturasArmazenadas.equipe(), entregas, novosItens);
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.ENTREGA_CONFIRMADA,
                new PayloadEventoSolicitacao(
                        "ENTREGAR_SOLICITACAO_COMERCIAL",
                        alteracoesComerciais.comparar(estadoAnterior, solicitacao),
                        Map.of("idOperacao", idOperacao, "comprovantesRegistrados", true)),
                contextoUsuario(idEntregador));
        return idOperacao;
    }

    private void validarResponsavelSaque(
            SolicitacaoMaterial solicitacao, EntregaSolicitacaoRequest request) {
        UUID idColaborador = request.idColaboradorSaque();
        if (idColaborador == null) {
            return;
        }
        var colaborador = colaboradorApi.buscarPorIdResponse(idColaborador).orElse(null);
        if (colaborador == null || !colaborador.ativo()) {
            throw new InvarianteVioladaException(
                    "O colaborador responsavel pelo saque deve existir e estar ativo.");
        }
        UUID idEquipe = solicitacao.getIdEquipeSaque();
        if (!Boolean.TRUE.equals(request.saqueForaEquipe())) {
            if (idEquipe == null) {
                throw new InvarianteVioladaException(
                        "O estoque de destino nao possui equipe; habilite o saque fora da equipe.");
            }
            if (!equipeApi.isMembroAtivo(idEquipe, idColaborador)) {
                throw new InvarianteVioladaException(
                        "O colaborador informado nao e membro ativo da equipe de destino.");
            }
        }
    }

    private AssinaturaSolicitacaoMaterialService.AssinaturasArmazenadas prepararAssinaturas(
            UUID idSolicitacao, String almox, String equipe) {
        if ((almox != null && almox.startsWith("data:image/"))
                || (equipe != null && equipe.startsWith("data:image/"))) {
            return assinaturas.armazenar(idSolicitacao, almox, equipe);
        }
        return new AssinaturaSolicitacaoMaterialService.AssinaturasArmazenadas(almox, equipe);
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID estornar(UUID solicitacaoId, UUID idOperador) {
        SolicitacaoMaterial solicitacao = buscarSolicitacaoComercial(solicitacaoId);
        AlteracoesSolicitacaoComercialService.Snapshot estadoAnterior = alteracoesComerciais.capturar(solicitacao);
        UUID idOperacao = UUID.randomUUID();
        for (ItemSolicitacao item : solicitacao.getItens()) {
            BigDecimal quantidadeLiquida = item.getQuantidadeEntregaLiquida();
            if (quantidadeLiquida.signum() > 0) {
                movimentar(solicitacao, item.getMaterial(), quantidadeLiquida, idOperador,
                        true, item.getNumSerie(), idOperacao);
            }
        }
        solicitacao.estornar();
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.ESTORNO_CONFIRMADO,
                new PayloadEventoSolicitacao(
                        "ESTORNAR_SOLICITACAO_COMERCIAL",
                        alteracoesComerciais.comparar(estadoAnterior, solicitacao),
                        Map.of("idOperacao", idOperacao, "parcial", false)),
                contextoUsuario(idOperador));
        return idOperacao;
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID registrarEstornoParcial(
            UUID solicitacaoId,
            UUID idOperador,
            EstornoParcialRequest request
    ) {
        SolicitacaoMaterial solicitacao = buscarSolicitacaoComercial(solicitacaoId);
        AlteracoesSolicitacaoComercialService.Snapshot estadoAnterior = alteracoesComerciais.capturar(solicitacao);
        validarItensUnicosEstorno(request);
        UUID idOperacao = UUID.randomUUID();
        Map<UUID, BigDecimal> estornos = new HashMap<>();
        for (EstornoParcialRequest.ItemEstornoRequest itemReq : request.itens()) {
            ItemSolicitacao item = buscarItem(solicitacao, itemReq.idItemSolicitacao());
            movimentar(solicitacao, item.getMaterial(), itemReq.quantidade(), idOperador,
                    true, item.getNumSerie(), idOperacao);
            estornos.put(itemReq.idItemSolicitacao(), itemReq.quantidade());
        }
        solicitacao.registrarEstornoParcial(estornos);
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.ESTORNO_CONFIRMADO,
                new PayloadEventoSolicitacao(
                        "ESTORNAR_PARCIAL_SOLICITACAO_COMERCIAL",
                        alteracoesComerciais.comparar(estadoAnterior, solicitacao),
                        Map.of("idOperacao", idOperacao, "parcial", true)),
                contextoUsuario(idOperador));
        return idOperacao;
    }

    private void validarItensUnicosEntrega(EntregaSolicitacaoRequest request) {
        Set<UUID> idsInformados = new HashSet<>();
        if (request.itens() != null) {
            request.itens().forEach(item -> registrarIdUnico(
                    idsInformados, item.idItemSolicitacao(), "na entrega"));
        }
        if (request.itensRemovidos() != null) {
            request.itensRemovidos().forEach(id -> registrarIdUnico(idsInformados, id, "na entrega"));
        }
    }

    private void validarItensUnicosEstorno(EstornoParcialRequest request) {
        Set<UUID> idsInformados = new HashSet<>();
        request.itens().forEach(item -> registrarIdUnico(
                idsInformados, item.idItemSolicitacao(), "no estorno parcial"));
    }

    private void registrarIdUnico(Set<UUID> idsInformados, UUID idItem, String contexto) {
        if (idItem != null && !idsInformados.add(idItem)) {
            throw new InvarianteVioladaException(
                    "O item " + idItem + " foi informado mais de uma vez " + contexto + ".");
        }
    }

    private void movimentar(
            SolicitacaoMaterial solicitacao,
            Material material,
            BigDecimal quantidade,
            UUID idOperador,
            boolean estorno,
            String numeroSerie,
            UUID idOperacao
    ) {
        Estoque origem = estorno ? solicitacao.getEstoqueDestino() : solicitacao.getEstoqueOrigem();
        Estoque destino = estorno ? solicitacao.getEstoqueOrigem() : solicitacao.getEstoqueDestino();
        debitoSaldoService.debitar(origem, material, CondicaoMaterial.NOVO, quantidade);

        Saldo saldoDestino = saldoRepository
                .findByEstoqueIdAndMaterialIdAndCondicao(destino.getId(), material.getId(), CondicaoMaterial.NOVO)
                .orElseGet(() -> new Saldo(destino, material, CondicaoMaterial.NOVO));
        saldoDestino.adicionarQuantidade(quantidade);
        saldoRepository.save(saldoDestino);

        if (material.isDetalhaSerie()) {
            SaldoSerie serie = serieRepository
                    .findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                            origem.getId(), material.getId(), numeroSerie, CondicaoMaterial.NOVO)
                    .orElseThrow(() -> new InvarianteVioladaException(
                            "Serial " + numeroSerie + " (NOVO) nao localizado na origem ("
                                    + origem.getNomeIdentificador() + ")"));
            // A série reservada para esta solicitação sai agora; a de outra continua presa.
            serie.cumprirReservaDa(solicitacao);
            serie.transferirPara(destino);
            serieRepository.save(serie);
        }

        movimentacaoRepository.save(MovimentacaoEstoque.criarComercial(
                solicitacao, origem, destino, material, quantidade,
                estorno ? MovimentacaoEstoque.TipoMovimentacao.ESTORNO
                        : MovimentacaoEstoque.TipoMovimentacao.ENTREGA,
                idOperador, idOperacao, numeroSerie));
    }

    private ItemSolicitacao buscarItem(SolicitacaoMaterial solicitacao, UUID idItem) {
        return solicitacao.getItens().stream()
                .filter(item -> item.getId().equals(idItem))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Item da solicitacao nao encontrado: " + idItem));
    }

    private static ContextoRegistroEventoSolicitacao contextoUsuario(UUID idUsuario) {
        return new ContextoRegistroEventoSolicitacao(
                idUsuario, "USUARIO " + idUsuario, OrigemComandoTecnico.UI, null);
    }

    private SolicitacaoMaterial buscarSolicitacaoComercial(UUID id) {
        SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Solicitacao de material nao encontrada"));
        if (solicitacao.getContexto() != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException(
                    "Use as operacoes tecnicas para entrega, devolucao ou estorno tecnico.");
        }
        return solicitacao;
    }
}
