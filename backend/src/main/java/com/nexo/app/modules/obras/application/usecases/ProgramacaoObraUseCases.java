package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ProgramacaoRequests;
import com.nexo.app.modules.obras.application.dto.ProgramacaoResponses;
import com.nexo.app.modules.obras.domain.EventoProgramacao;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.ProgramacaoItem;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import com.nexo.app.modules.obras.infrastructure.persistence.EventoProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemPlanejadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.VaoPlanejamentoRepository;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comandos da programação: programar, editar, reprogramar, cancelar, excluir e retirar item. Cada mudança depois
 * de criada grava um evento no histórico.
 */
@Service
@RequiredArgsConstructor
public class ProgramacaoObraUseCases {

    private final ProgramacaoRepository repository;
    private final EventoProgramacaoRepository eventoRepository;
    private final ItemPlanejadoRepository itemRepository;
    private final ObraRepository obraRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final VaoPlanejamentoRepository vaoRepository;
    private final KitConstrutivoUseCases kitUseCases;
    private final EquipeApi equipeApi;
    private final ProjecaoEquipesObra projecao;

    /** O que a seleção vira ao programar: os itens programáveis e o que fica de fora. */
    record Selecao(Set<ItemPlanejado> itens, int materiaisIncluidos, int ignoradosProgramados, int ignoradosConcluidos) {
    }

    @Transactional(readOnly = true)
    public ProgramacaoResponses.Previa previa(UUID idObra, ProgramacaoRequests.Previa request) {
        Obra obra = buscarObra(idObra);
        validarPeriodo(request.inicio(), request.fim());
        if (request.idProgramacao() != null) {
            Programacao programacao = buscarNaObra(idObra, request.idProgramacao());
            BigDecimal valor = programacao.valorTotal().setScale(2, RoundingMode.HALF_UP);
            List<ProgramacaoItem> itens = programacao.getItensAtivos();
            int servicos = (int) itens.stream().filter(v -> v.getItemPlanejado().getTipo() == TipoItemKit.SERVICO).count();
            return new ProgramacaoResponses.Previa(itens.size(), servicos, itens.size() - servicos, 0, 0, 0, 0, List.of(), valor,
                    obra.getDataPrevisaoFim(), obra.getPrazoRegulado(),
                    projecao.projetar(obra, request.inicio(), request.fim(), request.idBase(), request.idTipoEquipe(), valor, programacao.getId()));
        }
        Selecao selecao = selecionar(idObra, request.idsItens());
        Map<UUID, Map<UUID, BigDecimal>> valores = new HashMap<>();
        BigDecimal valor = BigDecimal.ZERO;
        int servicos = 0;
        int semValor = 0;
        Set<String> nomesSemValor = new LinkedHashSet<>();
        for (ItemPlanejado item : selecao.itens()) {
            if (item.getTipo() != TipoItemKit.SERVICO) {
                continue;
            }
            servicos++;
            BigDecimal unitario = valorNaData(item, request.inicio(), valores);
            if (unitario == null) {
                semValor++;
                nomesSemValor.add(item.getDescricao());
            } else {
                valor = valor.add(unitario.multiply(item.getQuantidadePrevista()));
            }
        }
        BigDecimal total = valor.setScale(2, RoundingMode.HALF_UP);
        return new ProgramacaoResponses.Previa(selecao.itens().size(), servicos, selecao.itens().size() - servicos,
                selecao.materiaisIncluidos(), selecao.ignoradosProgramados(), selecao.ignoradosConcluidos(), semValor,
                List.copyOf(nomesSemValor), total, obra.getDataPrevisaoFim(), obra.getPrazoRegulado(),
                projecao.projetar(obra, request.inicio(), request.fim(), request.idBase(), request.idTipoEquipe(), total, null));
    }

    @Transactional
    public ProgramacaoResponses.Resultado programar(UUID idObra, ProgramacaoRequests.Programar request) {
        Obra obra = buscarObra(idObra);
        if (obra.getStatusObra().isConcluida()) {
            throw new InvarianteVioladaException("A obra " + obra.getNumero() + " está encerrada e não pode ser programada.");
        }
        exigirEquipeAtiva(request.idEquipe());
        Selecao selecao = selecionar(idObra, request.idsItens());
        if (selecao.itens().isEmpty()) {
            throw new InvarianteVioladaException("Nenhum item pendente na seleção: todos já foram programados ou concluídos.");
        }
        Programacao programacao = new Programacao(repository.proximoNumero(), idObra, request.idEquipe(),
                request.inicio(), request.fim(), request.condicoes());
        Map<UUID, Map<UUID, BigDecimal>> valores = new HashMap<>();
        int semValor = 0;
        for (ItemPlanejado item : selecao.itens()) {
            BigDecimal unitario = item.getTipo() == TipoItemKit.SERVICO ? valorNaData(item, request.inicio(), valores) : null;
            if (item.getTipo() == TipoItemKit.SERVICO && unitario == null) {
                semValor++;
            }
            programacao.incluirItem(item, unitario);
        }
        obra.vincularEquipeSeAusente(request.idEquipe());
        Programacao salva = repository.saveAndFlush(programacao);
        return new ProgramacaoResponses.Resultado(detalhar(salva, obra), selecao.ignoradosProgramados(),
                selecao.ignoradosConcluidos(), selecao.materiaisIncluidos(), semValor);
    }

