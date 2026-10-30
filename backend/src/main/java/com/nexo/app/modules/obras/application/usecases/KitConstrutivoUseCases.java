package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.obras.application.dto.ItemKitRequest;
import com.nexo.app.modules.obras.application.dto.ItemKitResponse;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoFilter;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoRequest;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoResponse;
import com.nexo.app.modules.obras.domain.ItemKit;
import com.nexo.app.modules.obras.domain.KitConstrutivo;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cadastro de kits construtivos e cálculo do valor do kit em R$ (só serviços, valor vigente da Ação no contrato).
 *
 * <p>O valor não é cacheado: depende da data de referência e dos reajustes de Ação, que acontecem em outro
 * módulo. Só a lista de grupos é cacheada.</p>
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = CacheNames.OBRAS_KITS)
public class KitConstrutivoUseCases {

    private final KitConstrutivoRepository repository;
    private final ContratoApi contratoApi;
    private final EstoqueApi estoqueApi;

    @Transactional
    @CacheEvict(allEntries = true)
    public KitConstrutivoResponse criar(KitConstrutivoRequest request) {
        validarContrato(request.idContrato());
        String codigo = KitConstrutivo.normalizarCodigo(request.codigo());
        if (repository.existsByIdContratoAndCodigoAndAtivoTrue(request.idContrato(), codigo)) {
            throw new InvarianteVioladaException("Já existe um kit ativo com o código " + codigo + " neste contrato.");
        }
        KitConstrutivo kit = new KitConstrutivo(request.idContrato(), codigo, request.descricao(),
                request.grupo(), request.classesTensao());
        sincronizarItens(kit, request.itens());
        return montar(repository.save(kit), null, true);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public KitConstrutivoResponse atualizar(UUID id, KitConstrutivoRequest request) {
        KitConstrutivo kit = buscarEntidade(id);
        if (!kit.getIdContrato().equals(request.idContrato())) {
            throw new InvarianteVioladaException("O contrato de um kit não pode ser alterado. Cadastre um novo kit.");
        }
        if (!kit.getCodigo().equals(KitConstrutivo.normalizarCodigo(request.codigo()))) {
            throw new InvarianteVioladaException("O código de um kit não pode ser alterado. Cadastre um novo kit.");
        }
        kit.atualizarDados(request.descricao(), request.grupo(), request.classesTensao());
        sincronizarItens(kit, request.itens());
        return montar(repository.save(kit), null, true);
    }

    @Transactional(readOnly = true)
    public KitConstrutivoResponse buscarPorId(UUID id, LocalDate dataReferencia) {
        return montar(buscarEntidade(id), dataReferencia, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<KitConstrutivoResponse> listar(KitConstrutivoFilter filter, LocalDate dataReferencia,
                                                       Pageable pageable) {
        Page<KitConstrutivo> page = repository.findAll(KitConstrutivoSpecification.filterBy(filter), pageable);
        Map<UUID, Map<UUID, BigDecimal>> valoresPorContrato = new HashMap<>();
        Map<UUID, String> numerosContrato = new HashMap<>();
        return PageResponse.fromPage(page.map(kit -> montar(kit, dataReferencia, false,
                valoresPorContrato, numerosContrato, Map.of(), Map.of())));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "'grupos-' + #idContrato")
    public List<String> listarGrupos(UUID idContrato) {
        return repository.listarGrupos(idContrato);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        KitConstrutivo kit = buscarEntidade(id);
        kit.desativar();
        repository.save(kit);
    }

    public KitConstrutivo buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Kit construtivo não encontrado com o ID: " + id));
    }

    /**
     * Valor unitário vigente (R$, já com coeficiente do contrato) de cada Ação do contrato na data.
     */
    public Map<UUID, BigDecimal> valoresVigentes(UUID idContrato, LocalDate dataReferencia) {
        LocalDate data = dataReferencia != null ? dataReferencia : LocalDate.now();
        return contratoApi.listarAcoesComValor(idContrato, data).stream()
                .collect(Collectors.toMap(ContratoApi.AcaoValorItem::idAcao, ContratoApi.AcaoValorItem::valor,
                        (a, b) -> a));
    }

    private void sincronizarItens(KitConstrutivo kit, List<ItemKitRequest> itens) {
        List<ItemKitRequest> solicitados = itens != null ? itens : List.of();
        validarReferencias(solicitados);

        Set<UUID> mantidos = solicitados.stream().map(ItemKitRequest::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (ItemKit atual : new ArrayList<>(kit.getItensAtivos())) {
            if (atual.getId() != null && !mantidos.contains(atual.getId())) {
                kit.removerItem(atual.getId());
            }
        }
        for (ItemKitRequest item : solicitados) {
            if (item.id() != null) {
                kit.alterarItem(item.id(), item.operacao(), item.unidade(), item.quantidade());
            } else {
                kit.adicionarItem(item.tipo(), item.idReferencia(), item.operacao(), item.unidade(), item.quantidade());
            }
        }
    }

    private void validarReferencias(List<ItemKitRequest> itens) {
        for (ItemKitRequest item : itens) {
            if (item.tipo() == TipoItemKit.SERVICO && !contratoApi.existeAcao(item.idReferencia())) {
                throw new InvarianteVioladaException("A ação informada no item do kit não existe ou está inativa.");
            }
            if (item.tipo() == TipoItemKit.MATERIAL && estoqueApi.buscarMaterialPorIdResponse(item.idReferencia())
                    .filter(MaterialResponse::ativo).isEmpty()) {
                throw new InvarianteVioladaException("O material informado no item do kit não existe ou está inativo.");
            }
        }
    }

    private void validarContrato(UUID idContrato) {
        if (idContrato == null || !contratoApi.existeContrato(idContrato)) {
            throw new InvarianteVioladaException("O contrato informado para o kit não existe ou está inativo.");
        }
    }

    private KitConstrutivoResponse montar(KitConstrutivo kit, LocalDate dataReferencia, boolean comItens) {
        List<ItemKit> itens = kit.getItensAtivos();
        Map<UUID, AcaoResponse> acoes = indexar(contratoApi.buscarAcoesPorIdsResponse(ids(itens, TipoItemKit.SERVICO)),
                AcaoResponse::id);
        Map<UUID, MaterialResponse> materiais = indexar(
                estoqueApi.buscarMateriaisPorIdsResponse(ids(itens, TipoItemKit.MATERIAL)), MaterialResponse::id);
        return montar(kit, dataReferencia, comItens, new HashMap<>(), new HashMap<>(), acoes, materiais);
    }

    private KitConstrutivoResponse montar(KitConstrutivo kit, LocalDate dataReferencia, boolean comItens,
                                          Map<UUID, Map<UUID, BigDecimal>> valoresPorContrato,
                                          Map<UUID, String> numerosContrato,
                                          Map<UUID, AcaoResponse> acoes,
                                          Map<UUID, MaterialResponse> materiais) {
        LocalDate data = dataReferencia != null ? dataReferencia : LocalDate.now();
        Map<UUID, BigDecimal> valores = valoresPorContrato.computeIfAbsent(kit.getIdContrato(),
                idContrato -> valoresVigentes(idContrato, data));
        String numeroContrato = numerosContrato.computeIfAbsent(kit.getIdContrato(),
                idContrato -> contratoApi.buscarContratoResumo(idContrato)
                        .map(ContratoApi.ContratoResumo::numeroContrato).orElse(null));

        BigDecimal total = BigDecimal.ZERO;
        int servicos = 0;
        int materiaisQtd = 0;
        int semValor = 0;
        List<ItemKitResponse> itensResponse = new ArrayList<>();
        for (ItemKit item : kit.getItensAtivos()) {
            BigDecimal valorUnitario = null;
            BigDecimal valorTotal = null;
            if (item.getTipo() == TipoItemKit.SERVICO) {
                servicos++;
                valorUnitario = valores.get(item.getIdAcao());
                if (valorUnitario == null) {
                    semValor++;
                } else {
                    valorTotal = valorUnitario.multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP);
                    total = total.add(valorTotal);
                }
            } else {
                materiaisQtd++;
            }
            if (comItens) {
                itensResponse.add(montarItem(item, valorUnitario, valorTotal, acoes, materiais));
            }
        }

        return new KitConstrutivoResponse(
                kit.getId(),
                kit.getIdContrato(),
                numeroContrato,
                kit.getCodigo(),
                kit.getDescricao(),
                kit.getGrupo(),
                Set.copyOf(new HashSet<>(kit.getClassesTensao())),
                servicos + materiaisQtd,
                servicos,
                materiaisQtd,
                total.setScale(2, RoundingMode.HALF_UP),
                semValor,
                data,
                comItens ? itensResponse : List.of(),
                kit.isAtivo(),
                kit.getVersao(),
                kit.getDataCriacao(),
                kit.getDataUltimaAlteracao()
        );
    }

    private ItemKitResponse montarItem(ItemKit item, BigDecimal valorUnitario, BigDecimal valorTotal,
                                       Map<UUID, AcaoResponse> acoes, Map<UUID, MaterialResponse> materiais) {
        String codigo = null;
        String descricao = null;
        String unidade = item.getUnidade();
        if (item.getTipo() == TipoItemKit.SERVICO) {
            AcaoResponse acao = acoes.get(item.getIdAcao());
            if (acao != null) {
                codigo = acao.codigo();
                descricao = acao.nome();
            }
        } else {
            MaterialResponse material = materiais.get(item.getIdMaterial());
            if (material != null) {
                codigo = material.codigo();
                descricao = material.descricao();
                unidade = Optional.ofNullable(unidade).orElse(material.unidadeMedida());
            }
        }
        return new ItemKitResponse(item.getId(), item.getTipo(), item.getIdReferencia(), codigo, descricao,
                item.getOperacao(), unidade, item.getQuantidade(), valorUnitario, valorTotal);
    }

    private static Set<UUID> ids(Collection<ItemKit> itens, TipoItemKit tipo) {
        return itens.stream().filter(i -> i.getTipo() == tipo).map(ItemKit::getIdReferencia)
                .collect(Collectors.toSet());
    }

    private static <T> Map<UUID, T> indexar(List<T> lista, Function<T, UUID> id) {
        return lista.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
