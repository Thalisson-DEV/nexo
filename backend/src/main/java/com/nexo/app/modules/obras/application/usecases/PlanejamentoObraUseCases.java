package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.AnexoObraResponse;
import com.nexo.app.modules.obras.application.dto.ItemKitResponse;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoResponse;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.AplicarKit;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.EditarPonto;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.MoverPonto;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.NovaAnotacao;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.NovoPonto;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.NovoVao;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.ResultadoAplicacaoKit;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraResponse;
import com.nexo.app.modules.obras.domain.AnexoObra;
import com.nexo.app.modules.obras.domain.AnotacaoPlanejamento;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.OrigemPlanejamento;
import com.nexo.app.modules.obras.domain.PlanejamentoObra;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.domain.TipoPontoPlanejamento;
import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import com.nexo.app.modules.obras.infrastructure.persistence.AnexoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AnotacaoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitAplicadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PlanejamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.VaoPlanejamentoRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Comandos e leitura do planejamento da obra. Cada comando grava uma alteração pequena e devolve o planejamento
 * atualizado; mover ponto devolve só o ponto, porque acontece muitas vezes seguidas.
 */
@Service
@RequiredArgsConstructor
public class PlanejamentoObraUseCases {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ObraRepository obraRepository;
    private final AnexoObraRepository anexoRepository;
    private final AnexoObraUseCases anexoUseCases;
    private final PlanejamentoObraRepository planejamentoRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final VaoPlanejamentoRepository vaoRepository;
    private final KitAplicadoRepository kitAplicadoRepository;
    private final AnotacaoPlanejamentoRepository anotacaoRepository;
    private final KitConstrutivoUseCases kitUseCases;

    @Transactional(readOnly = true)
    public PlanejamentoObraResponse buscar(UUID idObra) {
        return montar(buscarObra(idObra));
    }

    // ---- Projeto mestre ----