    @Transactional
    public ProgramacaoResponses.ProgramacaoDetalhe editar(UUID id, ProgramacaoRequests.Editar request) {
        Programacao programacao = buscar(id);
        conferirVersao(programacao, request.versao());
        Obra obra = buscarObra(programacao.getIdObra());
        programacao.editar(request.condicoes()).ifPresent(evento -> registrar(evento, obra));
        return detalhar(repository.saveAndFlush(programacao), obra);
    }

    @Transactional
    public ProgramacaoResponses.ProgramacaoDetalhe reprogramar(UUID id, ProgramacaoRequests.Reprogramar request) {
        Programacao programacao = buscar(id);
        conferirVersao(programacao, request.versao());
        exigirEquipeAtiva(request.idEquipe());
        Obra obra = buscarObra(programacao.getIdObra());
        registrar(programacao.reprogramar(request.idEquipe(), request.inicio(), request.fim(), request.observacao()), obra);
        obra.vincularEquipeSeAusente(request.idEquipe());
        return detalhar(repository.saveAndFlush(programacao), obra);
    }

    @Transactional
    public ProgramacaoResponses.ProgramacaoDetalhe cancelar(UUID id, ProgramacaoRequests.Encerrar request) {
        Programacao programacao = buscar(id);
        conferirVersao(programacao, request.versao());
        Obra obra = buscarObra(programacao.getIdObra());
        registrar(programacao.cancelar(request.observacao()), obra);
        return detalhar(repository.saveAndFlush(programacao), obra);
    }

    @Transactional
    public void excluir(UUID id, ProgramacaoRequests.Encerrar request) {
        Programacao programacao = buscar(id);
        conferirVersao(programacao, request != null ? request.versao() : null);
        registrar(programacao.excluir(request != null ? request.observacao() : null), buscarObra(programacao.getIdObra()));
    }

    /** @return a programação atualizada, ou vazio quando o último item saiu e ela foi excluída */
    @Transactional
    public Optional<ProgramacaoResponses.ProgramacaoDetalhe> retirarItem(UUID id, UUID idItem) {
        Programacao programacao = buscar(id);
        Obra obra = buscarObra(programacao.getIdObra());
        registrar(programacao.retirarItem(idItem), obra);
        return programacao.isAtivo() ? Optional.of(detalhar(repository.saveAndFlush(programacao), obra)) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public ProgramacaoResponses.ProgramacaoDetalhe buscarPorId(UUID id) {
        Programacao programacao = buscar(id);
        return detalhar(programacao, buscarObra(programacao.getIdObra()));
    }

    @Transactional(readOnly = true)
    public List<ProgramacaoResponses.ProgramacaoDetalhe> listarDaObra(UUID idObra) {
        Obra obra = buscarObra(idObra);
        return repository.listarDaObraComItens(idObra).stream().map(p -> detalhar(p, obra)).toList();
    }

    /**
     * Separa o que pode ser programado. O material acompanha o serviço: serviço selecionado traz os materiais
     * pendentes do mesmo kit, porque a árvore pode estar mostrando só serviços.
     */
    Selecao selecionar(UUID idObra, List<UUID> idsItens) {
        if (idsItens == null || idsItens.isEmpty()) {
            return new Selecao(Set.of(), 0, 0, 0);
        }
        List<ItemPlanejado> escolhidos = itemRepository.listarDaObra(idObra, idsItens);
        Set<ItemPlanejado> itens = new LinkedHashSet<>();
        int programados = 0;
        int concluidos = 0;
        for (ItemPlanejado item : escolhidos) {
            if (item.programavel()) {
                itens.add(item);
            } else if (item.concluido() || item.getKitAplicado().isExecutadoAntes()) {
                concluidos++;
            } else if (item.getStatus() != StatusItemPlanejado.PENDENTE) {
                programados++;
            }
        }
        int antes = itens.size();
        Set<KitAplicado> kits = itens.stream()
                .filter(i -> i.getTipo() == TipoItemKit.SERVICO)
                .map(ItemPlanejado::getKitAplicado)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (KitAplicado kit : kits) {
            kit.getItensAtivos().stream()
                    .filter(i -> i.getTipo() == TipoItemKit.MATERIAL && i.programavel())
                    .forEach(itens::add);
        }
        return new Selecao(itens, itens.size() - antes, programados, concluidos);
    }

    private BigDecimal valorNaData(ItemPlanejado item, LocalDate data, Map<UUID, Map<UUID, BigDecimal>> valores) {
        UUID contrato = item.getKitAplicado().getIdContrato();
        return valores.computeIfAbsent(contrato, id -> kitUseCases.valoresVigentes(id, data)).get(item.getIdAcao());
    }

    private void registrar(EventoProgramacao evento, Obra obra) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (evento.getIdEquipeOrigem() != null) {
            ids.add(evento.getIdEquipeOrigem());
        }
        if (evento.getIdEquipeDestino() != null) {
            ids.add(evento.getIdEquipeDestino());
        }
        Map<UUID, String> codigos = codigos(ids);
        evento.identificar(obra.getNumero(), codigos.get(evento.getIdEquipeOrigem()), codigos.get(evento.getIdEquipeDestino()));
        eventoRepository.save(evento);
    }

