package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.MovimentacaoEstoqueApi;
import com.nexo.app.modules.estoque.application.mappers.EstoqueMapper;
import com.nexo.app.modules.estoque.application.mappers.MovimentacaoEstoqueMapper;
import com.nexo.app.modules.estoque.application.services.AssinaturaTransferenciaService;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.NormalizadorOcorrenciaTecnica;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.infrastructure.persistence.*;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.ReferenciaUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

@Service
@RequiredArgsConstructor
public class MovimentacaoEstoqueUseCases implements MovimentacaoEstoqueApi {

    private final MovimentacaoEstoqueRepository repository;
    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository serieRepository;
    private final MovimentacaoEstoqueMapper mapper;
    private final EstoqueMapper estoqueMapper;
    private final UsuarioApi usuarioApi;
    private final DebitoSaldoService debitoSaldoService;
    private final EstoqueCampoEquipeProvisioningService estoqueCampoEquipeService;
    private final AssinaturaTransferenciaService assinaturaTransferenciaService;

    private static final Map<String, Integer> MESES_MAP = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEV", 2), Map.entry("MAR", 3),
            Map.entry("ABR", 4), Map.entry("MAI", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AGO", 8), Map.entry("SET", 9),
            Map.entry("OUT", 10), Map.entry("NOV", 11), Map.entry("DEZ", 12)
    );

    @Transactional(readOnly = true)
    public PageResponse<MovimentacaoOperacaoSummaryResponse> listar(MovimentacaoEstoqueFilter filter, Pageable pageable) {
        Pageable pageableQuery = PageRequest.of(
                pageable.getPageNumber(), 
                pageable.getPageSize()
        );

        String numSerieLike = (filter.numSerie() != null && !filter.numSerie().isBlank())
                ? "%" + filter.numSerie().trim().toLowerCase() + "%"
                : null;

        String buscaGlobalLike = (filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank())
                ? "%" + filter.buscaGlobal().trim() + "%"
                : null;

        OffsetDateTime dataInicioRef = ReferenciaUtils.converterParaDataInicio(filter.referencia());
        OffsetDateTime dataFimRef = ReferenciaUtils.converterParaDataFim(filter.referencia());

        String sortDir = "DESC";
        String sortCol = "dataHora";

        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            sortDir = order.getDirection().name();
            String property = order.getProperty();
            
            sortCol = switch (property) {
                case "tipo" -> "tipo";
                case "operador", "operadorNome", "idUsuarioOperador" -> "operador";
                case "id", "idReferenciaOperacao" -> "id";
                default -> "dataHora";
            };
        }

        Page<UUID> pageIds = possuiFiltrosTecnicos(filter)
                ? repository.findUniqueOperacoesIds(
                        filter.idEstoque(), filter.idMaterial(), condicao(filter), tipo(filter),
                        filter.dataInicio(), filter.dataFim(),
                        dataInicioRef, dataFimRef, filter.idUsuarioOperador(), numSerieLike, filter.idBaseOrigem(),
                        filter.idBaseDestino(), buscaGlobalLike, contexto(filter),
                        filter.idSolicitacao(), tipoTecnico(filter), tipoObra(filter), filter.idObra(),
                        normalizarOcorrencia(filter.ocorrencia()), filter.idEquipe(), filter.idReserva(),
                        numeroReserva(filter.numeroReserva()), filter.idAgenteReserva(), statusSolicitacao(filter),
                        statusSeparacao(filter), sortCol, sortDir, pageableQuery)
                : repository.findUniqueOperacoesIds(
                        filter.idEstoque(), filter.idMaterial(), tipo(filter), filter.dataInicio(), filter.dataFim(),
                        dataInicioRef, dataFimRef, filter.idUsuarioOperador(), numSerieLike, filter.idBaseOrigem(),
                        filter.idBaseDestino(), buscaGlobalLike, sortCol, sortDir, pageableQuery);

        if (pageIds.isEmpty()) {
            return PageResponse.fromPage(Page.empty(pageable));
        }

        List<MovimentacaoEstoque> bases = repository.findAllByIdReferenciaOperacaoInOrderByDataHoraDesc(pageIds.getContent());
        
        Map<UUID, MovimentacaoEstoque> basePorIdRef = bases.stream()
                .collect(Collectors.toMap(
                        MovimentacaoEstoque::getIdReferenciaOperacao,
                        m -> m,
                        (existente, novo) -> existente
                ));

