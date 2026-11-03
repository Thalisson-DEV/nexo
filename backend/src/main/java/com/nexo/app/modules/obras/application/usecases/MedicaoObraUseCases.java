package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.MedicaoFilter;
import com.nexo.app.modules.obras.application.dto.MedicaoRequests;
import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.Medicao;
import com.nexo.app.modules.obras.domain.MedicaoItem;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.TipoMedicao;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemPlanejadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.MedicaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.MedicaoSpecification;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Medições das obras: o que está pendente de medição, gerar (parcial ou por ciclo), estornar e consultar.
 * Quantidade e valor de cada serviço são a soma dos apontamentos dele, já com o valor congelado na programação.
 */
@Service
@RequiredArgsConstructor
public class MedicaoObraUseCases {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final MedicaoRepository repository;
    private final ItemPlanejadoRepository itemRepository;
    private final ApontamentoExecucaoRepository apontamentoRepository;
    private final ObraRepository obraRepository;
    private final LocaisPlanejamento locais;
    private final MunicipioApi municipioApi;
    private final AuditorAware<String> auditor;
    private final ContextoObras contextos;

    @Transactional(readOnly = true)
    public List<MedicaoResponses.ObraAMedir> obrasAMedir() {
        List<ApontamentoExecucaoRepository.PendenteDeMedicao> pendentes = apontamentoRepository.somarPendentesDeMedicao();
        Map<UUID, Obra> obras = obraRepository.findAllById(pendentes.stream().map(ApontamentoExecucaoRepository.PendenteDeMedicao::idObra).toList())
                .stream().filter(Obra::isAtivo).collect(Collectors.toMap(Obra::getId, Function.identity()));
        Map<UUID, MedicaoResponses.ContextoObra> contexto = contextos.de(obras.values());
        return pendentes.stream()
                .filter(p -> obras.containsKey(p.idObra()))
                .map(p -> {
                    Obra obra = obras.get(p.idObra());
                    return new MedicaoResponses.ObraAMedir(obra.getId(), obra.getNumero(), obra.getTitulo(),
                            contexto.get(obra.getId()), p.servicos(), p.valor(), p.ultimaData());
                })
                .sorted(Comparator.comparing(MedicaoResponses.ObraAMedir::valor).reversed()
                        .thenComparing(MedicaoResponses.ObraAMedir::numero))
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicaoResponses.MedicaoDaObra daObra(UUID idObra) {
        Obra obra = buscarObra(idObra);
        Map<UUID, String> locaisDaObra = locais.daObra(idObra);
        List<MedicaoResponses.ItemPendente> pendentes = pendentes(idObra, locaisDaObra);
        List<Medicao> medicoes = repository.listarDaObraComItens(idObra);
        BigDecimal medido = somarGeradas(medicoes);
        BigDecimal saldo = obra.getValorProjeto() != null ? obra.getValorProjeto().subtract(medido) : null;
        return new MedicaoResponses.MedicaoDaObra(obra.getId(), obra.getNumero(), obra.getTitulo(), municipio(obra),
                obra.getValorProjeto(), pendentes, somar(pendentes), (int) pendentes.stream().filter(p -> p.valorTotal() == null).count(),
                medido, saldo, medicoes.stream().map(m -> resumo(m, obra)).toList());
    }

    @Transactional(readOnly = true)
    public MedicaoResponses.Ciclo ciclo(UUID idObra, YearMonth competencia) {
        if (competencia == null) {
            throw new InvarianteVioladaException("Informe a competência do ciclo.");
        }
        Obra obra = buscarObra(idObra);
        LocalDate inicio = competencia.atDay(1);
        LocalDate fim = competencia.atEndOfMonth();
        List<Medicao> medicoes = repository.listarDaObraComItens(idObra);
        BigDecimal anterior = somarGeradas(medicoes.stream().filter(m -> m.getCompetencia().isBefore(inicio)).toList());
        List<Medicao> doCiclo = medicoes.stream().filter(m -> m.getCompetencia().equals(inicio)).toList();
        BigDecimal noCiclo = somarGeradas(doCiclo);
        List<MedicaoResponses.ItemPendente> aMedir = pendentes(idObra, locais.daObra(idObra)).stream()
                .filter(p -> p.concluidoEm() == null || !p.concluidoEm().isAfter(fim))
                .toList();
        BigDecimal projeto = obra.getValorProjeto();
        BigDecimal saldoAnterior = projeto != null ? projeto.subtract(anterior) : null;
        return new MedicaoResponses.Ciclo(obra.getId(), obra.getNumero(), obra.getTitulo(), competencia, inicio, fim,
                projeto, anterior, saldoAnterior, noCiclo, saldoAnterior != null ? saldoAnterior.subtract(noCiclo) : null,
                aMedir, somar(aMedir), doCiclo.stream().map(m -> resumo(m, obra)).toList());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public MedicaoResponses.MedicaoDetalhe gerar(UUID idObra, MedicaoRequests.Gerar request) {
        Obra obra = buscarObra(idObra);
        LocalDate hoje = LocalDate.now(FUSO);
        YearMonth competencia = request.tipo() == TipoMedicao.CICLO ? request.competencia() : YearMonth.from(hoje);
        if (request.tipo() == TipoMedicao.CICLO && competencia == null) {
            throw new InvarianteVioladaException("Informe a competência do ciclo.");
        }
        Map<UUID, ApontamentoExecucaoRepository.ExecucaoDoItem> execucao = execucaoPorItem(idObra);
        List<ItemPlanejado> mediveis = itemRepository.listarMediveis(idObra);
        List<Medicao.ItemAMedir> itens;
        if (request.idsItens() != null && !request.idsItens().isEmpty()) {
            Set<UUID> escolhidos = new HashSet<>(request.idsItens());
            itens = mediveis.stream().filter(i -> escolhidos.contains(i.getId())).map(i -> aMedir(i, execucao)).toList();
            if (itens.size() < escolhidos.size()) {
                throw new InvarianteVioladaException("Há serviços escolhidos que já foram medidos ou não estão concluídos. Atualize a lista e tente de novo.");
            }
        } else {
            LocalDate fim = competencia.atEndOfMonth();
            itens = mediveis.stream().map(i -> aMedir(i, execucao))
                    .filter(i -> request.tipo() != TipoMedicao.CICLO || i.concluidoEm() == null || !i.concluidoEm().isAfter(fim))
                    .toList();
        }
        Medicao medicao = repository.saveAndFlush(Medicao.gerar(repository::proximoNumero, idObra, request.tipo(), competencia,
                hoje, itens, request.observacao()));
        return detalhe(medicao, obra, locais.daObra(idObra));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public MedicaoResponses.MedicaoDetalhe estornar(UUID id, MedicaoRequests.Estornar request) {
        Medicao medicao = buscar(id);
        medicao.estornar(request.justificativa(), auditor.getCurrentAuditor().orElse("SYSTEM"), OffsetDateTime.now(FUSO));
        repository.saveAndFlush(medicao);
        return detalhe(medicao, buscarObra(medicao.getIdObra()), locais.daObra(medicao.getIdObra()));
    }

    @Transactional(readOnly = true)
    public MedicaoResponses.MedicaoDetalhe buscarPorId(UUID id) {
        Medicao medicao = buscar(id);
        return detalhe(medicao, buscarObra(medicao.getIdObra()), locais.daObra(medicao.getIdObra()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicaoResponses.MedicaoResumo> listar(MedicaoFilter filtro, Pageable pageable) {
        var pagina = repository.findAll(MedicaoSpecification.filterBy(filtro), pageable);
        Map<UUID, Obra> obras = obraRepository.findAllById(pagina.getContent().stream().map(Medicao::getIdObra).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Obra::getId, Function.identity()));
        return PageResponse.fromPage(pagina.map(m -> resumo(m, obras.get(m.getIdObra()))));
    }

    /** Municípios e supervisores das obras com medição, em ordem alfabética. */
    @Transactional(readOnly = true)
    public MedicaoResponses.OpcoesMedicoes opcoes() {
        Collection<MedicaoResponses.ContextoObra> todos = contextos.de(obraRepository.findAllById(repository.obrasComMedicao())).values();
        Comparator<MedicaoResponses.Opcao> porNome = Comparator.comparing(MedicaoResponses.Opcao::nome,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return new MedicaoResponses.OpcoesMedicoes(
                todos.stream().filter(c -> c.idMunicipio() != null)
                        .map(c -> new MedicaoResponses.Opcao(c.idMunicipio(), c.municipio())).distinct().sorted(porNome).toList(),
                todos.stream().filter(c -> c.idSupervisor() != null)
                        .map(c -> new MedicaoResponses.Opcao(c.idSupervisor(), c.supervisor())).distinct().sorted(porNome).toList());
    }

    /** Medições geradas e estornadas da obra, para o fechamento. */
    @Transactional(readOnly = true)
    List<MedicaoResponses.MedicaoResumo> resumosDaObra(Obra obra) {
        return repository.listarDaObraComItens(obra.getId()).stream().map(m -> resumo(m, obra)).toList();
    }

    private List<MedicaoResponses.ItemPendente> pendentes(UUID idObra, Map<UUID, String> locaisDaObra) {
        Map<UUID, ApontamentoExecucaoRepository.ExecucaoDoItem> execucao = execucaoPorItem(idObra);
        return itemRepository.listarMediveis(idObra).stream()
                .map(i -> {
                    Medicao.ItemAMedir item = aMedir(i, execucao);
                    return new MedicaoResponses.ItemPendente(i.getId(), i.getCodigo(), i.getDescricao(), i.getUnidade(),
                            i.getOperacao(), i.getKitAplicado().getCodigoKit(),
                            LocaisPlanejamento.local(locaisDaObra, i.getKitAplicado().getIdPonto(), i.getKitAplicado().getIdVao()),
                            item.quantidade(), item.valorTotal(), item.concluidoEm());
                })
                .sorted(Comparator.comparing(MedicaoResponses.ItemPendente::concluidoEm, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MedicaoResponses.ItemPendente::local, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MedicaoResponses.ItemPendente::descricao, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Map<UUID, ApontamentoExecucaoRepository.ExecucaoDoItem> execucaoPorItem(UUID idObra) {
        return apontamentoRepository.somarPorItemDaObra(idObra).stream()
                .collect(Collectors.toMap(ApontamentoExecucaoRepository.ExecucaoDoItem::idItem, Function.identity()));
    }

    /** Sem apontamento ativo (não deveria acontecer num item concluído), vale o executado do item, sem valor. */
    private static Medicao.ItemAMedir aMedir(ItemPlanejado item, Map<UUID, ApontamentoExecucaoRepository.ExecucaoDoItem> execucao) {
        ApontamentoExecucaoRepository.ExecucaoDoItem e = execucao.get(item.getId());
        return e != null
                ? new Medicao.ItemAMedir(item, e.quantidade(), e.valor(), e.ultimaData())
                : new Medicao.ItemAMedir(item, item.getQuantidadeExecutada(), null, null);
    }

    private MedicaoResponses.MedicaoDetalhe detalhe(Medicao medicao, Obra obra, Map<UUID, String> locaisDaObra) {
        List<MedicaoItem> itens = medicao.estornada() ? medicao.getItens() : medicao.getItens().stream().filter(MedicaoItem::isAtivo).toList();
        return new MedicaoResponses.MedicaoDetalhe(resumo(medicao, obra), itens.stream()
                .sorted(Comparator.comparing(MedicaoItem::getConcluidoEm, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MedicaoItem::getDescricao, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new MedicaoResponses.ItemMedido(i.getId(), i.getItemPlanejado().getId(), i.getCodigo(), i.getDescricao(),
                        i.getUnidade(), i.getOperacao(), i.getCodigoKit(), LocaisPlanejamento.local(locaisDaObra, i.getIdPonto(), i.getIdVao()),
                        i.getQuantidade(), i.getValorUnitario(), i.getValorTotal(), i.getConcluidoEm()))
                .toList());
    }

    static MedicaoResponses.MedicaoResumo resumo(Medicao m, Obra obra) {
        return new MedicaoResponses.MedicaoResumo(m.getId(), m.getNumero(), m.getIdObra(),
                obra != null ? obra.getNumero() : null, obra != null ? obra.getTitulo() : null, m.getTipo(),
                m.getCompetenciaMes(), m.getStatus(), m.getValorTotal(), m.getItens().size(), m.getObservacao(),
                m.getDataCriacao(), m.getUsuarioCriacao(), m.getJustificativaEstorno(), m.getDataEstorno(), m.getUsuarioEstorno());
    }

    static BigDecimal somarGeradas(Collection<Medicao> medicoes) {
        return medicoes.stream().filter(m -> !m.estornada()).map(Medicao::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal somar(List<MedicaoResponses.ItemPendente> itens) {
        return itens.stream().map(MedicaoResponses.ItemPendente::valorTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Medicao buscar(UUID id) {
        return repository.buscarComItens(id).orElseThrow(() -> new RecursoNaoEncontradoException("Medição não encontrada."));
    }

    private Obra buscarObra(UUID idObra) {
        return obraRepository.findById(idObra).filter(Obra::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));
    }

    String municipio(Obra obra) {
        return obra.getIdMunicipio() != null ? municipioApi.buscarPorIdResponse(obra.getIdMunicipio()).map(MunicipioResponse::nome).orElse(null) : null;
    }

    Map<UUID, String> municipios(Collection<Obra> obras) {
        Map<UUID, String> nomes = new HashMap<>();
        obras.stream().map(Obra::getIdMunicipio).filter(Objects::nonNull).distinct()
                .forEach(id -> nomes.put(id, municipioApi.buscarPorIdResponse(id).map(MunicipioResponse::nome).orElse(null)));
        return nomes;
    }
}
