package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.CriacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.PlanejarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.IdempotenciaComandoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ReferenciaEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitacaoTecnicaUseCases {

    private static final String ESCOPO_CRIACAO = "CRIACAO_SOLICITACAO_TECNICA";

    private final ReferenciaTecnicaValidator referenciaValidator;
    private final EstoqueObraProvisioningService estoqueObraProvisioningService;
    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final ItemSolicitacaoRepository itemRepository;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final PublicadorEventoSolicitacaoTecnica publicadorEventos;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;
    private final IdempotenciaComandoSolicitacaoService idempotencia;

    @Transactional
    public CriacaoSolicitacaoTecnicaResponse criar(
            CriarSolicitacaoTecnicaRequest request,
            UUID idUsuario,
            OrigemComandoTecnico origem
    ) {
        return criar(request, idUsuario, origem, null);
    }

    @Transactional
    public CriacaoSolicitacaoTecnicaResponse criar(
            CriarSolicitacaoTecnicaRequest request,
            UUID idUsuario,
            OrigemComandoTecnico origem,
            String chaveIdempotencia
    ) {
        Objects.requireNonNull(request, "A solicitacao tecnica e obrigatoria");
        Objects.requireNonNull(idUsuario, "O usuario criador e obrigatorio");
        IdempotenciaComandoSolicitacaoService.Consulta<CriacaoSolicitacaoTecnicaResponse> consulta =
                chaveIdempotencia == null ? null : idempotencia.consultar(
                        ESCOPO_CRIACAO, idUsuario, chaveIdempotencia, request,
                        CriacaoSolicitacaoTecnicaResponse.class,
                        ObservabilidadeSolicitacaoTecnica.Operacao.CRIACAO);
        if (consulta != null && consulta.repetida()) {
            return consulta.resposta();
        }

        var contexto = referenciaValidator.validar(
                request.idObra(), request.ocorrencia(), request.idEquipe(), null,
                request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()));
        Estoque almoxarifado = resolverAlmoxarifado(request.idAlmoxarifadoAtendimento());
        Map<UUID, Material> materiais = resolverMateriais(request.itens());
        Map<TipoSolicitacaoTecnica, List<ItemResolvido>> grupos = agrupar(request.itens(), materiais);
        referenciaValidator.vincularEquipeSeNecessario(contexto);

        Estoque estoqueObra = estoqueObraProvisioningService.resolverOuProvisionar(contexto.obra());
        List<SolicitacaoMaterial> filhos = new ArrayList<>(grupos.size());
        grupos.forEach((tipo, itens) -> {
            SolicitacaoMaterial solicitacao = SolicitacaoMaterial.criarTecnica(
                    contexto.obra(), contexto.ocorrencia(), tipo, estoqueObra, almoxarifado, request.idEquipe(),
                    request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()),
                    request.dataPrevista(), request.observacao(), idUsuario);
            itens.forEach(item -> solicitacao.adicionarItemTecnico(
                    item.material(), item.quantidade()));
            filhos.add(solicitacao);
        });

        List<SolicitacaoMaterial> salvos = solicitacaoRepository.saveAll(filhos);
        salvos.stream()
                .filter(filho -> filho.getTipoTecnico().isAdicao()
                        && filho.getStatusSeparacao() == StatusSeparacao.PRONTO_PARA_SEPARACAO)
                .forEach(filho -> publicadorEventos.publicarFilho(
                        SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_PRONTA, filho));
        ContextoRegistroEventoSolicitacao contextoEvento = contextoEvento(idUsuario, origem, null);
        salvos.forEach(filho -> gravadorEvento.registrarFilho(
                filho, TipoEventoSolicitacao.SOLICITACAO_CRIADA,
                PayloadEventoSolicitacao.semAlteracoes(
                        "CRIAR_SOLICITACAO_TECNICA", snapshotInicial(filho, contextoEvento, contexto.obra(), contexto.equipe(), contexto.colaboradorSaque())),
                contextoEvento));
        observabilidade.registrarFilhosCriados(origem, salvos.size());
        CriacaoSolicitacaoTecnicaResponse resposta = new CriacaoSolicitacaoTecnicaResponse(
                salvos.stream()
                        .map(filho -> new CriacaoSolicitacaoTecnicaResponse.SolicitacaoCriada(
                                filho.getTipoTecnico(), filho.getId()))
                        .toList());
        if (consulta != null) {
            UUID idSolicitacao = salvos.size() == 1 ? salvos.getFirst().getId() : null;
            idempotencia.registrar(consulta, idSolicitacao, null, resposta);
        }
        return resposta;
    }

