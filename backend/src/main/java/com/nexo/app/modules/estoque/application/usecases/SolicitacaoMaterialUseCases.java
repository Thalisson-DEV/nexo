package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.SolicitacaoMaterialApi;
import com.nexo.app.modules.estoque.application.mappers.SolicitacaoMapper;
import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.AlteracoesSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.services.ConsultaOperacoesSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.PoliticaAcoesPermitidasSolicitacao;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.*;
import com.nexo.app.modules.estoque.infrastructure.persistence.*;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialAprovadaEvent;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialRejeitadaEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

@Service
@RequiredArgsConstructor
public class SolicitacaoMaterialUseCases implements SolicitacaoMaterialApi {

    private final SolicitacaoMaterialRepository repository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository serieRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SolicitacaoMapper mapper;
    private final MotivoRejeicaoUseCases motivoRejeicaoUseCases;
    private final ApplicationEventPublisher eventPublisher;
    private final OperacaoSolicitacaoMaterialRepository operacaoSolicitacaoRepository;
    private final PoliticaAcoesPermitidasSolicitacao politicaAcoesPermitidas;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final AlteracoesSolicitacaoComercialService alteracoesComerciais;
    private final ConsultaOperacoesSolicitacaoComercialService operacoesComerciais;
    private final EquipeApi equipeApi;
    private final ColaboradorApi colaboradorApi;
    private final DebitoSaldoService debitoSaldoService;

