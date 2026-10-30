package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmarEntregaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarDevolucaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.EstornarOperacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.AssinaturaSolicitacaoMaterialService;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemOperacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemOperacaoSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import com.nexo.app.modules.estoque.infrastructure.persistence.MotivoPendenciaRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSolicitacaoTecnicaRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

@Service
@RequiredArgsConstructor
public class OperacaoSolicitacaoTecnicaUseCases {

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final OperacaoSolicitacaoMaterialRepository operacaoRepository;
    private final ItemOperacaoSolicitacaoRepository itemOperacaoRepository;
    private final ComandoIdempotenteSolicitacaoRepository comandoRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository saldoSerieRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final ReferenciaTecnicaValidator referenciaValidator;
    private final ReservaSolicitacaoTecnicaUseCases reservaUseCases;
    private final ReservaSolicitacaoTecnicaRepository reservaRepository;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final PublicadorEventoSolicitacaoTecnica publicadorEventos;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;
    private final AssinaturaSolicitacaoMaterialService assinaturas;
    private final DebitoSaldoService debitoSaldoService;
    private final EstoqueCampoEquipeProvisioningService estoqueCampoEquipeService;
    private final MotivoPendenciaRepository motivoPendenciaRepository;

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID entregar(
            UUID idSolicitacao,
            ConfirmarEntregaTecnicaRequest request,
            UUID idUsuarioOperador,
            OrigemComandoTecnico origem
    ) {
        return entregar(idSolicitacao, request, idUsuarioOperador, origem, null);
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID entregar(
            UUID idSolicitacao,
            ConfirmarEntregaTecnicaRequest request,
            UUID idUsuarioOperador,
            OrigemComandoTecnico origem,
            String chaveIdempotencia
    ) {
        Objects.requireNonNull(request, "Os dados da entrega tecnica sao obrigatorios");
        Objects.requireNonNull(idUsuarioOperador, "O usuario operador e obrigatorio");
        UUID repetida = resultadoRepetido("ENTREGA_TECNICA", idSolicitacao, idUsuarioOperador,
                chaveIdempotencia, request);
        if (repetida != null) {
            return repetida;
        }
        SolicitacaoMaterial solicitacao = carregarTecnica(
                idSolicitacao, request.versaoEsperada(), ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA);

        if (solicitacao.getReserva() == null && request.numeroReserva() != null && !request.numeroReserva().isBlank()) {
            LocalDate dataReserva = request.dataReserva() != null ? request.dataReserva() : LocalDate.now();
            reservaUseCases.registrarOuAlterar(
                    idSolicitacao,
                    new RegistrarReservaTecnicaRequest(solicitacao.getVersao(), request.numeroReserva().trim(), dataReserva, request.idAgenteReserva(), null),
                    idUsuarioOperador,
                    origem
            );
            solicitacao = carregarTecnica(
                    idSolicitacao, null, ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA);
            reservaRepository.findBySolicitacaoId(idSolicitacao)
                    .ifPresent(solicitacao::associarReserva);
        }

        validarEntrega(solicitacao, request);
        UUID idColaboradorEfetivo = request.idColaboradorSaque() != null
                ? request.idColaboradorSaque()
                : solicitacao.getIdColaboradorSaque();
        if (idColaboradorEfetivo == null) {
            throw new InvarianteVioladaException("O colaborador responsavel pelo saque e obrigatorio para confirmar a entrega tecnica.");
        }
        boolean saqueForaEquipeEfetivo = request.idColaboradorSaque() != null
                ? Boolean.TRUE.equals(request.saqueForaEquipe())
                : solicitacao.isSaqueForaEquipe();

        solicitacao.registrarEquipeEfetivaSeAusente(request.idEquipe(), idColaboradorEfetivo, saqueForaEquipeEfetivo);

        referenciaValidator.validar(
                solicitacao.getIdObra(), solicitacao.getOcorrencia(), request.idEquipe(), null,
                idColaboradorEfetivo, saqueForaEquipeEfetivo);
        Estoque estoqueEquipe = estoqueCampoEquipeService.resolver(request.idEquipe());
        List<ItemSolicitacao> itens = validarItensEntrega(solicitacao, request.itens());
        SolicitacaoMaterial.StatusSolicitacao statusAnterior = solicitacao.getStatus();

        var assinaturasArmazenadas = prepararAssinaturas(
                idSolicitacao, request.assinaturaAlmox(), request.assinaturaEquipe());
        OperacaoSolicitacaoMaterial operacao = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, request.idEquipe(),
                idColaboradorEfetivo, saqueForaEquipeEfetivo, idUsuarioOperador,
                null, assinaturasArmazenadas.almox(), assinaturasArmazenadas.equipe(),
                Objects.requireNonNullElse(origem, OrigemComandoTecnico.UI), null);
        for (int indice = 0; indice < itens.size(); indice++) {
            ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest itemRequest = request.itens().get(indice);
            operacao.adicionarItem(itens.get(indice), itemRequest.quantidade(),
                    itemRequest.numeroSerie(), CondicaoMaterial.NOVO, null);
        }
        operacao = operacaoRepository.saveAndFlush(operacao);

        for (ItemOperacaoSolicitacao itemOperacao : operacao.getItens()) {
            ItemSolicitacao item = itemOperacao.getItemSolicitacao();
            item.registrarEntregaTecnica(itemOperacao.getQuantidade());
            transferirSaldo(
                    solicitacao.getAlmoxarifadoAtendimento(), estoqueEquipe,
                    itemOperacao, CondicaoMaterial.NOVO);
            movimentacaoRepository.save(MovimentacaoEstoque.criarTecnica(
                    solicitacao.getAlmoxarifadoAtendimento(), estoqueEquipe, itemOperacao,
                    MovimentacaoEstoque.TipoMovimentacao.ENTREGA,
                    CondicaoMaterial.NOVO, idUsuarioOperador));
        }
        solicitacao.confirmarEntregaTecnica();
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao, TipoEventoSolicitacao.ENTREGA_CONFIRMADA,
                new PayloadEventoSolicitacao(
                        "CONFIRMAR_ENTREGA",
                        alteracoesOperacionais(operacao, statusAnterior, TipoOperacaoSolicitacao.ENTREGA, false),
                        Map.of(
                                "idOperacao", operacao.getId(),
                                // Indica somente a presenca dos comprovantes/assinaturas, nunca seus valores.
                                "comprovantesRegistrados", true)),
                contextoEvento(idUsuarioOperador, origem));
        publicadorEventos.publicarFilho(
                SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_ENTREGUE,
                solicitacao, operacao.getId(), null);
        registrarResultado("ENTREGA_TECNICA", idSolicitacao, idUsuarioOperador, chaveIdempotencia, request, operacao.getId());
        return operacao.getId();
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
    public UUID devolver(
            UUID idSolicitacao,
            ConfirmarDevolucaoTecnicaRequest request,
            UUID idUsuarioOperador,
            OrigemComandoTecnico origem
    ) {
        return devolver(idSolicitacao, request, idUsuarioOperador, origem, null);
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID devolver(
            UUID idSolicitacao,
            ConfirmarDevolucaoTecnicaRequest request,
            UUID idUsuarioOperador,
            OrigemComandoTecnico origem,
            String chaveIdempotencia
    ) {
        Objects.requireNonNull(request, "Os dados da devolucao tecnica sao obrigatorios");
        Objects.requireNonNull(idUsuarioOperador, "O usuario operador e obrigatorio");
        UUID repetida = resultadoRepetido("DEVOLUCAO_TECNICA", idSolicitacao, idUsuarioOperador,
                chaveIdempotencia, request);
        if (repetida != null) {
            return repetida;
        }
        SolicitacaoMaterial solicitacao = carregarTecnica(
                idSolicitacao, request.versaoEsperada(), ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO);

        if (solicitacao.getReserva() == null && request.numeroReserva() != null && !request.numeroReserva().isBlank()) {
            LocalDate dataReserva = request.dataReserva() != null ? request.dataReserva() : LocalDate.now();
            reservaUseCases.registrarOuAlterar(
                    idSolicitacao,
                    new RegistrarReservaTecnicaRequest(solicitacao.getVersao(), request.numeroReserva().trim(), dataReserva, request.idAgenteReserva(), null),
                    idUsuarioOperador,
                    origem
            );
            solicitacao = carregarTecnica(
                    idSolicitacao, null, ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO);
            reservaRepository.findBySolicitacaoId(idSolicitacao)
                    .ifPresent(solicitacao::associarReserva);
        }

        validarDevolucao(solicitacao, request);
        referenciaValidator.validar(
                solicitacao.getIdObra(), solicitacao.getOcorrencia(), request.idEquipe(), null);
        Estoque estoqueEquipe = estoqueCampoEquipeService.resolver(request.idEquipe());
        List<ItemSolicitacao> itens = validarItensDevolucao(solicitacao, request.itens());
        SolicitacaoMaterial.StatusSolicitacao statusAnterior = solicitacao.getStatus();

        OperacaoSolicitacaoMaterial operacao = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.DEVOLUCAO, request.idEquipe(), idUsuarioOperador,
                null, null, null, Objects.requireNonNullElse(origem, OrigemComandoTecnico.UI), null);
        for (int indice = 0; indice < itens.size(); indice++) {
            ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest itemRequest = request.itens().get(indice);
            operacao.adicionarItem(itens.get(indice), itemRequest.quantidade(),
                    itemRequest.numeroSerie(), itemRequest.condicao(), null);
        }
        operacao = operacaoRepository.saveAndFlush(operacao);

        boolean semReserva = solicitacao.getReserva() == null;
        MotivoPendencia motivoPendenteReserva = semReserva ? obterOuCriarMotivoPendenteReserva() : null;

        for (ItemOperacaoSolicitacao itemOperacao : operacao.getItens()) {
            ItemSolicitacao item = itemOperacao.getItemSolicitacao();
            item.registrarDevolucaoTecnica(itemOperacao.getQuantidade());
            if (semReserva) {
                item.registrarPendencia(motivoPendenteReserva);
            }
            transferirSaldo(
                    estoqueEquipe, solicitacao.getAlmoxarifadoAtendimento(),
                    itemOperacao, itemOperacao.getCondicao());
            movimentacaoRepository.save(MovimentacaoEstoque.criarTecnica(
                    estoqueEquipe, solicitacao.getAlmoxarifadoAtendimento(), itemOperacao,
                    MovimentacaoEstoque.TipoMovimentacao.DEVOLUCAO,
                    itemOperacao.getCondicao(), idUsuarioOperador));
        }
        if (semReserva) {
            if (solicitacao.getItens() != null) {
                for (ItemSolicitacao it : solicitacao.getItens()) {
                    it.registrarPendencia(motivoPendenteReserva);
                }
            }
            solicitacao.registrarPendencia(motivoPendenteReserva);
        }
        solicitacao.confirmarDevolucaoTecnica();
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao, TipoEventoSolicitacao.DEVOLUCAO_CONFIRMADA,
                new PayloadEventoSolicitacao(
                        "CONFIRMAR_DEVOLUCAO",
                        alteracoesOperacionais(operacao, statusAnterior, TipoOperacaoSolicitacao.DEVOLUCAO, false),
                        Map.of("idOperacao", operacao.getId())),
                contextoEvento(idUsuarioOperador, origem));
        publicadorEventos.publicarFilho(
                SolicitacaoMaterialTecnicaEvent.Tipo.DEVOLUCAO_RECEBIDA,
                solicitacao, operacao.getId(), null);
        registrarResultado("DEVOLUCAO_TECNICA", idSolicitacao, idUsuarioOperador, chaveIdempotencia, request, operacao.getId());
        return operacao.getId();
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID estornar(
            UUID idSolicitacao,
            EstornarOperacaoTecnicaRequest request,
            UUID idUsuarioOperador,
            OrigemComandoTecnico origem
    ) {
        return estornar(idSolicitacao, request, idUsuarioOperador, origem, null);
    }

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID estornar(
            UUID idSolicitacao,
            EstornarOperacaoTecnicaRequest request,
            UUID idUsuarioOperador,
            OrigemComandoTecnico origem,
            String chaveIdempotencia
    ) {
        Objects.requireNonNull(request, "Os dados do estorno tecnico sao obrigatorios");
        Objects.requireNonNull(idUsuarioOperador, "O usuario operador e obrigatorio");
        UUID repetida = resultadoRepetido("ESTORNO_TECNICO", idSolicitacao, idUsuarioOperador,
                chaveIdempotencia, request);
        if (repetida != null) {
            return repetida;
        }
        SolicitacaoMaterial solicitacao = carregarTecnica(
                idSolicitacao, request.versaoEsperada(), ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO);
        OperacaoSolicitacaoMaterial original = operacaoRepository.findById(request.idOperacaoOriginal())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Operacao original nao encontrada"));
        validarOperacaoOriginal(solicitacao, original);
        SolicitacaoMaterial.StatusSolicitacao statusAnterior = solicitacao.getStatus();

        OperacaoSolicitacaoMaterial estorno = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ESTORNO, original.getIdEquipe(), idUsuarioOperador,
                request.motivo(), null, null, Objects.requireNonNullElse(origem, OrigemComandoTecnico.UI), original);
        adicionarItensEstorno(estorno, original, request.itens());
        estorno = operacaoRepository.saveAndFlush(estorno);