@Transactional
    public CriacaoSolicitacaoTecnicaResponse replanejar(
            UUID idSolicitacao,
            PlanejarSolicitacaoTecnicaRequest request,
            UUID idUsuario,
            OrigemComandoTecnico origem
    ) {
        Objects.requireNonNull(request, "O planejamento tecnico e obrigatorio");
        Objects.requireNonNull(idUsuario, "O usuario do planejamento e obrigatorio");
        SolicitacaoMaterial solicitacaoRaiz = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Solicitacao tecnica nao encontrada"));
        if (solicitacaoRaiz.getContexto() != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("A solicitacao informada nao e tecnica.");
        }
        if (!Objects.equals(solicitacaoRaiz.getVersao(), request.versaoEsperada())) {
            observabilidade.registrarConflito(
                    ObservabilidadeSolicitacaoTecnica.Operacao.PLANEJAMENTO,
                    ObservabilidadeSolicitacaoTecnica.TipoConflito.VERSAO);
            throw new ConflitoVersaoException(
                    ConflitoVersaoException.Recurso.SOLICITACAO,
                    request.versaoEsperada(), solicitacaoRaiz.getVersao());
        }

        List<SolicitacaoMaterial> filhos = new ArrayList<>(List.of(solicitacaoRaiz));
        if (filhos.stream().anyMatch(
                filho -> filho.getContexto() != ContextoSolicitacaoMaterial.TECNICO)) {
            throw new InvarianteVioladaException("A solicitacao tecnica e invalida.");
        }
        if (filhos.stream().anyMatch(SolicitacaoMaterial::estaEncerrada)) {
            throw new InvarianteVioladaException("A solicitacao possui saldo pendente encerrado e nao pode ser replanejada.");
        }
        Map<SolicitacaoMaterial, StatusSeparacao> separacoesOriginais = new LinkedHashMap<>();
        filhos.forEach(filho -> separacoesOriginais.put(filho, filho.getStatusSeparacao()));

        if (Boolean.TRUE.equals(request.saqueForaEquipe()) && request.idColaboradorSaque() == null) {
            throw new PreenchimentoInvalidoException(Map.of(
                    "idColaboradorSaque",
                    "O colaborador responsavel pelo saque e obrigatorio para saque fora da equipe."));
        }

        var contexto = referenciaValidator.validar(
                request.idObra(), request.ocorrencia(), request.idEquipe(), null,
                request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()));
        referenciaValidator.vincularEquipeSeNecessario(contexto);
        Estoque almoxarifado = resolverAlmoxarifado(request.idAlmoxarifadoAtendimento());
        Map<UUID, Material> materiais = resolverMateriaisPlanejamento(request.itens());
        Map<UUID, ItemExistente> existentes = indexarItens(filhos);
        List<ItemPlanejado> planejamento = validarPlanejamento(
                request.itens(), materiais, existentes, solicitacaoRaiz.getTipoTecnico());
        validarRemocoesEAlteracoesDeRota(
                solicitacaoRaiz, existentes, planejamento, contexto.obra().id(), almoxarifado.getId());
        boolean possuiFato = filhos.stream().anyMatch(SolicitacaoMaterial::possuiFatoOperacional);
        boolean houveAlteracao = houveAlteracaoPlanejamento(
                solicitacaoRaiz, codigoOcorrencia(contexto.ocorrencia()), almoxarifado, request,
                existentes, planejamento, contexto.colaboradorSaque());
        exigirJustificativaAposFato(possuiFato, houveAlteracao, request.justificativa());

        List<AlteracaoEventoSolicitacao> alteracoesCabecalho = alteracoesCabecalho(
                solicitacaoRaiz, codigoOcorrencia(contexto.ocorrencia()), almoxarifado, request,
                contexto.obra(), contexto.equipe(), contexto.colaboradorSaque());
        Map<SolicitacaoMaterial, List<AlteracaoEventoSolicitacao>> alteracoesPorFilho = new LinkedHashMap<>();
        Map<SolicitacaoMaterial, List<ItemSolicitacao>> itensAdicionadosPorFilho = new LinkedHashMap<>();
        Set<SolicitacaoMaterial> filhosComItensAlterados = new HashSet<>();
        Estoque estoqueObra = estoqueObraProvisioningService.resolverOuProvisionar(contexto.obra());
        Map<TipoSolicitacaoTecnica, SolicitacaoMaterial> destinos = new EnumMap<>(TipoSolicitacaoTecnica.class);
        destinos.put(solicitacaoRaiz.getTipoTecnico(), solicitacaoRaiz);

        Set<UUID> mantidos = planejamento.stream()
                .map(ItemPlanejado::existente)
                .filter(Objects::nonNull)
                .map(item -> item.item().getId())
                .collect(Collectors.toSet());
        List<ItemSolicitacao> removidos = existentes.values().stream()
                .filter(existente -> !mantidos.contains(existente.item().getId()))
                .map(existente -> {
                    filhosComItensAlterados.add(existente.filho());
                    adicionarAlteracao(alteracoesPorFilho, existente.filho(),
                            "itens[" + existente.item().getId() + "]", itemSnapshot(existente.item()), null);
                    return existente.filho().removerItemTecnico(existente.item().getId());
                }).toList();

        List<ItemSolicitacao> novosItens = new ArrayList<>();
        for (ItemPlanejado itemPlanejado : planejamento) {
            SolicitacaoMaterial destino = destinos.get(itemPlanejado.tipo());
            ItemExistente existente = itemPlanejado.existente();
            if (existente == null) {
                ItemSolicitacao adicionado = destino.adicionarItemTecnico(
                        itemPlanejado.material(), itemPlanejado.quantidade());
                filhosComItensAlterados.add(destino);
                itensAdicionadosPorFilho.computeIfAbsent(destino, ignorado -> new ArrayList<>())
                        .add(adicionado);
                novosItens.add(adicionado);
                continue;
            }
            ItemSolicitacao item = existente.item();
            Material materialAnterior = item.getMaterial();
            java.math.BigDecimal quantidadeAnterior = item.getQuantidade();
            boolean materialAlterado = !Objects.equals(materialAnterior.getId(), itemPlanejado.material().getId());
            boolean quantidadeAlterada = quantidadeAnterior.compareTo(itemPlanejado.quantidade()) != 0;
            boolean conteudoAlterado = materialAlterado || quantidadeAlterada;
            item.replanejar(itemPlanejado.material(), itemPlanejado.quantidade());
            if (existente.filho() != destino) {
                throw new InvarianteVioladaException("O item nao pode mudar de solicitacao durante o planejamento.");
            }
            if (conteudoAlterado) {
                filhosComItensAlterados.add(destino);
                String caminhoItem = "itens[" + item.getId() + "]";
                if (materialAlterado) {
                    adicionarAlteracao(alteracoesPorFilho, destino,
                            caminhoItem + ".material", referenciaMaterial(materialAnterior),
                            referenciaMaterial(itemPlanejado.material()));
                }
                if (quantidadeAlterada) {
                    adicionarAlteracao(alteracoesPorFilho, destino,
                            caminhoItem + ".quantidade", quantidadeAnterior, itemPlanejado.quantidade());
                }
            }
        }

        filhos.forEach(filho -> {
            filho.substituirPlanejamentoTecnico(
                    contexto.obra(), contexto.ocorrencia(), estoqueObra, almoxarifado, request.idEquipe(),
                    request.idColaboradorSaque(), Boolean.TRUE.equals(request.saqueForaEquipe()),
                    request.dataPrevista(), request.observacao());
            if (filhosComItensAlterados.contains(filho)) {
                filho.invalidarSeparacao();
            }
        });

        if (!removidos.isEmpty()) {
            itemRepository.deleteAll(removidos);
        }
        if (!novosItens.isEmpty()) {
            itemRepository.saveAll(novosItens);
        }
        List<SolicitacaoMaterial> salvos = solicitacaoRepository.saveAll(filhos);
        itensAdicionadosPorFilho.forEach((filho, itensAdicionados) -> itensAdicionados.forEach(item -> {
            if (item.getId() == null) {
                throw new IllegalStateException("O item adicionado deve estar identificado antes do historico.");
            }
            adicionarAlteracao(alteracoesPorFilho, filho,
                    "itens[" + item.getId() + "]", null, itemSnapshot(item));
        }));
        registrarEventosReplanejamento(
                filhos, alteracoesCabecalho, alteracoesPorFilho,
                contextoEvento(idUsuario, origem, request.justificativa()));
        salvos.stream()
                .filter(filho -> filho.getStatus() != SolicitacaoMaterial.StatusSolicitacao.CANCELADO)
                .filter(filho -> filho.getTipoTecnico().isAdicao()
                        && filho.getStatusSeparacao() == StatusSeparacao.PRONTO_PARA_SEPARACAO
                        && separacoesOriginais.get(filho) != StatusSeparacao.PRONTO_PARA_SEPARACAO)
                .forEach(filho -> publicadorEventos.publicarFilho(
                        SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_PRONTA, filho));
        salvos.stream()
                .filter(filho -> filho.getStatus() != SolicitacaoMaterial.StatusSolicitacao.CANCELADO)
                .filter(filho -> !filho.getTipoTecnico().isAdicao()
                        && filho.getDataPrevistaRetirada() != null
                        && filho.getIdEquipeSaque() != null
                        && filho.getReserva() != null
                        && alteracoesCabecalho.stream().anyMatch(alteracao ->
                                alteracao.caminho().equals("dataPrevista")
                                        || alteracao.caminho().equals("equipe")))
                .forEach(filho -> publicadorEventos.publicarFilho(
                        SolicitacaoMaterialTecnicaEvent.Tipo.DEVOLUCAO_PRONTA, filho));
        return new CriacaoSolicitacaoTecnicaResponse(
                salvos.stream()
                        .filter(filho -> filho.getStatus() != SolicitacaoMaterial.StatusSolicitacao.CANCELADO)
                        .map(filho -> new CriacaoSolicitacaoTecnicaResponse.SolicitacaoCriada(
                                filho.getTipoTecnico(), filho.getId()))
                        .toList());
    }

    private boolean houveAlteracaoPlanejamento(
            SolicitacaoMaterial solicitacao,
            String ocorrenciaNormalizada,
            Estoque almoxarifado,
            PlanejarSolicitacaoTecnicaRequest request,
            Map<UUID, ItemExistente> existentes,
            List<ItemPlanejado> planejamento,
            com.nexo.app.modules.rh.ColaboradorResponse colaboradorNovo
    ) {
        if (!alteracoesCabecalho(solicitacao, ocorrenciaNormalizada, almoxarifado, request, null, null, colaboradorNovo).isEmpty()) {
            return true;
        }
        Set<UUID> mantidos = planejamento.stream().map(ItemPlanejado::existente)
                .filter(Objects::nonNull).map(item -> item.item().getId()).collect(Collectors.toSet());
        if (existentes.keySet().stream().anyMatch(id -> !mantidos.contains(id))) {
            return true;
        }
        return planejamento.stream().anyMatch(item -> item.existente() == null
                || !Objects.equals(item.existente().item().getMaterial().getId(), item.material().getId())
                || item.existente().item().getQuantidade().compareTo(item.quantidade()) != 0
                || item.existente().filho().getTipoTecnico() != item.tipo());
    }

    private List<AlteracaoEventoSolicitacao> alteracoesCabecalho(
            SolicitacaoMaterial solicitacao,
            String ocorrenciaNormalizada,
            Estoque almoxarifado,
            PlanejarSolicitacaoTecnicaRequest request,
            com.nexo.app.modules.obras.ObraApi.ObraPublica obraNova,
            com.nexo.app.modules.rh.EquipeApi.EquipePublica equipeNova,
            com.nexo.app.modules.rh.ColaboradorResponse colaboradorNovo
    ) {
        List<AlteracaoEventoSolicitacao> alteracoes = new ArrayList<>();
        if (!Objects.equals(solicitacao.getIdObra(), request.idObra())) {
            alteracoes.add(new AlteracaoEventoSolicitacao("obra",
                    referenciaObra(solicitacao.getIdObra(), null),
                    referenciaObra(request.idObra(), obraNova)));
        }
        adicionarSeDiferente(alteracoes, "ocorrencia", solicitacao.getOcorrencia(), ocorrenciaNormalizada);
        adicionarSeDiferente(alteracoes, "almoxarifado",
                solicitacao.getAlmoxarifadoAtendimento() == null ? null : new ReferenciaEventoSolicitacao(
                        solicitacao.getAlmoxarifadoAtendimento().getId(),
                        solicitacao.getAlmoxarifadoAtendimento().getNomeIdentificador()),
                almoxarifado == null ? null : new ReferenciaEventoSolicitacao(
                        almoxarifado.getId(),
                        almoxarifado.getNomeIdentificador()));
        if (!Objects.equals(solicitacao.getIdEquipeSaque(), request.idEquipe())) {
            alteracoes.add(new AlteracaoEventoSolicitacao("equipe",
                    referenciaEquipe(solicitacao.getIdEquipeSaque(), null),
                    referenciaEquipe(request.idEquipe(), equipeNova)));
        }
        if (!Objects.equals(solicitacao.getIdColaboradorSaque(), request.idColaboradorSaque())) {
            alteracoes.add(new AlteracaoEventoSolicitacao("colaboradorSaque",
                    referenciaColaborador(solicitacao.getIdColaboradorSaque(), null),
                    referenciaColaborador(request.idColaboradorSaque(), colaboradorNovo)));
        }
        adicionarSeDiferente(alteracoes, "saqueForaEquipe", solicitacao.isSaqueForaEquipe(), Boolean.TRUE.equals(request.saqueForaEquipe()));
        adicionarSeDiferente(alteracoes, "dataPrevista", solicitacao.getDataPrevistaRetirada(), request.dataPrevista());
        adicionarSeDiferente(alteracoes, "observacao", solicitacao.getObservacao(), limpar(request.observacao()));
        return alteracoes;
    }

    private void exigirJustificativaAposFato(
            boolean possuiFato,
            boolean houveAlteracao,
            String justificativa
    ) {
        if (possuiFato && houveAlteracao && (justificativa == null || justificativa.isBlank())) {
            throw new PreenchimentoInvalidoException(Map.of(
                    "justificativa", "A justificativa e obrigatoria depois do primeiro fato operacional."));
        }
    }

    private void adicionarAlteracao(
            Map<SolicitacaoMaterial, List<AlteracaoEventoSolicitacao>> alteracoesPorFilho,
            SolicitacaoMaterial filho,
            String caminho,
            Object anterior,
            Object novo
    ) {
        alteracoesPorFilho.computeIfAbsent(filho, ignorado -> new ArrayList<>())
                .add(new AlteracaoEventoSolicitacao(caminho, anterior, novo));
    }

    private void adicionarSeDiferente(
            List<AlteracaoEventoSolicitacao> alteracoes,
            String caminho,
            Object anterior,
            Object novo
    ) {
        if (!Objects.equals(anterior, novo)) {
            alteracoes.add(new AlteracaoEventoSolicitacao(caminho, anterior, novo));
        }
    }

    private Map<String, Object> itemSnapshot(ItemSolicitacao item) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", item.getId());
        resultado.put("material", referenciaMaterial(item.getMaterial()));
        resultado.put("tipoTecnico", item.getSolicitacao().getTipoTecnico());
        resultado.put("quantidade", item.getQuantidade());
        return resultado;
    }

    private Map<String, Object> snapshotInicial(
            SolicitacaoMaterial solicitacao,
            ContextoRegistroEventoSolicitacao contexto,
            com.nexo.app.modules.obras.ObraApi.ObraPublica obra,
            com.nexo.app.modules.rh.EquipeApi.EquipePublica equipe,
            com.nexo.app.modules.rh.ColaboradorResponse colaboradorSaque
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("obra", referenciaObra(solicitacao.getIdObra(), obra));
        snapshot.put("tipoObra", solicitacao.getTipoObraSnapshot());
        snapshot.put("ocorrencia", solicitacao.getIdOcorrencia() == null ? null
                : new ReferenciaEventoSolicitacao(solicitacao.getIdOcorrencia(), solicitacao.getOcorrencia()));
        snapshot.put("tipoTecnico", solicitacao.getTipoTecnico());
        snapshot.put("almoxarifado", solicitacao.getAlmoxarifadoAtendimento() == null ? null
                : new ReferenciaEventoSolicitacao(
                        solicitacao.getAlmoxarifadoAtendimento().getId(),
                        solicitacao.getAlmoxarifadoAtendimento().getNomeIdentificador()));
        snapshot.put("equipe", referenciaEquipe(solicitacao.getIdEquipeSaque(), equipe));
        snapshot.put("colaboradorSaque", referenciaColaborador(solicitacao.getIdColaboradorSaque(), colaboradorSaque));
        snapshot.put("saqueForaEquipe", solicitacao.isSaqueForaEquipe());
        snapshot.put("dataPrevista", solicitacao.getDataPrevistaRetirada());
        snapshot.put("observacao", solicitacao.getObservacao());
        snapshot.put("itens", solicitacao.getItens().stream().map(this::itemSnapshot).toList());
        snapshot.put("usuario", new ReferenciaEventoSolicitacao(
                contexto.idAutor(), contexto.autorSnapshot()));
        return snapshot;
    }

    private ReferenciaEventoSolicitacao referenciaObra(UUID idObra, com.nexo.app.modules.obras.ObraApi.ObraPublica obra) {
        if (idObra == null && obra == null) return null;
        UUID id = obra != null ? obra.id() : idObra;
        String desc = obra != null
                ? (obra.numero() + (obra.titulo() != null && !obra.titulo().isBlank() ? " - " + obra.titulo() : ""))
                : null;
        return new ReferenciaEventoSolicitacao(id, desc);
    }

    private ReferenciaEventoSolicitacao referenciaEquipe(UUID idEquipe, com.nexo.app.modules.rh.EquipeApi.EquipePublica equipe) {
        if (idEquipe == null && equipe == null) return null;
        UUID id = equipe != null ? equipe.id() : idEquipe;
        String desc = equipe != null
                ? (equipe.codigo())
                : null;
        return new ReferenciaEventoSolicitacao(id, desc);
    }

    private ReferenciaEventoSolicitacao referenciaColaborador(UUID idColaborador, com.nexo.app.modules.rh.ColaboradorResponse colaborador) {
        if (idColaborador == null && colaborador == null) return null;
        UUID id = colaborador != null ? colaborador.id() : idColaborador;
        String desc = colaborador != null
                ? (colaborador.nomeCompleto())
                : null;
        return new ReferenciaEventoSolicitacao(id, desc);
    }

    private ReferenciaEventoSolicitacao referenciaMaterial(Material material) {
        return material == null ? null : new ReferenciaEventoSolicitacao(
                material.getId(), material.getCodigo() + " - " + material.getDescricao());
    }

    private void registrarEventosReplanejamento(
            List<SolicitacaoMaterial> filhos,
            List<AlteracaoEventoSolicitacao> alteracoesCabecalho,
            Map<SolicitacaoMaterial, List<AlteracaoEventoSolicitacao>> alteracoesPorFilho,
            ContextoRegistroEventoSolicitacao contexto
    ) {
        filhos.forEach(filho -> {
            List<AlteracaoEventoSolicitacao> alteracoes = new ArrayList<>(alteracoesCabecalho);
            alteracoes.addAll(alteracoesPorFilho.getOrDefault(filho, List.of()));
            if (!alteracoes.isEmpty()) {
                gravadorEvento.registrarFilho(filho, tipoEventoDasAlteracoes(alteracoes),
                        new PayloadEventoSolicitacao("REPLANEJAR_SOLICITACAO_TECNICA", alteracoes, Map.of()),
                        contexto);
            }
        });
    }

    private TipoEventoSolicitacao tipoEventoDasAlteracoes(List<AlteracaoEventoSolicitacao> alteracoes) {
        if (alteracoes.stream().anyMatch(alteracao -> alteracao.caminho().equals("ocorrencia"))) {
            return TipoEventoSolicitacao.OCORRENCIA_ALTERADA;
        }
        if (alteracoes.stream().anyMatch(alteracao -> alteracao.caminho().equals("equipe")
                || alteracao.caminho().equals("colaboradorSaque")
                || alteracao.caminho().equals("saqueForaEquipe"))) {
            return TipoEventoSolicitacao.EQUIPE_ALTERADA;
        }
        if (alteracoes.stream().anyMatch(alteracao -> alteracao.caminho().equals("dataPrevista"))) {
            return TipoEventoSolicitacao.DATA_PREVISTA_ALTERADA;
        }
        if (alteracoes.stream().anyMatch(alteracao -> alteracao.caminho().contains("material")
                || alteracao.caminho().matches("^itens\\[[^]]+]$"))) {
            return TipoEventoSolicitacao.MATERIAL_ALTERADO;
        }
        return TipoEventoSolicitacao.PLANEJAMENTO_ALTERADO;
    }

    private String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private String codigoOcorrencia(com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica ocorrencia) {
        return ocorrencia == null ? null : ocorrencia.codigo();
    }

    private Map<UUID, Material> resolverMateriaisPlanejamento(
            List<PlanejarSolicitacaoTecnicaRequest.Item> itens
    ) {
        if (itens == null || itens.isEmpty()) {
            throw campoInvalido("itens", "Informe ao menos um item tecnico.");
        }
        Map<String, String> erros = new LinkedHashMap<>();
        for (int indice = 0; indice < itens.size(); indice++) {
            var item = itens.get(indice);
            if (item == null || item.idMaterial() == null) {
                erros.put("itens[" + indice + "].idMaterial", "O material e obrigatorio.");
            }
            if (item == null || item.tipoMovimentacao() == null) {
                erros.put("itens[" + indice + "].tipoMovimentacao", "O tipo de movimentacao e obrigatorio.");
            }
            if (item == null || item.quantidade() == null || item.quantidade().signum() <= 0) {
                erros.put("itens[" + indice + "].quantidade", "A quantidade deve ser positiva.");
            }
        }
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        List<UUID> ids = itens.stream()
                .map(PlanejarSolicitacaoTecnicaRequest.Item::idMaterial).distinct().toList();
        Map<UUID, Material> materiais = materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        for (int indice = 0; indice < itens.size(); indice++) {
            Material material = materiais.get(itens.get(indice).idMaterial());
            if (material == null || !material.isAtivo()) {
                erros.put("itens[" + indice + "].idMaterial", "O material deve existir e estar ativo.");
            }
        }
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        return materiais;
    }

    private Map<UUID, ItemExistente> indexarItens(List<SolicitacaoMaterial> filhos) {
        Map<UUID, ItemExistente> existentes = new LinkedHashMap<>();
        filhos.forEach(filho -> filho.getItens().forEach(item ->
                existentes.put(item.getId(), new ItemExistente(filho, item))));
        return existentes;
    }

    private List<ItemPlanejado> validarPlanejamento(
            List<PlanejarSolicitacaoTecnicaRequest.Item> itens,
            Map<UUID, Material> materiais,
            Map<UUID, ItemExistente> existentes,
            TipoSolicitacaoTecnica tipoSolicitacao
    ) {
        Map<String, String> erros = new LinkedHashMap<>();
        Set<UUID> idsInformados = new HashSet<>();
        List<ItemPlanejado> resultado = new ArrayList<>(itens.size());
        for (int indice = 0; indice < itens.size(); indice++) {
            var informado = itens.get(indice);
            ItemExistente existente = informado.idItem() == null ? null : existentes.get(informado.idItem());
            if (informado.idItem() != null && existente == null) {
                erros.put("itens[" + indice + "].idItem", "O item nao pertence a solicitacao informada.");
                continue;
            }
            if (informado.idItem() != null && !idsInformados.add(informado.idItem())) {
                erros.put("itens[" + indice + "].idItem", "O item foi informado mais de uma vez.");
                continue;
            }
            Material material = materiais.get(informado.idMaterial());
            TipoSolicitacaoTecnica tipo = informado.tipoMovimentacao();
            if (tipo != tipoSolicitacao) {
                erros.put("itens[" + indice + "].tipoMovimentacao",
                        "O tipo do item deve ser o mesmo tipo imutavel da solicitacao.");
            }
            if (existente != null) {
                ItemSolicitacao atual = existente.item();
                if (informado.quantidade().compareTo(atual.getQuantidadeOperadaLiquida()) < 0) {
                    erros.put("itens[" + indice + "].quantidade",
                            "A quantidade nao pode ficar abaixo da quantidade liquida confirmada.");
                }
                boolean mudaIdentidade = !Objects.equals(atual.getMaterial().getId(), material.getId())
                        || existente.filho().getTipoTecnico() != tipo;
                if (atual.possuiFatoOperacional() && mudaIdentidade) {
                    erros.put("itens[" + indice + "]",
                            "Material ou tipo de item ja operado nao pode ser alterado.");
                }
            }
            resultado.add(new ItemPlanejado(
                    existente, material, informado.quantidade(), tipo));
        }
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        return resultado;
    }

    private void validarRemocoesEAlteracoesDeRota(
            SolicitacaoMaterial solicitacao,
            Map<UUID, ItemExistente> existentes,
            List<ItemPlanejado> planejamento,
            UUID novoIdObra,
            UUID novoIdAlmoxarifado
    ) {
        Set<UUID> mantidos = planejamento.stream()
                .map(ItemPlanejado::existente).filter(Objects::nonNull)
                .map(item -> item.item().getId()).collect(Collectors.toSet());
        boolean removeOperado = existentes.values().stream()
                .anyMatch(item -> !mantidos.contains(item.item().getId())
                        && item.item().possuiFatoOperacional());
        if (removeOperado) {
            throw new InvarianteVioladaException("Item com fato operacional nao pode ser removido.");
        }
        boolean rotaAlterada = !Objects.equals(solicitacao.getIdObra(), novoIdObra)
                || !Objects.equals(solicitacao.getAlmoxarifadoAtendimento().getId(), novoIdAlmoxarifado);
        boolean separacaoIniciada = solicitacao.getStatusSeparacao() == StatusSeparacao.EM_SEPARACAO
                || solicitacao.getStatusSeparacao() == StatusSeparacao.SEPARADO;
        boolean possuiFato = solicitacao.possuiFatoOperacional();
        if (rotaAlterada && (separacaoIniciada || possuiFato)) {
            throw new InvarianteVioladaException(
                    "Obra ou almoxarifado nao pode mudar depois do primeiro fato operacional.");
        }
    }

    private Estoque resolverAlmoxarifado(UUID idAlmoxarifado) {
        Estoque estoque = idAlmoxarifado == null
                ? null
                : estoqueRepository.findById(idAlmoxarifado).orElse(null);
        String tipo = estoque == null || estoque.getTipoEstoque() == null
                ? null
                : estoque.getTipoEstoque().getNome();
        if (estoque == null || !estoque.isAtivo()
                || !("FISICO".equalsIgnoreCase(tipo) || "OBRA".equalsIgnoreCase(tipo))) {
            throw campoInvalido(
                    "idAlmoxarifadoAtendimento",
                    "O estoque de atendimento deve existir, estar ativo e ser do tipo OBRA ou FISICO.");
        }
        return estoque;
    }

    private Map<UUID, Material> resolverMateriais(List<CriarSolicitacaoTecnicaRequest.Item> itens) {
        if (itens == null || itens.isEmpty()) {
            throw campoInvalido("itens", "Informe ao menos um item tecnico.");
        }
        Map<String, String> erros = new LinkedHashMap<>();
        for (int indice = 0; indice < itens.size(); indice++) {
            var item = itens.get(indice);
            if (item == null || item.idMaterial() == null) {
                erros.put("itens[" + indice + "].idMaterial", "O material e obrigatorio.");
            }
            if (item == null || item.tipoMovimentacao() == null) {
                erros.put("itens[" + indice + "].tipoMovimentacao", "O tipo de movimentacao e obrigatorio.");
            }
            if (item == null || item.quantidade() == null || item.quantidade().signum() <= 0) {
                erros.put("itens[" + indice + "].quantidade", "A quantidade deve ser positiva.");
            }
        }
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }

        List<UUID> ids = itens.stream().map(CriarSolicitacaoTecnicaRequest.Item::idMaterial).distinct().toList();
        Map<UUID, Material> materiais = materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        for (int indice = 0; indice < itens.size(); indice++) {
            UUID idMaterial = itens.get(indice).idMaterial();
            Material material = materiais.get(idMaterial);
            if (material == null || !material.isAtivo()) {
                erros.put("itens[" + indice + "].idMaterial", "O material deve existir e estar ativo.");
            }
        }
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        return materiais;
    }

    private Map<TipoSolicitacaoTecnica, List<ItemResolvido>> agrupar(
            List<CriarSolicitacaoTecnicaRequest.Item> itens,
            Map<UUID, Material> materiais
    ) {
        Map<TipoSolicitacaoTecnica, List<ItemResolvido>> grupos =
                new EnumMap<>(TipoSolicitacaoTecnica.class);
        itens.forEach(item -> {
            Material material = materiais.get(item.idMaterial());
            TipoSolicitacaoTecnica tipo = item.tipoMovimentacao();
            grupos.computeIfAbsent(tipo, ignorado -> new ArrayList<>())
                    .add(new ItemResolvido(material, item.quantidade()));
        });
        return grupos;
    }

    private PreenchimentoInvalidoException campoInvalido(String campo, String mensagem) {
        return new PreenchimentoInvalidoException(Map.of(campo, mensagem));
    }

    private ContextoRegistroEventoSolicitacao contextoEvento(
            UUID idUsuario,
            OrigemComandoTecnico origem,
            String justificativa
    ) {
        return new ContextoRegistroEventoSolicitacao(
                idUsuario, "USUARIO " + idUsuario, origem, justificativa);
    }

    private record ItemResolvido(
            Material material,
            java.math.BigDecimal quantidade
    ) {
    }

    private record ItemExistente(SolicitacaoMaterial filho, ItemSolicitacao item) {
    }

    private record ItemPlanejado(
            ItemExistente existente,
            Material material,
            java.math.BigDecimal quantidade,
            TipoSolicitacaoTecnica tipo
    ) {
    }
}