    @Transactional
    public PlanejamentoObraResponse definirProjetoMestre(UUID idObra, UUID idAnexo) {
        Obra obra = buscarObra(idObra);
        AnexoObra anexo = anexoRepository.findByIdAndObraIdAndAtivoTrue(idAnexo, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado nesta obra."));
        validarPdf(anexo.getContentType(), anexo.getNomeArquivo());
        obterPlanejamento(idObra).definirProjetoMestre(anexo.getId());
        return montar(obra);
    }

    /**
     * Anexa o PDF e o torna o projeto mestre. O anterior continua nos anexos da obra e os pontos não mudam.
     */
    @Transactional
    public PlanejamentoObraResponse enviarProjetoMestre(UUID idObra, UUID idTipoAnexo, MultipartFile arquivo) {
        validarPdf(arquivo != null ? arquivo.getContentType() : null, arquivo != null ? arquivo.getOriginalFilename() : null);
        AnexoObraResponse anexo = anexoUseCases.anexar(idObra, idTipoAnexo, "Projeto mestre do planejamento", arquivo);
        return definirProjetoMestre(idObra, anexo.id());
    }

    @Transactional
    public PlanejamentoObraResponse girarPagina(UUID idObra, int pagina, int graus) {
        Obra obra = buscarObra(idObra);
        obterPlanejamento(idObra).girarPagina(pagina, graus);
        return montar(obra);
    }

    // ---- Pontos ----

    @Transactional
    public PlanejamentoObraResponse adicionarPonto(UUID idObra, NovoPonto request) {
        Obra obra = buscarObra(idObra);
        criarPonto(idObra, request, OrigemPlanejamento.MANUAL);
        return montar(obra);
    }

    PontoPlanejamento criarPonto(UUID idObra, NovoPonto request, OrigemPlanejamento origem) {
        String numero = PontoPlanejamento.normalizarNumero(request.numero());
        validarNumeroLivre(idObra, numero);
        PontoPlanejamento ponto = new PontoPlanejamento(idObra, numero, request.pagina(), request.x(), request.y(), origem);
        ponto.editar(numero, request.barramento(), request.tipo(), null, false, false);
        return pontoRepository.save(ponto);
    }

    @Transactional
    public PlanejamentoObraResponse editarPonto(UUID idObra, UUID idPonto, EditarPonto request) {
        Obra obra = buscarObra(idObra);
        PontoPlanejamento ponto = buscarPonto(idObra, idPonto);
        conferirVersao(ponto, request.versao());
        String numero = PontoPlanejamento.normalizarNumero(request.numero());
        if (!numero.equals(ponto.getNumero())) {
            validarNumeroLivre(idObra, numero);
        }
        ponto.editar(numero, request.barramento(), request.tipo(), request.tipoExecucao(), request.temPoda(),
                request.temAterramento());
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse.Ponto moverPonto(UUID idObra, UUID idPonto, MoverPonto request) {
        PontoPlanejamento ponto = buscarPonto(idObra, idPonto);
        conferirVersao(ponto, request.versao());
        ponto.mover(request.pagina(), request.x(), request.y());
        return paraResponse(pontoRepository.saveAndFlush(ponto));
    }

    /**
     * Exclui o ponto com seus kits e vãos. Qualquer item programado ou executado impede a exclusão inteira.
     */
    @Transactional
    public PlanejamentoObraResponse excluirPonto(UUID idObra, UUID idPonto) {
        Obra obra = buscarObra(idObra);
        PontoPlanejamento ponto = buscarPonto(idObra, idPonto);
        List<VaoPlanejamento> vaos = vaoRepository.findByIdObraAndAtivoTrue(idObra).stream()
                .filter(v -> v.liga(idPonto))
                .toList();
        vaos.forEach(this::removerVao);
        kitAplicadoRepository.findByIdPontoAndAtivoTrue(idPonto).forEach(KitAplicado::remover);
        ponto.desativar();
        return montar(obra);
    }

    // ---- Vãos ----

    @Transactional
    public PlanejamentoObraResponse adicionarVao(UUID idObra, NovoVao request) {
        Obra obra = buscarObra(idObra);
        criarVao(idObra, request.idPontoA(), request.idPontoB(), request.metragem(), OrigemPlanejamento.MANUAL);
        return montar(obra);
    }

    VaoPlanejamento criarVao(UUID idObra, UUID idPontoA, UUID idPontoB, BigDecimal metragem, OrigemPlanejamento origem) {
        PontoPlanejamento a = buscarPonto(idObra, idPontoA);
        PontoPlanejamento b = buscarPonto(idObra, idPontoB);
        boolean duplicado = vaoRepository.findByIdObraAndAtivoTrue(idObra).stream()
                .anyMatch(v -> v.liga(idPontoA) && v.liga(idPontoB));
        if (duplicado) {
            throw new InvarianteVioladaException("Já existe um vão entre P" + a.getNumero() + " e P" + b.getNumero() + ".");
        }
        return vaoRepository.save(new VaoPlanejamento(idObra, idPontoA, idPontoB, metragem, origem));
    }

    /**
     * Muda a metragem do vão e leva junto os kits que usavam a metragem antiga e ainda não foram programados.
     */
    @Transactional
    public PlanejamentoObraResponse editarVao(UUID idObra, UUID idVao, BigDecimal metragem, Long versao) {
        Obra obra = buscarObra(idObra);
        VaoPlanejamento vao = buscarVao(idObra, idVao);
        conferirVersao(vao, versao);
        BigDecimal anterior = vao.getMetragem();
        vao.informarMetragem(metragem);
        if (metragem != null && anterior != null) {
            kitAplicadoRepository.findByIdVaoAndAtivoTrue(idVao).stream()
                    .filter(k -> k.getMultiplicador().compareTo(anterior) == 0)
                    .filter(k -> k.itensBloqueantes().isEmpty() && !k.isExecutadoAntes())
                    .forEach(k -> k.alterarMultiplicador(metragem));
        }
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse excluirVao(UUID idObra, UUID idVao) {
        Obra obra = buscarObra(idObra);
        removerVao(buscarVao(idObra, idVao));
        return montar(obra);
    }

    private void removerVao(VaoPlanejamento vao) {
        kitAplicadoRepository.findByIdVaoAndAtivoTrue(vao.getId()).forEach(KitAplicado::remover);
        vao.desativar();
    }

    // ---- Kits ----

    @Transactional
    public ResultadoAplicacaoKit aplicarKit(UUID idObra, AplicarKit request) {
        Obra obra = buscarObra(idObra);
        List<UUID> pontos = request.idsPontos() != null ? request.idsPontos() : List.of();
        List<UUID> vaos = request.idsVaos() != null ? request.idsVaos() : List.of();
        if (pontos.isEmpty() && vaos.isEmpty()) {
            throw new InvarianteVioladaException("Selecione ao menos um ponto ou vão para aplicar o kit.");
        }
        List<String> jaTinham = aplicar(obra, request.idKit(), pontos, vaos, request.operacao(), request.multiplicador());
        return new ResultadoAplicacaoKit(montar(obra), jaTinham);
    }

    /**
     * @return os locais que já tinham o kit com a mesma operação e por isso ficaram como estavam
     */
    List<String> aplicar(Obra obra, UUID idKit, List<UUID> pontos, List<UUID> vaos, OperacaoItemKit operacao,
                         BigDecimal multiplicador) {
        KitConstrutivo kit = kitUseCases.buscarEntidade(idKit);
        // A tensão não bloqueia: a tela filtra por ela e o usuário pode escolher "ver todos".
        kit.validarAplicacao(null);
        OperacaoItemKit acao = operacao != null ? operacao : OperacaoItemKit.INSTALAR;
        List<KitAplicado.ModeloItem> modelos = modelos(kitUseCases.buscarPorId(idKit, null));

        List<String> jaTinham = new ArrayList<>();
        for (UUID idPonto : pontos) {
            PontoPlanejamento ponto = buscarPonto(obra.getId(), idPonto);
            boolean existe = kitAplicadoRepository.findByIdPontoAndAtivoTrue(idPonto).stream()
                    .anyMatch(k -> k.getIdKit().equals(idKit) && k.getOperacao() == acao);
            if (existe) {
                jaTinham.add("P" + ponto.getNumero());
            } else {
                kitAplicadoRepository.save(KitAplicado.noPonto(obra.getId(), idPonto, kit, acao, multiplicador, modelos));
            }
        }
        for (UUID idVao : vaos) {
            VaoPlanejamento vao = buscarVao(obra.getId(), idVao);
            boolean existe = kitAplicadoRepository.findByIdVaoAndAtivoTrue(idVao).stream()
                    .anyMatch(k -> k.getIdKit().equals(idKit) && k.getOperacao() == acao);
            if (existe) {
                jaTinham.add(rotulo(obra.getId(), vao));
                continue;
            }
            BigDecimal metragem = multiplicador != null ? multiplicador : vao.getMetragem();
            if (metragem == null && modelos.stream().anyMatch(m -> KitAplicado.medidoEmMetro(m.unidade()))) {
                throw new InvarianteVioladaException("Informe a metragem do vão " + rotulo(obra.getId(), vao)
                        + " para aplicar o kit " + kit.getCodigo() + ".");
            }
            kitAplicadoRepository.save(KitAplicado.noVao(obra.getId(), idVao, kit, acao, metragem, modelos));
        }
        return jaTinham;
    }

    @Transactional
    public PlanejamentoObraResponse alterarMultiplicador(UUID idObra, UUID idKitAplicado, BigDecimal multiplicador, Long versao) {
        Obra obra = buscarObra(idObra);
        KitAplicado kit = buscarKitAplicado(idObra, idKitAplicado);
        conferirVersao(kit, versao);
        kit.alterarMultiplicador(multiplicador);
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse removerKit(UUID idObra, UUID idKitAplicado) {
        Obra obra = buscarObra(idObra);
        buscarKitAplicado(idObra, idKitAplicado).remover();
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse marcarExecutadoAntes(UUID idObra, UUID idKitAplicado, boolean executado, Long versao) {
        Obra obra = buscarObra(idObra);
        KitAplicado kit = buscarKitAplicado(idObra, idKitAplicado);
        conferirVersao(kit, versao);
        kit.marcarExecutadoAntes(executado);
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse alterarItem(UUID idObra, UUID idKitAplicado, UUID idItem, BigDecimal quantidade, Long versao) {
        Obra obra = buscarObra(idObra);
        KitAplicado kit = buscarKitAplicado(idObra, idKitAplicado);
        conferirVersao(buscarItem(kit, idItem), versao);
        kit.alterarQuantidadeItem(idItem, quantidade);
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse removerItem(UUID idObra, UUID idKitAplicado, UUID idItem) {
        Obra obra = buscarObra(idObra);
        buscarKitAplicado(idObra, idKitAplicado).removerItem(idItem);
        return montar(obra);
    }

    // ---- Anotações ----

    @Transactional
    public PlanejamentoObraResponse adicionarAnotacao(UUID idObra, NovaAnotacao request) {
        Obra obra = buscarObra(idObra);
        anotacaoRepository.save(new AnotacaoPlanejamento(idObra, request.pagina(), request.x(), request.y(), request.texto()));
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse editarAnotacao(UUID idObra, UUID idAnotacao, String texto, Long versao) {
        Obra obra = buscarObra(idObra);
        AnotacaoPlanejamento anotacao = buscarAnotacao(idObra, idAnotacao);
        conferirVersao(anotacao, versao);
        anotacao.editar(texto);
        return montar(obra);
    }

    @Transactional
    public PlanejamentoObraResponse excluirAnotacao(UUID idObra, UUID idAnotacao) {
        Obra obra = buscarObra(idObra);
        buscarAnotacao(idObra, idAnotacao).desativar();
        return montar(obra);
    }

    // ---- Apoio ----

    Obra buscarObra(UUID idObra) {
        return obraRepository.findById(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));
    }

    PlanejamentoObra obterPlanejamento(UUID idObra) {
        return planejamentoRepository.findByIdObraAndAtivoTrue(idObra)
                .orElseGet(() -> planejamentoRepository.save(new PlanejamentoObra(idObra)));
    }

    PontoPlanejamento buscarPonto(UUID idObra, UUID idPonto) {
        return pontoRepository.findByIdAndIdObraAndAtivoTrue(idPonto, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ponto não encontrado nesta obra."));
    }

    private VaoPlanejamento buscarVao(UUID idObra, UUID idVao) {
        return vaoRepository.findByIdAndIdObraAndAtivoTrue(idVao, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vão não encontrado nesta obra."));
    }

    private KitAplicado buscarKitAplicado(UUID idObra, UUID id) {
        return kitAplicadoRepository.findByIdAndIdObraAndAtivoTrue(id, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Kit não encontrado neste planejamento."));
    }

    private AnotacaoPlanejamento buscarAnotacao(UUID idObra, UUID id) {
        return anotacaoRepository.findByIdAndIdObraAndAtivoTrue(id, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anotação não encontrada nesta obra."));
    }

    private static ItemPlanejado buscarItem(KitAplicado kit, UUID idItem) {
        return kit.getItensAtivos().stream()
                .filter(i -> Objects.equals(i.getId(), idItem))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado neste kit."));
    }

    private void validarNumeroLivre(UUID idObra, String numero) {
        if (pontoRepository.existsByIdObraAndNumeroAndAtivoTrue(idObra, numero)) {
            throw new InvarianteVioladaException("Já existe o ponto " + numero + " nesta obra.");
        }
    }

    private static void validarPdf(String contentType, String nomeArquivo) {
        boolean pdf = "application/pdf".equalsIgnoreCase(contentType)
                || (nomeArquivo != null && nomeArquivo.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if (!pdf) {
            throw new InvarianteVioladaException("O projeto mestre precisa ser um arquivo PDF.");
        }
    }

    /** Recusa a gravação quando o registro mudou depois que o usuário o abriu; a tela recarrega e avisa. */
    private static void conferirVersao(BaseEntity entidade, Long versao) {
        if (versao != null && !versao.equals(entidade.getVersao())) {
            throw new ObjectOptimisticLockingFailureException(entidade.getClass(), entidade.getId());
        }
    }

    private String rotulo(UUID idObra, VaoPlanejamento vao) {
        return "P" + buscarPonto(idObra, vao.getIdPontoA()).getNumero()
                + " — P" + buscarPonto(idObra, vao.getIdPontoB()).getNumero();
    }

    private static List<KitAplicado.ModeloItem> modelos(KitConstrutivoResponse kit) {
        List<KitAplicado.ModeloItem> modelos = new ArrayList<>();
        for (ItemKitResponse item : kit.itens()) {
            modelos.add(new KitAplicado.ModeloItem(item.tipo(), item.idReferencia(), item.codigoReferencia(),
                    item.descricaoReferencia(), item.operacao(), item.unidade(), item.quantidade()));
        }
        return modelos;
    }

    // ---- Leitura ----

    private PlanejamentoObraResponse montar(Obra obra) {
        UUID idObra = obra.getId();
        pontoRepository.flush();
        PlanejamentoObra planejamento = planejamentoRepository.findByIdObraAndAtivoTrue(idObra).orElse(null);
        List<PontoPlanejamento> pontos = pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(idObra);
        List<VaoPlanejamento> vaos = vaoRepository.findByIdObraAndAtivoTrue(idObra);
        List<KitAplicado> kits = kitAplicadoRepository.listarComItens(idObra);
        Map<UUID, Map<UUID, BigDecimal>> valores = valoresPorContrato(kits);

        Set<UUID> locaisComKit = new HashSet<>();
        for (KitAplicado kit : kits) {
            locaisComKit.add(kit.getIdPonto() != null ? kit.getIdPonto() : kit.getIdVao());
        }

        return new PlanejamentoObraResponse(idObra, obra.getNumero(), obra.getTitulo(), obra.getIdContrato(),
                obra.getClasseTensao(), projetoMestre(idObra, planejamento),
                planejamento != null ? planejamento.getRotacoes() : Map.of(),
                pontos.stream().map(PlanejamentoObraUseCases::paraResponse).toList(),
                vaos.stream().map(v -> new PlanejamentoObraResponse.Vao(v.getId(), v.getIdPontoA(), v.getIdPontoB(),
                        v.getMetragem(), v.getOrigem(), v.getVersao())).toList(),
                kits.stream().map(k -> paraResponse(k, valores.getOrDefault(k.getIdContrato(), Map.of()))).toList(),
                anotacaoRepository.findByIdObraAndAtivoTrue(idObra).stream()
                        .map(a -> new PlanejamentoObraResponse.Anotacao(a.getId(), a.getPagina(), a.getX(), a.getY(),
                                a.getTexto(), a.getUsuarioCriacao(), a.getDataCriacao(), a.getVersao()))
                        .toList(),
                indicadores(kits, valores,
                        (int) pontos.stream().filter(p -> !locaisComKit.contains(p.getId())).count(),
                        (int) vaos.stream().filter(v -> !locaisComKit.contains(v.getId())).count()),
                avancoPorKit(kits, valores));
    }

    private PlanejamentoObraResponse.ProjetoMestre projetoMestre(UUID idObra, PlanejamentoObra planejamento) {
        if (planejamento == null || planejamento.getIdAnexoMestre() == null) {
            return null;
        }
        return anexoRepository.findByIdAndObraIdAndAtivoTrue(planejamento.getIdAnexoMestre(), idObra)
                .map(a -> new PlanejamentoObraResponse.ProjetoMestre(a.getId(), a.getNomeArquivo(), a.getTamanhoBytes()))
                .orElse(null);
    }

    private Map<UUID, Map<UUID, BigDecimal>> valoresPorContrato(List<KitAplicado> kits) {
        Map<UUID, Map<UUID, BigDecimal>> valores = new HashMap<>();
        LocalDate hoje = LocalDate.now();
        for (KitAplicado kit : kits) {
            valores.computeIfAbsent(kit.getIdContrato(), id -> kitUseCases.valoresVigentes(id, hoje));
        }
        return valores;
    }

    private static PlanejamentoObraResponse.Ponto paraResponse(PontoPlanejamento p) {
        return new PlanejamentoObraResponse.Ponto(p.getId(), p.getNumero(), p.getPagina(), p.getX(), p.getY(),
                p.getBarramento(), p.getTipo(), p.getTipoExecucao(), p.isTemPoda(), p.isTemAterramento(),
                p.getOrigem(), p.getUsuarioCriacao(), p.getDataCriacao(), p.getVersao());
    }

    private static PlanejamentoObraResponse.Kit paraResponse(KitAplicado k, Map<UUID, BigDecimal> valores) {
        return new PlanejamentoObraResponse.Kit(k.getId(), k.getIdPonto(), k.getIdVao(), k.getIdKit(), k.getCodigoKit(),
                k.getDescricaoKit(), k.getGrupoKit(), k.getOperacao(), k.getMultiplicador(), k.isExecutadoAntes(),
                k.getVersao(),
                k.getItensAtivos().stream()
                        .map(i -> new PlanejamentoObraResponse.Item(i.getId(), i.getTipo(), i.getCodigo(), i.getDescricao(),
                                i.getOperacao(), i.getUnidade(), i.getQuantidadePrevista(), i.getQuantidadeExecutada(),
                                i.getStatus(), i.getTipo() == TipoItemKit.SERVICO ? valores.get(i.getIdAcao()) : null,
                                i.getVersao()))
                        .toList());
    }

    static PlanejamentoObraResponse.Indicadores indicadores(List<KitAplicado> kits, Map<UUID, Map<UUID, BigDecimal>> valores,
                                                            int pontosSemKit, int vaosSemKit) {
        BigDecimal executado = BigDecimal.ZERO;
        BigDecimal programado = BigDecimal.ZERO;
        BigDecimal pendente = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        int semValor = 0;
        for (KitAplicado kit : kits) {
            Map<UUID, BigDecimal> valoresKit = valores.getOrDefault(kit.getIdContrato(), Map.of());
            for (ItemPlanejado item : kit.getItensAtivos()) {
                if (item.getTipo() != TipoItemKit.SERVICO) {
                    continue;
                }
                BigDecimal unitario = valoresKit.get(item.getIdAcao());
                if (unitario == null) {
                    semValor++;
                    continue;
                }
                BigDecimal valorPrevisto = unitario.multiply(item.getQuantidadePrevista());
                BigDecimal valorExecutado = unitario.multiply(item.getQuantidadeExecutada()).min(valorPrevisto);
                total = total.add(valorPrevisto);
                executado = executado.add(valorExecutado);
                if (item.getStatus() == StatusItemPlanejado.PENDENTE) {
                    pendente = pendente.add(valorPrevisto);
                } else if (item.getStatus() == StatusItemPlanejado.PROGRAMADO
                        || item.getStatus() == StatusItemPlanejado.EM_ANDAMENTO) {
                    programado = programado.add(valorPrevisto.subtract(valorExecutado));
                }
            }
        }
        return new PlanejamentoObraResponse.Indicadores(percentual(executado, total), moeda(executado), moeda(programado),
                moeda(pendente), moeda(total), semValor, pontosSemKit, vaosSemKit);
    }

    static List<PlanejamentoObraResponse.AvancoKit> avancoPorKit(List<KitAplicado> kits, Map<UUID, Map<UUID, BigDecimal>> valores) {
        Map<String, BigDecimal[]> somas = new LinkedHashMap<>();
        Map<String, String> descricoes = new HashMap<>();
        kits.stream()
                .sorted((a, b) -> a.getCodigoKit().compareTo(b.getCodigoKit()))
                .forEach(kit -> {
                    descricoes.put(kit.getCodigoKit(), kit.getDescricaoKit());
                    BigDecimal[] soma = somas.computeIfAbsent(kit.getCodigoKit(), c -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    Map<UUID, BigDecimal> valoresKit = valores.getOrDefault(kit.getIdContrato(), Map.of());
                    for (ItemPlanejado item : kit.getItensAtivos()) {
                        BigDecimal unitario = item.getTipo() == TipoItemKit.SERVICO ? valoresKit.get(item.getIdAcao()) : null;
                        if (unitario != null) {
                            BigDecimal previsto = unitario.multiply(item.getQuantidadePrevista());
                            soma[0] = soma[0].add(unitario.multiply(item.getQuantidadeExecutada()).min(previsto));
                            soma[1] = soma[1].add(previsto);
                        }
                    }
                });
        return somas.entrySet().stream()
                .map(e -> new PlanejamentoObraResponse.AvancoKit(e.getKey(), descricoes.get(e.getKey()),
                        percentual(e.getValue()[0], e.getValue()[1]), moeda(e.getValue()[0]), moeda(e.getValue()[1])))
                .toList();
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0
                ? BigDecimal.ZERO
                : parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