        for (ItemOperacaoSolicitacao itemEstorno : estorno.getItens()) {
            aplicarEstornoNaCustodiaEquipe(solicitacao, original, itemEstorno);
        }
        solicitacao.confirmarEstornoTecnico();
        solicitacaoRepository.save(solicitacao);
        gravadorEvento.registrarFilho(
                solicitacao, TipoEventoSolicitacao.ESTORNO_CONFIRMADO,
                new PayloadEventoSolicitacao(
                        "CONFIRMAR_ESTORNO",
                        alteracoesOperacionais(estorno, statusAnterior, original.getTipo(), true),
                        Map.of("idOperacao", estorno.getId(), "idOperacaoOriginal", original.getId())),
                contextoEvento(idUsuarioOperador, origem));
        publicadorEventos.publicarFilho(
                SolicitacaoMaterialTecnicaEvent.Tipo.ESTORNO_TECNICO,
                solicitacao, estorno.getId(), original.getId());
        registrarResultado("ESTORNO_TECNICO", idSolicitacao, idUsuarioOperador, chaveIdempotencia, request, estorno.getId());
        return estorno.getId();
    }

    private UUID resultadoRepetido(
            String escopo,
            UUID idSolicitacao,
            UUID idUsuario,
            String chave,
            Object payload
    ) {
        if (chave == null) {
            return null;
        }
        validarChaveIdempotencia(chave);
        String hash = hashPayload(escopo, idSolicitacao, payload);
        return comandoRepository.findByEscopoAndIdUsuarioAndChave(escopo, idUsuario, chave)
                .map(comando -> {
                    if (!comando.possuiMesmoPayload(hash)) {
                        observabilidade.registrarConflito(
                                operacaoObservavel(escopo),
                                ObservabilidadeSolicitacaoTecnica.TipoConflito.IDEMPOTENCIA);
                        throw new InvarianteVioladaException("A chave de idempotencia ja foi usada com outro payload.");
                    }
                    String idOperacao = comando.getResposta().get("idOperacao");
                    if (idOperacao == null) {
                        throw new InvarianteVioladaException("O resultado idempotente da operacao esta invalido.");
                    }
                    return UUID.fromString(idOperacao);
                })
                .orElse(null);
    }

    private void registrarResultado(
            String escopo,
            UUID idSolicitacao,
            UUID idUsuario,
            String chave,
            Object payload,
            UUID idOperacao
    ) {
        if (chave == null) {
            return;
        }
        comandoRepository.save(new ComandoIdempotenteSolicitacao(
                escopo, idUsuario, chave, hashPayload(escopo, idSolicitacao, payload),
                idSolicitacao, Map.of("idOperacao", idOperacao.toString())));
    }

    private void validarChaveIdempotencia(String chave) {
        if (chave.isBlank() || chave.length() > 200) {
            throw new InvarianteVioladaException("A chave de idempotencia e obrigatoria e deve ter ate 200 caracteres.");
        }
    }

    private String hashPayload(String escopo, UUID idSolicitacao, Object payload) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((escopo + "|" + idSolicitacao + "|" + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder resultado = new StringBuilder(64);
            for (byte valor : bytes) {
                resultado.append(String.format("%02x", valor));
            }
            return resultado.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    private void validarOperacaoOriginal(
            SolicitacaoMaterial solicitacao,
            OperacaoSolicitacaoMaterial original
    ) {
        if (!Objects.equals(original.getSolicitacao().getId(), solicitacao.getId())
                || (original.getTipo() != TipoOperacaoSolicitacao.ENTREGA
                && original.getTipo() != TipoOperacaoSolicitacao.DEVOLUCAO)) {
            throw new InvarianteVioladaException(
                    "O estorno deve referenciar uma entrega ou devolucao da mesma solicitacao.");
        }
    }

    private void adicionarItensEstorno(
            OperacaoSolicitacaoMaterial estorno,
            OperacaoSolicitacaoMaterial original,
            List<EstornarOperacaoTecnicaRequest.ItemEstornoTecnicoRequest> requisicoes
    ) {
        if (requisicoes == null || requisicoes.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um item para estorno tecnico.");
        }
        Set<UUID> ids = new HashSet<>();
        for (EstornarOperacaoTecnicaRequest.ItemEstornoTecnicoRequest requisicao : requisicoes) {
            if (!ids.add(requisicao.idItemOperacaoOriginal())) {
                throw new InvarianteVioladaException("Um item operacional nao pode ser estornado mais de uma vez.");
            }
            ItemOperacaoSolicitacao itemOriginal = original.getItens().stream()
                    .filter(item -> Objects.equals(item.getId(), requisicao.idItemOperacaoOriginal()))
                    .findFirst()
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Item da operacao original nao encontrado"));
            validarQuantidadeEstornavel(itemOriginal, requisicao.quantidade());
            validarSerieEstorno(itemOriginal, requisicao.numeroSerie());
            estorno.adicionarItem(
                    itemOriginal.getItemSolicitacao(), requisicao.quantidade(), itemOriginal.getNumeroSerie(), itemOriginal);
        }
    }

    private void validarQuantidadeEstornavel(ItemOperacaoSolicitacao itemOriginal, BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new InvarianteVioladaException("A quantidade do estorno deve ser positiva.");
        }
        BigDecimal jaEstornada = Objects.requireNonNullElse(
                itemOperacaoRepository.somarQuantidadeEstornadaPorItemOriginal(itemOriginal.getId()), BigDecimal.ZERO);
        if (quantidade.add(jaEstornada).compareTo(itemOriginal.getQuantidade()) > 0) {
            throw new InvarianteVioladaException("O estorno excede a quantidade ainda reversivel da operacao original.");
        }
    }

    private void validarSerieEstorno(ItemOperacaoSolicitacao itemOriginal, String numeroSerie) {
        if (!itemOriginal.getMaterial().isDetalhaSerie()) {
            if (numeroSerie != null && !numeroSerie.isBlank()) {
                throw new InvarianteVioladaException("Material nao seriado nao aceita numero de serie no estorno.");
            }
            return;
        }
        if (numeroSerie == null || !numeroSerie.trim().equals(itemOriginal.getNumeroSerie())) {
            throw new InvarianteVioladaException("O estorno deve informar a mesma serie da operacao original.");
        }
    }

    private void aplicarEstornoNaCustodia(
            SolicitacaoMaterial solicitacao,
            OperacaoSolicitacaoMaterial original,
            ItemOperacaoSolicitacao itemEstorno,
            Map<UUID, Saldo> saldosPorMaterial,
            Map<ChaveLockSerie, SaldoSerie> seriesPorMaterial
    ) {
        ItemSolicitacao item = itemEstorno.getItemSolicitacao();
        if (original.getTipo() == TipoOperacaoSolicitacao.ENTREGA) {
            item.registrarEstornoEntregaTecnica(itemEstorno.getQuantidade());
            removerCustodiaDaObra(itemEstorno, saldosPorMaterial, seriesPorMaterial);
            movimentacaoRepository.save(MovimentacaoEstoque.criarTecnica(
                    solicitacao.getEstoqueDestino(), null, itemEstorno,
                    MovimentacaoEstoque.TipoMovimentacao.ESTORNO, itemEstorno.getOperacao().getIdUsuarioOperador()));
            return;
        }
        item.registrarEstornoDevolucaoTecnica(itemEstorno.getQuantidade());
        recomporCustodiaDaObra(itemEstorno, saldosPorMaterial, seriesPorMaterial);
        movimentacaoRepository.save(MovimentacaoEstoque.criarTecnica(
                null, solicitacao.getEstoqueOrigem(), itemEstorno,
                MovimentacaoEstoque.TipoMovimentacao.ESTORNO, itemEstorno.getOperacao().getIdUsuarioOperador()));
    }

    private void aplicarEstornoNaCustodiaEquipe(
            SolicitacaoMaterial solicitacao,
            OperacaoSolicitacaoMaterial original,
            ItemOperacaoSolicitacao itemEstorno
    ) {
        Estoque estoqueEquipe = estoqueCampoEquipeService.resolver(original.getIdEquipe());
        Estoque atendimento = solicitacao.getAlmoxarifadoAtendimento();
        ItemSolicitacao item = itemEstorno.getItemSolicitacao();
        CondicaoMaterial condicao = itemEstorno.getCondicao();

        if (original.getTipo() == TipoOperacaoSolicitacao.ENTREGA) {
            item.registrarEstornoEntregaTecnica(itemEstorno.getQuantidade());
            transferirSaldo(estoqueEquipe, atendimento, itemEstorno, condicao);
            movimentacaoRepository.save(MovimentacaoEstoque.criarTecnica(
                    estoqueEquipe, atendimento, itemEstorno,
                    MovimentacaoEstoque.TipoMovimentacao.ESTORNO, condicao,
                    itemEstorno.getOperacao().getIdUsuarioOperador()));
            return;
        }

        item.registrarEstornoDevolucaoTecnica(itemEstorno.getQuantidade());
        transferirSaldo(atendimento, estoqueEquipe, itemEstorno, condicao);
        movimentacaoRepository.save(MovimentacaoEstoque.criarTecnica(
                atendimento, estoqueEquipe, itemEstorno,
                MovimentacaoEstoque.TipoMovimentacao.ESTORNO, condicao,
                itemEstorno.getOperacao().getIdUsuarioOperador()));
    }

    private void removerCustodiaDaObra(
            ItemOperacaoSolicitacao itemEstorno,
            Map<UUID, Saldo> saldosPorMaterial,
            Map<ChaveLockSerie, SaldoSerie> seriesPorMaterial
    ) {
        Saldo saldo = saldoBloqueado(itemEstorno, saldosPorMaterial);
        saldo.removerQuantidade(itemEstorno.getQuantidade());
        saldoRepository.save(saldo);
        if (itemEstorno.getMaterial().isDetalhaSerie()) {
            SaldoSerie serie = serieBloqueada(itemEstorno, seriesPorMaterial);
            serie.desativar();
            saldoSerieRepository.save(serie);
        }
    }

    private void recomporCustodiaDaObra(
            ItemOperacaoSolicitacao itemEstorno,
            Map<UUID, Saldo> saldosPorMaterial,
            Map<ChaveLockSerie, SaldoSerie> seriesPorMaterial
    ) {
        Saldo saldo = saldoBloqueado(itemEstorno, saldosPorMaterial);
        saldo.adicionarQuantidade(itemEstorno.getQuantidade());
        saldoRepository.save(saldo);
        if (itemEstorno.getMaterial().isDetalhaSerie()) {
            SaldoSerie serie = serieBloqueada(itemEstorno, seriesPorMaterial);
            serie.ativar();
            saldoSerieRepository.save(serie);
        }
    }

    private Map<UUID, Saldo> bloquearSaldosParaEstorno(
            SolicitacaoMaterial solicitacao,
            OperacaoSolicitacaoMaterial original,
            List<ItemOperacaoSolicitacao> itensEstorno
    ) {
        boolean reduzCustodia = original.getTipo() == TipoOperacaoSolicitacao.ENTREGA;
        Estoque estoqueObra = reduzCustodia
                ? solicitacao.getEstoqueDestino() : solicitacao.getEstoqueOrigem();
        Map<UUID, BigDecimal> totaisPorMaterial = new LinkedHashMap<>();
        itensEstorno.forEach(item -> totaisPorMaterial.merge(
                item.getMaterial().getId(), item.getQuantidade(), BigDecimal::add));
        Map<UUID, Saldo> saldos = new LinkedHashMap<>();
        for (UUID idMaterial : ordenarMateriaisParaLock(totaisPorMaterial.keySet())) {
            java.util.Optional<Saldo> encontrado = buscarSaldoComLock(
                    estoqueObra.getId(), idMaterial, ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO);
            Saldo saldo;
            if (reduzCustodia) {
                saldo = encontrado.orElseThrow(() -> custodiaInsuficiente(
                        ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO,
                        ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SALDO,
                        "Saldo insuficiente na obra para estorno de entrega."));
                if (saldo.getQuantidade().compareTo(totaisPorMaterial.get(idMaterial)) < 0) {
                    throw custodiaInsuficiente(
                            ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO,
                            ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SALDO,
                            "Saldo insuficiente na obra para estorno de entrega.");
                }
            } else {
                Material material = itensEstorno.stream()
                        .map(ItemOperacaoSolicitacao::getMaterial)
                        .filter(item -> item.getId().equals(idMaterial))
                        .findFirst()
                        .orElseThrow();
                saldo = encontrado.orElseGet(() -> new Saldo(estoqueObra, material, CondicaoMaterial.NOVO));
            }
            saldos.put(idMaterial, saldo);
        }
        return saldos;
    }

    private Map<ChaveLockSerie, SaldoSerie> bloquearSeriesParaEstorno(
            SolicitacaoMaterial solicitacao,
            OperacaoSolicitacaoMaterial original,
            List<ItemOperacaoSolicitacao> itensEstorno
    ) {
        boolean reduzCustodia = original.getTipo() == TipoOperacaoSolicitacao.ENTREGA;
        Estoque estoqueObra = reduzCustodia
                ? solicitacao.getEstoqueDestino() : solicitacao.getEstoqueOrigem();
        List<ChaveLockSerie> chaves = itensEstorno.stream()
                .filter(item -> item.getMaterial().isDetalhaSerie())
                .map(item -> new ChaveLockSerie(item.getMaterial().getId(), item.getNumeroSerie()))
                .toList();
        Map<ChaveLockSerie, SaldoSerie> series = new LinkedHashMap<>();
        for (ChaveLockSerie chave : ordenarSeriesParaLock(chaves)) {
            SaldoSerie serie = buscarSerieComLock(estoqueObra.getId(), chave, reduzCustodia,
                            ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO)
                    .orElseThrow(() -> custodiaInsuficiente(
                            ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO,
                            ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SERIE,
                            reduzCustodia
                                    ? "A serie nao esta sob custodia da equipe."
                                    : "A serie original nao foi encontrada na obra."));
            if (!reduzCustodia && serie.isAtivo()) {
                throw custodiaInsuficiente(
                        ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO,
                        ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SERIE,
                        "A serie ja esta ativa na obra.");
            }
            series.put(chave, serie);
        }
        return series;
    }

    private java.util.Optional<SaldoSerie> buscarSerieComLock(
            UUID idEstoque,
            ChaveLockSerie chave,
            boolean apenasAtiva,
            ObservabilidadeSolicitacaoTecnica.Operacao operacao
    ) {
        try {
            if (apenasAtiva) {
                return saldoSerieRepository
                    .findWithLockByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                            idEstoque, chave.idMaterial(), chave.numeroSerie(), CondicaoMaterial.NOVO);
            }
            return saldoSerieRepository.findWithLockByEstoqueIdAndMaterialIdAndNumSerieAndCondicao(
                    idEstoque, chave.idMaterial(), chave.numeroSerie(), CondicaoMaterial.NOVO);
        } catch (RuntimeException ex) {
            observabilidade.registrarFalhaConcorrencia(operacao, ex);
            throw ex;
        }
    }

    private Saldo saldoBloqueado(
            ItemOperacaoSolicitacao item,
            Map<UUID, Saldo> saldosPorMaterial
    ) {
        Saldo saldo = saldosPorMaterial.get(item.getMaterial().getId());
        if (saldo == null) {
            throw new IllegalStateException("O saldo do material nao foi bloqueado antes do estorno.");
        }
        return saldo;
    }

    private SaldoSerie serieBloqueada(
            ItemOperacaoSolicitacao item,
            Map<ChaveLockSerie, SaldoSerie> seriesPorMaterial
    ) {
        SaldoSerie serie = seriesPorMaterial.get(
                new ChaveLockSerie(item.getMaterial().getId(), item.getNumeroSerie()));
        if (serie == null) {
            throw new IllegalStateException("A serie do material nao foi bloqueada antes do estorno.");
        }
        return serie;
    }

    private SolicitacaoMaterial carregarTecnica(
            UUID idSolicitacao,
            Long versaoEsperada,
            ObservabilidadeSolicitacaoTecnica.Operacao operacao
    ) {
        SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitacao material nao encontrada"));
        if (solicitacao.getContexto() != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("O comando exige solicitacao tecnica.");
        }
        if (versaoEsperada != null && !Objects.equals(solicitacao.getVersao(), versaoEsperada)) {
            observabilidade.registrarConflito(
                    operacao, ObservabilidadeSolicitacaoTecnica.TipoConflito.VERSAO);
            throw new ConflitoVersaoException(
                    ConflitoVersaoException.Recurso.SOLICITACAO,
                    versaoEsperada, solicitacao.getVersao());
        }
        return solicitacao;
    }

    private void validarEntrega(SolicitacaoMaterial solicitacao, ConfirmarEntregaTecnicaRequest request) {
        if (!solicitacao.getTipoTecnico().isAdicao()) {
            throw new InvarianteVioladaException("Entrega tecnica se aplica somente aos tipos 221 e 921.");
        }
        if (solicitacao.getDataPrevistaRetirada() == null) {
            throw new InvarianteVioladaException("Defina a data prevista antes de confirmar a entrega tecnica.");
        }
        if (solicitacao.getStatusSeparacao() != StatusSeparacao.SEPARADO) {
            throw new InvarianteVioladaException("Conclua a separacao antes de confirmar a entrega tecnica.");
        }
        exigirReserva(solicitacao, ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA);
        if (request.idEquipe() == null) {
            throw new InvarianteVioladaException("A equipe e obrigatoria para confirmar a entrega tecnica.");
        }
    }

    private void validarDevolucao(SolicitacaoMaterial solicitacao, ConfirmarDevolucaoTecnicaRequest request) {
        if (!solicitacao.getTipoTecnico().isDevolucao()) {
            throw new InvarianteVioladaException("Devolucao tecnica se aplica somente aos tipos 222 e 922.");
        }
        if (request.idEquipe() == null) {
            throw new InvarianteVioladaException("A equipe e obrigatoria para confirmar a devolucao tecnica.");
        }
    }

    private List<ItemSolicitacao> validarItensEntrega(
            SolicitacaoMaterial solicitacao,
            List<ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest> itens
    ) {
        if (itens == null || itens.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um item para entrega tecnica.");
        }
        Set<UUID> idsNaoSeriados = new HashSet<>();
        Set<String> series = new HashSet<>();
        Map<UUID, BigDecimal> quantidadesPorItem = new LinkedHashMap<>();

        List<ItemSolicitacao> itensMapeados = new ArrayList<>(itens.size());
        for (ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest itemRequest : itens) {
            if (itemRequest.idItemSolicitacao() == null) {
                throw new InvarianteVioladaException("O identificador do item da solicitacao e obrigatorio.");
            }
            ItemSolicitacao item = solicitacao.getItens().stream()
                    .filter(candidato -> Objects.equals(candidato.getId(), itemRequest.idItemSolicitacao()))
                    .findFirst()
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Item da solicitacao nao encontrado"));

            if (itemRequest.quantidade() == null || itemRequest.quantidade().signum() <= 0) {
                throw new InvarianteVioladaException("A quantidade da entrega deve ser positiva.");
            }

            if (!item.getMaterial().isDetalhaSerie()) {
                if (!idsNaoSeriados.add(itemRequest.idItemSolicitacao())) {
                    throw new InvarianteVioladaException("Um item nao pode ser informado mais de uma vez na entrega.");
                }
            }

            validarSerieParaEntrega(solicitacao, item, itemRequest, series);

            quantidadesPorItem.merge(item.getId(), itemRequest.quantidade(), BigDecimal::add);
            itensMapeados.add(item);
        }

        quantidadesPorItem.forEach((idItem, totalQtd) -> {
            ItemSolicitacao item = solicitacao.getItens().stream()
                    .filter(candidato -> Objects.equals(candidato.getId(), idItem))
                    .findFirst()
                    .orElseThrow();
            if (totalQtd.compareTo(item.getQuantidadePendente()) > 0) {
                throw new InvarianteVioladaException("A quantidade da entrega excede o saldo pendente do item.");
            }
        });

        return itensMapeados;
    }

    private List<ItemSolicitacao> validarItensDevolucao(
            SolicitacaoMaterial solicitacao,
            List<ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest> itens
    ) {
        if (itens == null || itens.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um item para devolucao tecnica.");
        }
        Set<UUID> idsNaoSeriados = new HashSet<>();
        Set<String> series = new HashSet<>();
        Map<UUID, BigDecimal> quantidadesPorItem = new LinkedHashMap<>();

        List<ItemSolicitacao> itensMapeados = new ArrayList<>(itens.size());
        for (ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest itemRequest : itens) {
            if (itemRequest.idItemSolicitacao() == null) {
                throw new InvarianteVioladaException("O identificador do item da solicitacao e obrigatorio.");
            }
            ItemSolicitacao item = solicitacao.getItens().stream()
                    .filter(candidato -> Objects.equals(candidato.getId(), itemRequest.idItemSolicitacao()))
                    .findFirst()
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Item da solicitacao nao encontrado"));

            if (itemRequest.quantidade() == null || itemRequest.quantidade().signum() <= 0) {
                throw new InvarianteVioladaException("A quantidade da devolucao deve ser positiva.");
            }

            if (!item.getMaterial().isDetalhaSerie()) {
                if (!idsNaoSeriados.add(itemRequest.idItemSolicitacao())) {
                    throw new InvarianteVioladaException("Um item nao pode ser informado mais de uma vez na devolucao.");
                }
            }

            if (item.getMaterial().isDetalhaSerie()) {
                if (itemRequest.quantidade().compareTo(BigDecimal.ONE) != 0
                        || itemRequest.numeroSerie() == null || itemRequest.numeroSerie().isBlank()) {
                    throw new InvarianteVioladaException("Material seriado exige quantidade 1 e numero de serie.");
                }
                String serie = itemRequest.numeroSerie().trim();
                if (!series.add(serie)) {
                    throw new InvarianteVioladaException("A serie informada nao esta sob custodia da equipe.");
                }
            }

            quantidadesPorItem.merge(item.getId(), itemRequest.quantidade(), BigDecimal::add);
            itensMapeados.add(item);
        }

        quantidadesPorItem.forEach((idItem, totalQtd) -> {
            ItemSolicitacao item = solicitacao.getItens().stream()
                    .filter(candidato -> Objects.equals(candidato.getId(), idItem))
                    .findFirst()
                    .orElseThrow();
            if (totalQtd.compareTo(item.getQuantidadePendente()) > 0) {
                throw new InvarianteVioladaException("A quantidade da devolucao excede o saldo pendente do item.");
            }
        });

        return itensMapeados;
    }

    private Map<UUID, Saldo> bloquearEValidarSaldosObra(
            SolicitacaoMaterial solicitacao,
            List<ItemSolicitacao> itens,
            List<ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest> requisicoes
    ) {
        Map<UUID, BigDecimal> totaisPorMaterial = new LinkedHashMap<>();
        for (int indice = 0; indice < itens.size(); indice++) {
            totaisPorMaterial.merge(itens.get(indice).getMaterial().getId(), requisicoes.get(indice).quantidade(), BigDecimal::add);
        }
        Map<UUID, Saldo> saldos = new LinkedHashMap<>();
        for (UUID idMaterial : ordenarMateriaisParaLock(totaisPorMaterial.keySet())) {
            BigDecimal quantidade = totaisPorMaterial.get(idMaterial);
            Saldo saldo = buscarSaldoComLock(
                            solicitacao.getEstoqueOrigem().getId(), idMaterial,
                            ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO)
                    .orElseThrow(() -> custodiaInsuficiente(
                            ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO,
                            ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SALDO,
                            "Saldo insuficiente na obra para devolucao tecnica."));
            if (saldo.getQuantidade().compareTo(quantidade) < 0) {
                throw custodiaInsuficiente(
                        ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO,
                        ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SALDO,
                        "Saldo insuficiente na obra para devolucao tecnica.");
            }
            saldos.put(idMaterial, saldo);
        }
        return saldos;
    }

    private Map<ChaveLockSerie, SaldoSerie> bloquearEValidarSeriesObra(
            SolicitacaoMaterial solicitacao,
            List<ItemSolicitacao> itens,
            List<ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest> requisicoes
    ) {
        List<ChaveLockSerie> chaves = new java.util.ArrayList<>();
        for (int indice = 0; indice < itens.size(); indice++) {
            if (itens.get(indice).getMaterial().isDetalhaSerie()) {
                chaves.add(new ChaveLockSerie(
                        itens.get(indice).getMaterial().getId(),
                        requisicoes.get(indice).numeroSerie().trim()));
            }
        }
        Map<ChaveLockSerie, SaldoSerie> series = new LinkedHashMap<>();
        for (ChaveLockSerie chave : ordenarSeriesParaLock(chaves)) {
            SaldoSerie serie;
            try {
                serie = saldoSerieRepository
                        .findWithLockByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                                solicitacao.getEstoqueOrigem().getId(), chave.idMaterial(),
                                chave.numeroSerie(), CondicaoMaterial.NOVO)
                        .orElseThrow(() -> custodiaInsuficiente(
                                ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO,
                                ObservabilidadeSolicitacaoTecnica.RecursoCustodia.SERIE,
                                "A serie informada nao esta sob custodia da equipe."));
            } catch (RuntimeException ex) {
                observabilidade.registrarFalhaConcorrencia(
                        ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO, ex);
                throw ex;
            }
            series.put(chave, serie);
        }
        return series;
    }

    static List<UUID> ordenarMateriaisParaLock(java.util.Collection<UUID> idsMateriais) {
        return idsMateriais.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }

    static List<ChaveLockSerie> ordenarSeriesParaLock(java.util.Collection<ChaveLockSerie> chaves) {
        return chaves.stream()
                .sorted(Comparator.comparing((ChaveLockSerie chave) -> chave.idMaterial().toString())
                        .thenComparing(ChaveLockSerie::numeroSerie))
                .toList();
    }

    private java.util.Optional<Saldo> buscarSaldoComLock(
            UUID idEstoque,
            UUID idMaterial,
            ObservabilidadeSolicitacaoTecnica.Operacao operacao
    ) {
        try {
            return saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(
                    idEstoque, idMaterial, CondicaoMaterial.NOVO);
        } catch (RuntimeException ex) {
            observabilidade.registrarFalhaConcorrencia(operacao, ex);
            throw ex;
        }
    }

    private InvarianteVioladaException custodiaInsuficiente(
            ObservabilidadeSolicitacaoTecnica.Operacao operacao,
            ObservabilidadeSolicitacaoTecnica.RecursoCustodia recurso,
            String mensagem
    ) {
        observabilidade.registrarCustodiaInsuficiente(operacao, recurso);
        return new InvarianteVioladaException(mensagem);
    }

    private void validarSerieParaEntrega(
            SolicitacaoMaterial solicitacao,
            ItemSolicitacao item,
            ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest itemRequest,
            Set<String> series
    ) {
        if (!item.getMaterial().isDetalhaSerie()) {
            return;
        }
        if (itemRequest.quantidade().compareTo(BigDecimal.ONE) != 0
                || itemRequest.numeroSerie() == null || itemRequest.numeroSerie().isBlank()) {
            throw new InvarianteVioladaException("Material seriado exige quantidade 1 e numero de serie.");
        }
        String serie = itemRequest.numeroSerie().trim();
        if (!series.add(serie)) {
            throw new InvarianteVioladaException("A serie foi informada mais de uma vez na entrega.");
        }
    }

    private void transferirSaldo(
            Estoque origem,
            Estoque destino,
            ItemOperacaoSolicitacao itemOperacao,
            CondicaoMaterial condicao
    ) {
        debitoSaldoService.debitar(
                origem, itemOperacao.getMaterial(), condicao, itemOperacao.getQuantidade());
        Saldo saldoDestino = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                        destino.getId(), itemOperacao.getMaterial().getId(), condicao)
                .orElseGet(() -> new Saldo(destino, itemOperacao.getMaterial(), condicao));
        saldoDestino.adicionarQuantidade(itemOperacao.getQuantidade());
        saldoRepository.save(saldoDestino);

        if (itemOperacao.getMaterial().isDetalhaSerie()) {
            SaldoSerie serie = saldoSerieRepository
                    .findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                            origem.getId(), itemOperacao.getMaterial().getId(),
                            itemOperacao.getNumeroSerie(), condicao)
                    .orElseThrow(() -> new InvarianteVioladaException(
                            "A serie informada nao esta no estoque e na condicao de origem."));
            serie.transferirPara(destino);
            saldoSerieRepository.save(serie);
        }
    }

    private void adicionarCustodiaObra(SolicitacaoMaterial solicitacao, ItemOperacaoSolicitacao itemOperacao) {
        Saldo saldo = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                        solicitacao.getEstoqueDestino().getId(), itemOperacao.getMaterial().getId(), CondicaoMaterial.NOVO)
                .orElseGet(() -> new Saldo(solicitacao.getEstoqueDestino(), itemOperacao.getMaterial(), CondicaoMaterial.NOVO));
        saldo.adicionarQuantidade(itemOperacao.getQuantidade());
        saldoRepository.save(saldo);
        if (itemOperacao.getMaterial().isDetalhaSerie()) {
            saldoSerieRepository.save(new SaldoSerie(
                    solicitacao.getEstoqueDestino(), itemOperacao.getMaterial(),
                    itemOperacao.getNumeroSerie(), CondicaoMaterial.NOVO));
        }
    }

    private void baixarSerieDaObra(
            ItemOperacaoSolicitacao itemOperacao,
            Map<ChaveLockSerie, SaldoSerie> seriesPorMaterial
    ) {
        if (!itemOperacao.getMaterial().isDetalhaSerie()) {
            return;
        }
        SaldoSerie serie = seriesPorMaterial.get(new ChaveLockSerie(
                itemOperacao.getMaterial().getId(), itemOperacao.getNumeroSerie()));
        if (serie == null) {
            throw new IllegalStateException("A serie da devolucao nao foi bloqueada antes da operacao.");
        }
        serie.desativar();
        saldoSerieRepository.save(serie);
    }

    private ContextoRegistroEventoSolicitacao contextoEvento(UUID idUsuario, OrigemComandoTecnico origem) {
        return new ContextoRegistroEventoSolicitacao(
                idUsuario, "USUARIO " + idUsuario, Objects.requireNonNullElse(origem, OrigemComandoTecnico.UI), null);
    }

    private List<AlteracaoEventoSolicitacao> alteracoesOperacionais(
            OperacaoSolicitacaoMaterial operacao,
            SolicitacaoMaterial.StatusSolicitacao statusAnterior,
            TipoOperacaoSolicitacao tipoOriginal,
            boolean estorno
    ) {
        List<AlteracaoEventoSolicitacao> alteracoes = new ArrayList<>();
        String campoQuantidade = tipoOriginal == TipoOperacaoSolicitacao.ENTREGA
                ? "quantidadeEntregaLiquida" : "quantidadeDevolucaoLiquida";
        Map<ItemSolicitacao, BigDecimal> totaisPorItem = new LinkedHashMap<>();
        for (ItemOperacaoSolicitacao itemOperacao : operacao.getItens()) {
            totaisPorItem.merge(itemOperacao.getItemSolicitacao(), itemOperacao.getQuantidade(), BigDecimal::add);
        }
        totaisPorItem.forEach((item, totalOperado) -> {
            BigDecimal atual = tipoOriginal == TipoOperacaoSolicitacao.ENTREGA
                    ? item.getQuantidadeEntregaLiquida() : item.getQuantidadeDevolucaoLiquida();
            BigDecimal anterior = estorno
                    ? atual.add(totalOperado)
                    : atual.subtract(totalOperado);
            alteracoes.add(new AlteracaoEventoSolicitacao(
                    "itens[" + item.getId() + "]." + campoQuantidade,
                    anterior, atual));
            BigDecimal pendenteAtual = item.getQuantidadePendente();
            BigDecimal pendenteAnterior = estorno
                    ? pendenteAtual.subtract(totalOperado)
                    : pendenteAtual.add(totalOperado);
            alteracoes.add(new AlteracaoEventoSolicitacao(
                    "itens[" + item.getId() + "].quantidadePendente",
                    pendenteAnterior, pendenteAtual));
        });
        if (statusAnterior != operacao.getSolicitacao().getStatus()) {
            alteracoes.add(new AlteracaoEventoSolicitacao(
                    "status", statusAnterior, operacao.getSolicitacao().getStatus()));
        }
        return alteracoes;
    }

    private void exigirReserva(
            SolicitacaoMaterial solicitacao,
            ObservabilidadeSolicitacaoTecnica.Operacao operacao
    ) {
        if (solicitacao.isExcecaoReserva()) {
            return;
        }
        if (solicitacao.getReserva() == null) {
            observabilidade.registrarReservaInvalida(
                    operacao, ObservabilidadeSolicitacaoTecnica.MotivoReservaInvalida.AUSENTE);
        }
        solicitacao.exigirReservaParaMovimentacaoTecnica();
    }

    private ObservabilidadeSolicitacaoTecnica.Operacao operacaoObservavel(String escopo) {
        return switch (escopo) {
            case "ENTREGA_TECNICA" -> ObservabilidadeSolicitacaoTecnica.Operacao.ENTREGA;
            case "DEVOLUCAO_TECNICA" -> ObservabilidadeSolicitacaoTecnica.Operacao.DEVOLUCAO;
            case "ESTORNO_TECNICO" -> ObservabilidadeSolicitacaoTecnica.Operacao.ESTORNO;
            default -> throw new IllegalArgumentException("Escopo tecnico de idempotencia desconhecido");
        };
    }

    private MotivoPendencia obterOuCriarMotivoPendenteReserva() {
        MotivoPendencia motivo = motivoPendenciaRepository.findByNomeIgnoreCase("Pendente Reserva")
                .orElseGet(() -> motivoPendenciaRepository.save(
                        new MotivoPendencia("Pendente Reserva", "Pendente Reserva")));
        if (!motivo.isAtivo()) {
            motivo.ativar();
            motivo = motivoPendenciaRepository.save(motivo);
        }
        return motivo;
    }

    record ChaveLockSerie(UUID idMaterial, String numeroSerie) {
    }
}