    private Map<UUID, String> codigos(Set<UUID> ids) {
        Map<UUID, String> codigos = new HashMap<>();
        equipeApi.buscarPorIdsResponse(ids).forEach(e -> codigos.put(e.id(), e.codigo()));
        return codigos;
    }

    private ProgramacaoResponses.ProgramacaoDetalhe detalhar(Programacao p, Obra obra) {
        Map<UUID, String> locais = locais(p.getIdObra());
        List<ProgramacaoResponses.ItemProgramado> itens = new ArrayList<>();
        for (ProgramacaoItem vinculo : p.getItensAtivos()) {
            ItemPlanejado item = vinculo.getItemPlanejado();
            KitAplicado kit = item.getKitAplicado();
            itens.add(new ProgramacaoResponses.ItemProgramado(item.getId(),
                    locais.get(kit.getIdPonto() != null ? kit.getIdPonto() : kit.getIdVao()), item.getTipo(), item.getCodigo(),
                    item.getDescricao(), item.getUnidade(), vinculo.getQuantidade(), item.getQuantidadeExecutada(),
                    vinculo.getValorUnitario(), vinculo.getValorTotal(), item.getStatus()));
        }
        return new ProgramacaoResponses.ProgramacaoDetalhe(p.getId(), p.getNumero(), p.getIdObra(), obra.getNumero(),
                obra.getTitulo(), p.getIdEquipe(), codigos(Set.of(p.getIdEquipe())).get(p.getIdEquipe()),
                p.getDataInicio(), p.getDataFim(), p.getDescricao(), p.isLinhaViva(), p.isDesligamento(), p.isFinalizaObra(),
                p.getTurno(), p.getSituacaoIntervencao(), p.getObservacao(), p.situacaoEm(LocalDate.now()),
                p.valorTotal().setScale(2, RoundingMode.HALF_UP), p.getUsuarioCriacao(), p.getDataCriacao(), p.getVersao(), itens);
    }

    Map<UUID, String> locais(UUID idObra) {
        Map<UUID, String> numeros = new HashMap<>();
        for (PontoPlanejamento ponto : pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(idObra)) {
            numeros.put(ponto.getId(), "P" + ponto.getNumero());
        }
        Map<UUID, String> locais = new HashMap<>(numeros);
        for (VaoPlanejamento vao : vaoRepository.findByIdObraAndAtivoTrue(idObra)) {
            locais.put(vao.getId(), numeros.get(vao.getIdPontoA()) + " — " + numeros.get(vao.getIdPontoB()));
        }
        return locais;
    }

    private Programacao buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Programação não encontrada."));
    }

    private Programacao buscarNaObra(UUID idObra, UUID id) {
        Programacao programacao = buscar(id);
        if (!Objects.equals(programacao.getIdObra(), idObra)) {
            throw new RecursoNaoEncontradoException("Programação não encontrada nesta obra.");
        }
        return programacao;
    }

    private Obra buscarObra(UUID idObra) {
        return obraRepository.findById(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));
    }

    private void exigirEquipeAtiva(UUID idEquipe) {
        if (equipeApi.buscarAtivaPorId(idEquipe).isEmpty()) {
            throw new InvarianteVioladaException("A equipe informada não existe ou está inativa.");
        }
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("O fim da programação não pode ser antes do início.");
        }
    }

    private static void conferirVersao(Programacao programacao, Long versao) {
        if (versao != null && !versao.equals(programacao.getVersao())) {
            throw new ObjectOptimisticLockingFailureException(Programacao.class, programacao.getId());
        }
    }
}