    @Transactional
    public UUID criar(SolicitacaoRequest request, UUID idRequisitante) {
        SolicitacaoMaterial solicitacao = construirSolicitacao(request, idRequisitante);
        solicitacao = repository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.SOLICITACAO_CRIADA,
                PayloadEventoSolicitacao.semAlteracoes(
                        "CRIAR_SOLICITACAO_COMERCIAL",
                        Map.of("direta", false, "status", solicitacao.getStatus().name())),
                contextoUsuario(idRequisitante, null));
        return solicitacao.getId();
    }

    @Transactional
    public UUID criarDireta(SolicitacaoRequest request, UUID idRequisitante, UUID idAprovador) {
        SolicitacaoMaterial solicitacao = construirSolicitacao(request, idRequisitante);
        solicitacao.aprovar(idAprovador);
        solicitacao = repository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.SOLICITACAO_CRIADA,
                PayloadEventoSolicitacao.semAlteracoes(
                        "CRIAR_SOLICITACAO_COMERCIAL_DIRETA",
                        Map.of("direta", true, "status", solicitacao.getStatus().name())),
                contextoUsuario(idAprovador, null));
        return solicitacao.getId();
    }

    @Transactional(readOnly = true)
    public PageResponse<SolicitacaoSummaryResponse> listar(SolicitacaoFilter filter, Pageable pageable) {
        Pageable paginaEstavel = paginaEstavel(pageable);
        return PageResponse.fromPage(
                        repository.findAll(SolicitacaoSpecification.filterBy(filter), paginaEstavel)
                        .map(solicitacao -> {
                            SolicitacaoSummaryResponse resumo = mapper.toSummaryResponse(solicitacao);
                            var acoesPermitidas = politicaAcoesPermitidas.calcular(solicitacao);
                            resumo.setAcoesPermitidas(acoesPermitidas);
                            if (solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
                                resumo.setTecnico(DetalhesTecnicosSolicitacaoResponse.de(
                                        solicitacao,
                                        List.of(solicitacao),
                                        operacaoSolicitacaoRepository
                                                .findAllBySolicitacaoIdOrderByRealizadaEmAscIdAsc(solicitacao.getId()),
                                        acoesPermitidas));
                            }
                            return resumo;
                        })
        );
    }

    private Pageable paginaEstavel(Pageable pageable) {
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("dataSolicitacao"));
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Order.desc("id")));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    @Transactional(readOnly = true)
    public SolicitacaoResponse buscarPorIdCompleto(UUID id) {
        return repository.findById(id)
                .map(solicitacao -> {
                    var acoesPermitidas = politicaAcoesPermitidas.calcular(solicitacao);
                    SolicitacaoResponse resposta = mapper.toResponse(solicitacao)
                            .comAcoesPermitidas(acoesPermitidas);
                    if (solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
                        return resposta.comTecnico(DetalhesTecnicosSolicitacaoResponse.de(
                                solicitacao,
                                List.of(solicitacao),
                                operacaoSolicitacaoRepository
                                        .findAllBySolicitacaoIdOrderByRealizadaEmAscIdAsc(solicitacao.getId()),
                                acoesPermitidas));
                    }
                    return resposta.comOperacoes(operacoesComerciais.listar(solicitacao));
                })
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));
    }

    @Transactional
    public void aprovar(UUID solicitacaoId, UUID idAprovador, AprovarSolicitacaoRequest request) {
        SolicitacaoMaterial solicitacao = buscarEntidadePorId(solicitacaoId);
        AlteracoesSolicitacaoComercialService.Snapshot estadoAnterior = alteracoesComerciais.capturar(solicitacao);
        
        if (request != null) {
            if (request.idEstoqueOrigem() != null) {
                solicitacao.trocarEstoqueOrigemComercial(estoqueRepository.findById(request.idEstoqueOrigem())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de origem não encontrado")));
            }
            if (request.dataPrevista() != null) {
                solicitacao.reagendarSaqueComercial(request.dataPrevista());
            }

            // 1. Remoções
            if (request.itensRemovidos() != null) {
                request.itensRemovidos().forEach(solicitacao::removerItem);
            }

            // 2. Edições de Quantidade
            if (request.itensEditados() != null) {
                for (AprovarSolicitacaoRequest.ItemEdicaoAprovacaoRequest edicao : request.itensEditados()) {
                    ItemSolicitacao item = buscarItemNaSolicitacao(solicitacao, edicao.idItemSolicitacao());
                    item.atualizarQuantidade(edicao.novaQuantidade());
                }
            }

            // 3. Adições
            if (request.itensAdicionados() != null) {
                for (AprovarSolicitacaoRequest.NovoItemAprovacaoRequest novo : request.itensAdicionados()) {
                    Material material = materialRepository.findById(novo.idMaterial())
                            .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + novo.idMaterial()));
                    solicitacao.adicionarItem(material, novo.quantidade());
                }
            }
        }

        solicitacao.aprovar(idAprovador);
        repository.save(solicitacao);

        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.APROVACAO_CONFIRMADA,
                new PayloadEventoSolicitacao(
                        "APROVAR_SOLICITACAO_COMERCIAL",
                        alteracoesComerciais.comparar(estadoAnterior, solicitacao),
                        Map.of("status", solicitacao.getStatus().name())),
                contextoUsuario(idAprovador, null));

        // Só aqui, e não em criarDireta: naquele caminho a requisição nasce aprovada
        // porque o material já está sendo entregue no balcão, e convocar o
        // almoxarifado para separar o que ele tem em mãos seria ruído.
        eventPublisher.publishEvent(new SolicitacaoMaterialAprovadaEvent(
                solicitacao.getId(),
                nomeDoEstoque(solicitacao.getEstoqueOrigem()),
                nomeDoEstoque(solicitacao.getEstoqueDestino()),
                solicitacao.getItens().size(),
                solicitacao.getIdRequisitante(),
                idAprovador));
    }

    @Transactional
    public void rejeitar(UUID solicitacaoId, RejeitarSolicitacaoRequest request) {
        SolicitacaoMaterial solicitacao = buscarEntidadePorId(solicitacaoId);
        AlteracoesSolicitacaoComercialService.Snapshot estadoAnterior = alteracoesComerciais.capturar(solicitacao);
        exigirAutoridadeParaRejeitar(solicitacao);
        MotivoRejeicao motivo = motivoRejeicaoUseCases.buscarEntidadePorId(request.idMotivoRejeicao());
        UUID idAutor = SecurityUtils.getUsuarioLogadoId();
        solicitacao.rejeitar(motivo, request.justificativa());
        repository.save(solicitacao);

        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.REJEICAO_CONFIRMADA,
                new PayloadEventoSolicitacao(
                        solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO
                                ? "REJEITAR_SOLICITACAO_TECNICA"
                                : "REJEITAR_SOLICITACAO_COMERCIAL",
                        alteracoesComerciais.comparar(estadoAnterior, solicitacao),
                        Map.of("status", solicitacao.getStatus().name())),
                contextoUsuario(idAutor, solicitacao.getJustificativaRejeicao()));

        eventPublisher.publishEvent(new SolicitacaoMaterialRejeitadaEvent(
                solicitacao.getId(),
                solicitacao.getEstoqueOrigem() != null
                        ? nomeDoEstoque(solicitacao.getEstoqueOrigem())
                        : nomeDoEstoque(solicitacao.getAlmoxarifadoAtendimento()),
                nomeDoEstoque(solicitacao.getEstoqueDestino()),
                motivo.getNome(),
                solicitacao.getJustificativaRejeicao(),
                solicitacao.getIdRequisitante(),
                idAutor));
    }

    /**
     * Comercial: reprovar a solicitação pendente é decisão de quem aprova; rejeitar a já aprovada
     * (ex.: sem material para atender) é do almoxarifado. Técnica: segue o planejamento.
     */
    private static void exigirAutoridadeParaRejeitar(SolicitacaoMaterial solicitacao) {
        String exigida;
        if (solicitacao.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            if (SecurityUtils.possuiAutoridade("estoque:solicitacao:plan")) return;
            exigida = "estoque:solicitacao:write";
        } else {
            exigida = solicitacao.getStatus() == SolicitacaoMaterial.StatusSolicitacao.PENDENTE
                    ? "estoque:solicitacao:approve"
                    : "estoque:solicitacao:write";
        }
        if (!SecurityUtils.possuiAutoridade(exigida)) {
            throw new AuthorizationDeniedException("Sem permissão para rejeitar a solicitação neste status.");
        }
    }

    private static String nomeDoEstoque(Estoque estoque) {
        return estoque == null ? null : estoque.getNomeIdentificador();
    }

    private static ContextoRegistroEventoSolicitacao contextoUsuario(UUID idUsuario, String justificativa) {
        return new ContextoRegistroEventoSolicitacao(
                idUsuario,
                "USUARIO " + idUsuario,
                OrigemComandoTecnico.UI,
                justificativa);
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID registrarEntrega(UUID solicitacaoId, UUID idEntregador, EntregaSolicitacaoRequest request) {
        SolicitacaoMaterial solicitacao = buscarEntidadePorId(solicitacaoId);
        
        if (request == null) return null;

        UUID idOperacao = UUID.randomUUID();

        // 1. Processa remoções solicitadas no balcão (antes de qualquer entrega)
        if (request.itens() != null && request.itensRemovidos() != null) {
            request.itensRemovidos().forEach(solicitacao::removerItem);
        }

        // 2. Mapeia itens existentes
        Map<UUID, SolicitacaoMaterial.ItemEntregaInfo> entregas = new HashMap<>();
        if (request.itens() != null) {
            for (EntregaSolicitacaoRequest.ItemEntregaRequest itemReq : request.itens()) {
                // Se o item foi removido no passo anterior, ignora a tentativa de entrega
                boolean itemExiste = solicitacao.getItens().stream().anyMatch(i -> i.getId().equals(itemReq.idItemSolicitacao()));
                if (!itemExiste) continue;

                entregas.put(itemReq.idItemSolicitacao(), new SolicitacaoMaterial.ItemEntregaInfo(
                    itemReq.quantidade(),
                    itemReq.novaQuantidadeSolicitada(),
                    itemReq.numSerie()
                ));
                
                if (itemReq.quantidade() != null && itemReq.quantidade().compareTo(BigDecimal.ZERO) > 0) {
                    ItemSolicitacao item = buscarItemNaSolicitacao(solicitacao, itemReq.idItemSolicitacao());
                    processarMovimentacaoFisica(solicitacao, item.getMaterial(), itemReq.quantidade(), idEntregador, false, itemReq.numSerie(), idOperacao);
                }
            }
        }

        // 3. Mapeia itens adicionados no balcão (ad-hoc)
        List<SolicitacaoMaterial.NovoItemEntregaInfo> novosItens = new ArrayList<>();
        if (request.itensAdicionados() != null) {
            for (EntregaSolicitacaoRequest.NovoItemEntregaRequest novoReq : request.itensAdicionados()) {
                Material material = materialRepository.findById(novoReq.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + novoReq.idMaterial()));
                BigDecimal quantidadeSolicitada = novoReq.quantidadeSolicitada() != null
                    ? novoReq.quantidadeSolicitada()
                    : novoReq.quantidade();
                novosItens.add(new SolicitacaoMaterial.NovoItemEntregaInfo(
                    material, quantidadeSolicitada, novoReq.quantidade(), novoReq.numSerie()
                ));

                // Movimentação física imediata para o item ad-hoc
                processarMovimentacaoFisica(solicitacao, material, novoReq.quantidade(), idEntregador, false, novoReq.numSerie(), idOperacao);
            }
        }

        // 4. Delega para o domínio processar as regras de série e status
        solicitacao.registrarEntrega(idEntregador, request.assinaturaAlmox(), request.assinaturaEquipe(), entregas, novosItens);
        repository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.ENTREGA_CONFIRMADA,
                PayloadEventoSolicitacao.semAlteracoes(
                        "ENTREGAR_SOLICITACAO_COMERCIAL",
                        Map.of("idOperacao", idOperacao, "comprovantesRegistrados", true)),
                contextoUsuario(idEntregador, null));
        return idOperacao;
    }

    private ItemSolicitacao buscarItemNaSolicitacao(SolicitacaoMaterial solicitacao, UUID itemId) {
        return solicitacao.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item da solicitação não encontrado: " + itemId));
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID estornar(UUID solicitacaoId, UUID idOperador) {
        SolicitacaoMaterial solicitacao = buscarEntidadePorId(solicitacaoId);
        solicitacao.estornar();

        UUID idOperacao = UUID.randomUUID();

        for (ItemSolicitacao item : solicitacao.getItens()) {
            if (item.getQuantidadeEntregue().compareTo(BigDecimal.ZERO) > 0) {
                processarMovimentacaoFisica(solicitacao, item.getMaterial(), item.getQuantidadeEntregue(), idOperador, true, item.getNumSerie(), idOperacao);
            }
        }

        repository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.ESTORNO_CONFIRMADO,
                PayloadEventoSolicitacao.semAlteracoes(
                        "ESTORNAR_SOLICITACAO_COMERCIAL",
                        Map.of("idOperacao", idOperacao, "parcial", false)),
                contextoUsuario(idOperador, null));
        return idOperacao;
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID registrarEstornoParcial(UUID solicitacaoId, UUID idOperador, EstornoParcialRequest request) {
        SolicitacaoMaterial solicitacao = buscarEntidadePorId(solicitacaoId);
        
        UUID idOperacao = UUID.randomUUID();

        Map<UUID, BigDecimal> estornos = new HashMap<>();
        for (EstornoParcialRequest.ItemEstornoRequest itemReq : request.itens()) {
            ItemSolicitacao item = buscarItemNaSolicitacao(solicitacao, itemReq.idItemSolicitacao());
            
            // Registra a movimentação física (estorno inverte origem e destino)
            processarMovimentacaoFisica(solicitacao, item.getMaterial(), itemReq.quantidade(), idOperador, true, item.getNumSerie(), idOperacao);
            
            estornos.put(itemReq.idItemSolicitacao(), itemReq.quantidade());
        }

        solicitacao.registrarEstornoParcial(estornos);
        repository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao,
                TipoEventoSolicitacao.ESTORNO_CONFIRMADO,
                PayloadEventoSolicitacao.semAlteracoes(
                        "ESTORNAR_PARCIAL_SOLICITACAO_COMERCIAL",
                        Map.of("idOperacao", idOperacao, "parcial", true)),
                contextoUsuario(idOperador, null));
        return idOperacao;
    }

    private SolicitacaoMaterial construirSolicitacao(SolicitacaoRequest request, UUID idRequisitante) {
        validarDataPrevistaComercial(request.dataPrevista());
        Estoque estoqueOrigem = estoqueRepository.findById(request.idEstoqueOrigem())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de origem não encontrado"));
        validarEstoqueOrigemComercial(estoqueOrigem);

        UUID idEquipe = request.idEquipe();
        Estoque estoqueDestino = resolverEstoqueDestinoComercial(idEquipe, request.idEstoqueDestino());
        validarPlanejamentoSaqueComercial(
                idEquipe, request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()));

        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(
                estoqueOrigem, estoqueDestino, idRequisitante, request.dataPrevista(),
                idEquipe, request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()));
        solicitacao.definirObservacaoComercial(request.observacao());

        for (SolicitacaoRequest.ItemRequest itemReq : request.itens()) {
            Material material = materialRepository.findById(itemReq.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + itemReq.idMaterial()));
            solicitacao.adicionarItem(material, itemReq.quantidade());
        }
        return solicitacao;
    }

    private void validarEstoqueOrigemComercial(Estoque estoqueOrigem) {
        if (!estoqueOrigem.isAtivo()
                || estoqueOrigem.getTipoEstoque() == null
                || !"FISICO".equalsIgnoreCase(estoqueOrigem.getTipoEstoque().getNome())) {
            throw new InvarianteVioladaException(
                    "O estoque de origem da solicitacao comercial deve estar ativo e ser do tipo FISICO.");
        }
    }

    private Estoque resolverEstoqueDestinoComercial(UUID idEquipe, UUID idEstoqueDestinoLegado) {
        if (idEquipe == null) {
            throw new InvarianteVioladaException("A equipe responsavel pelo saque e obrigatoria.");
        }

        Estoque estoqueDestino = estoqueRepository.findByIdEquipe(idEquipe)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "A equipe selecionada nao possui estoque de campo vinculado. Regularize o cadastro da equipe."));

        if (!estoqueDestino.isAtivo() || !estoqueDestino.isTipoCampo()) {
            throw new InvarianteVioladaException(
                    "O estoque vinculado a equipe deve estar ativo e ser do tipo CAMPO.");
        }

        if (idEstoqueDestinoLegado != null && !estoqueDestino.getId().equals(idEstoqueDestinoLegado)) {
            throw new InvarianteVioladaException(
                    "O estoque de destino informado nao corresponde ao estoque de campo da equipe selecionada.");
        }
        return estoqueDestino;
    }

    private void validarDataPrevistaComercial(LocalDate dataPrevista) {
        if (dataPrevista == null) {
            throw new InvarianteVioladaException("A data prevista de saque e obrigatoria para a solicitacao comercial.");
        }
    }

    private void validarPlanejamentoSaqueComercial(
            UUID idEquipe, UUID idColaboradorSaque, boolean saqueForaEquipe) {
        if (idEquipe == null || equipeApi.buscarAtivaPorId(idEquipe).isEmpty()) {
            throw new InvarianteVioladaException("A equipe de saque deve existir e estar ativa.");
        }
        if (idColaboradorSaque == null) {
            return;
        }
        ColaboradorResponse colaborador = colaboradorApi.buscarPorIdResponse(idColaboradorSaque)
                .filter(ColaboradorResponse::ativo)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "O colaborador responsavel pelo saque deve existir e estar ativo."));
        if (!saqueForaEquipe && !equipeApi.isMembroAtivo(idEquipe, colaborador.id())) {
            throw new InvarianteVioladaException(
                    "O colaborador informado nao e um membro ativo da equipe de saque.");
        }
    }

    private void processarMovimentacaoFisica(SolicitacaoMaterial solicitacao, Material material, BigDecimal qtd, UUID idOperador, boolean isEstorno, String numSerie, UUID idOperacao) {
        Estoque origem = isEstorno ? solicitacao.getEstoqueDestino() : solicitacao.getEstoqueOrigem();
        Estoque destino = isEstorno ? solicitacao.getEstoqueOrigem() : solicitacao.getEstoqueDestino();

        debitoSaldoService.debitar(origem, material, CondicaoMaterial.NOVO, qtd);

        Saldo saldoDestino = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(destino.getId(), material.getId(), CondicaoMaterial.NOVO)
                .orElseGet(() -> new Saldo(destino, material, CondicaoMaterial.NOVO));
        saldoDestino.adicionarQuantidade(qtd);
        saldoRepository.save(saldoDestino);

        // Atualiza Saldo Serial (Se aplicável)
        if (material.isDetalhaSerie()) {
            SaldoSerie serie = serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(origem.getId(), material.getId(), numSerie, CondicaoMaterial.NOVO)
                    .orElseThrow(() -> new InvarianteVioladaException("Serial " + numSerie + " (NOVO) não localizado na origem (" + origem.getNomeIdentificador() + ")"));
            serie.cumprirReservaDa(solicitacao);
            serie.transferirPara(destino);
            serieRepository.save(serie);
        }

        MovimentacaoEstoque mov = new MovimentacaoEstoque(
                origem, destino, material, qtd, 
                isEstorno ? MovimentacaoEstoque.TipoMovimentacao.ESTORNO : MovimentacaoEstoque.TipoMovimentacao.ENTREGA, 
                CondicaoMaterial.NOVO, idOperador, idOperacao, numSerie
        );
        movimentacaoRepository.save(mov);
    }

    private SolicitacaoMaterial buscarEntidadePorId(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação de material não encontrada"));
    }
}