        List<MovimentacaoOperacaoSummaryResponse> operacoes = pageIds.getContent().stream()
                .map(idRef -> {
                    MovimentacaoEstoque base = basePorIdRef.get(idRef);
                    if (base == null) return null;

                    MovimentacaoOperacaoSummaryResponse opResponse = mapper.toOperacaoSummaryResponse(base);
                    opResponse.setOperadorNome(usuarioApi.buscarNomePorId(base.getIdUsuarioOperador()).orElse("Desconhecido"));
                    
                    return opResponse;
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return PageResponse.fromPage(new PageImpl<>(operacoes, pageable, pageIds.getTotalElements()));
    }

    private String normalizarOcorrencia(String ocorrencia) {
        return ocorrencia == null || ocorrencia.isBlank() ? null : NormalizadorOcorrenciaTecnica.normalizar(ocorrencia);
    }

    private String numeroReserva(String numeroReserva) {
        if (numeroReserva == null || numeroReserva.isBlank()) {
            return null;
        }
        if (!numeroReserva.trim().matches("\\d+")) {
            throw new InvarianteVioladaException("Numero de reserva deve conter apenas digitos.");
        }
        return numeroReserva.trim();
    }

    private boolean possuiFiltrosTecnicos(MovimentacaoEstoqueFilter filter) {
        return filter.condicao() != null || filter.contexto() != null
                || filter.idSolicitacao() != null
                || filter.tipoTecnico() != null || filter.tipoObra() != null || filter.idObra() != null
                || filter.ocorrencia() != null || filter.idEquipe() != null || filter.idReserva() != null
                || filter.numeroReserva() != null || filter.idAgenteReserva() != null
                || filter.statusSolicitacao() != null || filter.statusSeparacao() != null;
    }

    private String tipo(MovimentacaoEstoqueFilter filter) {
        return filter.tipo() == null ? null : filter.tipo().name();
    }

    private String condicao(MovimentacaoEstoqueFilter filter) {
        return filter.condicao() == null ? null : filter.condicao().name();
    }

    private String contexto(MovimentacaoEstoqueFilter filter) {
        return filter.contexto() == null ? null : filter.contexto().name();
    }

    private String tipoTecnico(MovimentacaoEstoqueFilter filter) {
        return filter.tipoTecnico() == null ? null : filter.tipoTecnico().getCodigo();
    }

    private String tipoObra(MovimentacaoEstoqueFilter filter) {
        return filter.tipoObra() == null ? null : filter.tipoObra().name();
    }

    private String statusSolicitacao(MovimentacaoEstoqueFilter filter) {
        return filter.statusSolicitacao() == null ? null : filter.statusSolicitacao().name();
    }

    private String statusSeparacao(MovimentacaoEstoqueFilter filter) {
        return filter.statusSeparacao() == null ? null : filter.statusSeparacao().name();
    }

    @Transactional(readOnly = true)
    public MovimentacaoOperacaoResponse buscarOperacaoPorIdReferencia(UUID idReferencia) {
        List<MovimentacaoEstoque> movimentacoes = repository.findAllByIdReferenciaOperacaoInOrderByDataHoraDesc(List.of(idReferencia));
        
        if (movimentacoes.isEmpty()) {
            throw new RecursoNaoEncontradoException("Operação não encontrada");
        }

        MovimentacaoEstoque base = movimentacoes.get(0);
        String operadorNome = usuarioApi.buscarNomePorId(base.getIdUsuarioOperador()).orElse("Desconhecido");

        List<MovimentacaoItemResponse> itens = movimentacoes.stream()
                .map(mapper::toItemResponse)
                .toList();

        String assinaturaEntregador = normalizarTipoAssinatura(base.getAssinaturaEntregador());
        String assinaturaRecebedor = normalizarTipoAssinatura(base.getAssinaturaRecebedor());

        return new MovimentacaoOperacaoResponse(
                base.getIdReferenciaOperacao(),
                base.getTipo(),
                base.getDataHora(),
                operadorNome,
                estoqueMapper.toResponse(base.getEstoqueOrigem()),
                estoqueMapper.toResponse(base.getEstoqueDestino()),
                base.getSolicitacao() == null ? null : base.getSolicitacao().getContexto(),
                base.getSolicitacao() == null ? null : base.getSolicitacao().getId(),
                base.getTipoTecnico(),
                base.getTipoObraSnapshot(),
                base.getOcorrencia(),
                base.getReserva() == null ? null : base.getReserva().getId(),
                base.getOperacaoSolicitacao() == null ? null : base.getOperacaoSolicitacao().getId(),
                base.getIdObra(),
                base.getIdEquipe(),
                itens,
                assinaturaEntregador,
                assinaturaRecebedor
        );
    }

    private String normalizarTipoAssinatura(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        if ("FISICA".equalsIgnoreCase(valor)) {
            return "FISICA";
        }
        return "DIGITAL";
    }

    @Transactional(readOnly = true)
    public MovimentacaoEstoqueResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentação não encontrada"));
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public void entradaLote(MovimentacaoBulkRequest request, UUID idOperador) {
        Estoque destino = estoqueRepository.findById(request.idEstoqueDestino())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de destino não encontrado"));

        UUID idReferencia = UUID.randomUUID();
        OffsetDateTime dataHora = OffsetDateTime.now();

        for (MovimentacaoBulkRequest.ItemMovimentacaoRequest itemReq : request.itens()) {
            Material material = materialRepository.findById(itemReq.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + itemReq.idMaterial()));

            CondicaoMaterial condicao = itemReq.condicao() != null ? itemReq.condicao() : CondicaoMaterial.NOVO;

            MovimentacaoEstoque mov = new MovimentacaoEstoque(
                    null, destino, material, itemReq.quantidade(),
                    MovimentacaoEstoque.TipoMovimentacao.ENTRADA_AVULSA, condicao, idOperador, idReferencia, itemReq.numSerie(), dataHora
            );
            repository.save(mov);

            Saldo saldo = obterOuCriarSaldo(destino, material, condicao);
            saldo.adicionarQuantidade(itemReq.quantidade());
            saldoRepository.save(saldo);

            if (material.isDetalhaSerie()) {
                if (serieRepository.findByNumSerieAndAtivoTrue(itemReq.numSerie()).isPresent()) {
                    throw new InvarianteVioladaException("O número de série " + itemReq.numSerie() + " já consta no sistema.");
                }
                serieRepository.save(new SaldoSerie(destino, material, itemReq.numSerie(), condicao));
            }
        }
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public void baixaLote(MovimentacaoBulkRequest request, UUID idOperador) {
        Estoque origem = estoqueRepository.findById(request.idEstoqueOrigem())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de origem não encontrado"));

        UUID idReferencia = UUID.randomUUID();
        OffsetDateTime dataHora = OffsetDateTime.now();

        for (MovimentacaoBulkRequest.ItemMovimentacaoRequest itemReq : request.itens()) {
            Material material = materialRepository.findById(itemReq.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + itemReq.idMaterial()));

            CondicaoMaterial condicao = itemReq.condicao() != null ? itemReq.condicao() : CondicaoMaterial.NOVO;

            MovimentacaoEstoque mov = new MovimentacaoEstoque(
                    origem, null, material, itemReq.quantidade(),
                    MovimentacaoEstoque.TipoMovimentacao.BAIXA_AVULSA, condicao, idOperador, idReferencia, itemReq.numSerie(), dataHora
            );
            repository.save(mov);

            debitoSaldoService.debitar(origem, material, condicao, itemReq.quantidade());

            if (material.isDetalhaSerie()) {
                SaldoSerie serie = serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(origem.getId(), material.getId(), itemReq.numSerie(), condicao)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Serial " + itemReq.numSerie() + " (" + condicao + ") não localizado no estoque de origem."));
                serie.desativar();
                serieRepository.save(serie);
            }
        }
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID transferenciaLote(MovimentacaoBulkRequest request, UUID idOperador) {
        Estoque origem = estoqueRepository.findById(request.idEstoqueOrigem())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de origem não encontrado"));
        Estoque destino = estoqueRepository.findById(request.idEstoqueDestino())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de destino não encontrado"));

        UUID idReferencia = UUID.randomUUID();
        OffsetDateTime dataHora = OffsetDateTime.now();

        var assinaturasArmazenadas = assinaturaTransferenciaService.armazenar(
                idReferencia, request.assinaturaEntregador(), request.assinaturaRecebedor());

        for (MovimentacaoBulkRequest.ItemMovimentacaoRequest itemReq : request.itens()) {
            Material material = materialRepository.findById(itemReq.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + itemReq.idMaterial()));

            // Transferências são permitidas apenas para materiais NOVOS
            CondicaoMaterial condicao = itemReq.condicao() != null ? itemReq.condicao() : CondicaoMaterial.NOVO;

            MovimentacaoEstoque mov = new MovimentacaoEstoque(
                    origem, destino, material, itemReq.quantidade(),
                    MovimentacaoEstoque.TipoMovimentacao.TRANSFERENCIA, condicao, idOperador, idReferencia, itemReq.numSerie(), dataHora
            );
            mov.registrarAssinaturas(assinaturasArmazenadas.entregador(), assinaturasArmazenadas.recebedor());
            repository.save(mov);

            debitoSaldoService.debitar(origem, material, condicao, itemReq.quantidade());

            Saldo saldoDestino = obterOuCriarSaldo(destino, material, condicao);
            saldoDestino.adicionarQuantidade(itemReq.quantidade());
            saldoRepository.save(saldoDestino);

            if (material.isDetalhaSerie()) {
                SaldoSerie serie = serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(origem.getId(), material.getId(), itemReq.numSerie(), condicao)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Serial " + itemReq.numSerie() + " (NOVO) não localizado no estoque de origem."));
                serie.transferirPara(destino);
                serieRepository.save(serie);
            }
        }
        return idReferencia;
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID devolucaoComercial(DevolucaoComercialRequest request, UUID idOperador) {
        if (request.itens() == null || request.itens().isEmpty()) {
            throw new InvarianteVioladaException("A devolução comercial deve conter pelo menos um item.");
        }

        Estoque origemCampo = estoqueCampoEquipeService.resolver(request.idEquipe());
        Estoque destinoFisico = estoqueRepository.findById(request.idEstoqueDestino())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de destino não encontrado"));

        if (!destinoFisico.isAtivo() || destinoFisico.getTipoEstoque() == null
                || !"FISICO".equalsIgnoreCase(destinoFisico.getTipoEstoque().getNome())) {
            throw new InvarianteVioladaException("O estoque de destino da devolução comercial deve estar ativo e ser do tipo FISICO.");
        }

        validarSeriesUnicas(request.itens());

        UUID idReferencia = UUID.randomUUID();
        // A baixa dos itens que o destino não guarda tem referência própria: a listagem agrupa por
        // referência e mostraria a devolução e a baixa como uma operação só.
        UUID idReferenciaBaixa = UUID.randomUUID();
        OffsetDateTime dataHora = OffsetDateTime.now();

        for (DevolucaoComercialRequest.ItemDevolucaoComercialRequest itemReq : request.itens()) {
            Material material = materialRepository.findById(itemReq.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + itemReq.idMaterial()));

            CondicaoMaterial condicao = itemReq.condicao() != null ? itemReq.condicao() : CondicaoMaterial.NOVO;
            boolean guardar = itemReq.deveGuardar();

            if (material.isDetalhaSerie() && itemReq.quantidade().compareTo(BigDecimal.ONE) != 0) {
                throw new InvarianteVioladaException(
                        "O material " + material.getCodigo() + " controla série: devolva uma série por item, com quantidade 1.");
            }

            debitoSaldoService.debitar(origemCampo, material, condicao, itemReq.quantidade());

            if (guardar) {
                Saldo saldoDestino = obterOuCriarSaldo(destinoFisico, material, condicao);
                saldoDestino.adicionarQuantidade(itemReq.quantidade());
                saldoRepository.save(saldoDestino);
            }

            if (material.isDetalhaSerie()) {
                SaldoSerie serie = serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                        origemCampo.getId(), material.getId(), itemReq.numSerie(), condicao)
                        .orElseThrow(() -> new InvarianteVioladaException(
                                "Serial " + itemReq.numSerie() + " (" + condicao + ") não localizado no estoque da equipe (" + origemCampo.getNomeIdentificador() + ")."));
                serie.transferirPara(destinoFisico);
                if (!guardar) {
                    serie.desativar();
                }
                serieRepository.save(serie);
            }

            MovimentacaoEstoque mov = MovimentacaoEstoque.criarDevolucaoComercial(
                    origemCampo, destinoFisico, material, itemReq.quantidade(), condicao,
                    request.idEquipe(), idOperador, idReferencia, itemReq.numSerie()
            );
            repository.save(mov);

            if (!guardar) {
                repository.save(new MovimentacaoEstoque(
                        destinoFisico, null, material, itemReq.quantidade(),
                        MovimentacaoEstoque.TipoMovimentacao.BAIXA_AVULSA, condicao, idOperador,
                        idReferenciaBaixa, itemReq.numSerie(), dataHora
                ));
            }
        }

        return idReferencia;
    }

    private void validarSeriesUnicas(List<DevolucaoComercialRequest.ItemDevolucaoComercialRequest> itens) {
        Set<String> series = new HashSet<>();
        for (DevolucaoComercialRequest.ItemDevolucaoComercialRequest item : itens) {
            if (StringUtils.hasText(item.numSerie()) && !series.add(item.idMaterial() + "|" + item.numSerie().trim())) {
                throw new InvarianteVioladaException(
                        "O número de série " + item.numSerie() + " foi informado mais de uma vez na devolução.");
            }
        }
    }

    private Saldo obterOuCriarSaldo(Estoque estoque, Material material, CondicaoMaterial condicao) {
        return saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(estoque.getId(), material.getId(), condicao)
                .orElseGet(() -> new Saldo(estoque, material, condicao));
    }
}
