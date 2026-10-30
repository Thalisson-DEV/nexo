package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.ComposicaoEquipeApi;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.EquipeMembro;
import com.nexo.app.modules.rh.domain.Veiculo;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeMembroRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.VeiculoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composição operacional da equipe (veículo e membros) mantida pelo próprio
 * responsável na abertura do turno.
 */
@Service
@RequiredArgsConstructor
public class ComposicaoEquipeUseCases implements ComposicaoEquipeApi {

    private final EquipeRepository equipeRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final EquipeMembroRepository equipeMembroRepository;
    private final VeiculoRepository veiculoRepository;
    private final MembroEquipeUseCase membroEquipeUseCase;
    private final MetaFaturamentoUseCases metaFaturamentoUseCases;

    @Override
    @Transactional(readOnly = true)
    public Optional<ComposicaoEquipe> buscarComposicao(UUID idEquipe) {
        if (idEquipe == null) {
            return Optional.empty();
        }
        return equipeRepository.findById(idEquipe).map(this::toComposicao);
    }

    @Override
    @Transactional(readOnly = true)
    public OpcoesComposicao listarOpcoes(UUID idEquipe) {
        Equipe equipe = buscarEquipeAtiva(idEquipe);

        Map<UUID, Colaborador> candidatos = new LinkedHashMap<>();
        if (equipe.getBaseOperacional() != null) {
            colaboradorRepository.buscarAtivosPorBases(Set.of(equipe.getBaseOperacional().getId()))
                    .forEach(c -> candidatos.put(c.getId(), c));
        }
        // Membros atuais sempre aparecem, mesmo que sejam de outra base.
        equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .map(EquipeMembro::getColaborador)
                .forEach(c -> candidatos.putIfAbsent(c.getId(), c));

        Map<UUID, EquipeMembro> vinculos = candidatos.isEmpty()
                ? Map.of()
                : equipeMembroRepository.findVinculosAtivosPorColaboradores(candidatos.keySet()).stream()
                        .collect(Collectors.toMap(v -> v.getColaborador().getId(), Function.identity(), (a, b) -> a));

        List<OpcaoColaborador> colaboradores = candidatos.values().stream()
                .map(c -> {
                    EquipeMembro vinculo = vinculos.get(c.getId());
                    boolean outraEquipe = vinculo != null && !vinculo.getEquipe().getId().equals(equipe.getId());
                    return new OpcaoColaborador(
                            c.getId(),
                            c.getNomeCompleto(),
                            c.getFuncao() != null ? c.getFuncao().getNome() : null,
                            vinculo != null ? vinculo.getEquipe().getCodigo() : null,
                            outraEquipe && vinculo.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL
                    );
                })
                .sorted(Comparator.comparing(o -> Objects.requireNonNullElse(o.nome(), "")))
                .toList();

        List<VeiculoComposicao> veiculos = veiculoRepository.findAllByAtivoTrue().stream()
                .map(this::toVeiculo)
                .sorted(Comparator.comparing(v -> Objects.requireNonNullElse(v.placa(), "")))
                .toList();

        return new OpcoesComposicao(colaboradores, veiculos);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "equipes", allEntries = true),
            @CacheEvict(cacheNames = "membros-equipes", allEntries = true)
    })
    public ComposicaoEquipe atualizarPeloResponsavel(UUID idEquipe, UUID idUsuarioResponsavel,
                                                    UUID idVeiculo, List<UUID> idsParceiros) {
        Equipe equipe = buscarEquipeAtiva(idEquipe);

        if (!equipe.isResponsavel(idUsuarioResponsavel)) {
            throw new InvarianteVioladaException(
                    "Apenas o responsável da equipe pode alterar o veículo e os membros na abertura do turno.");
        }

        if (idVeiculo != null && (equipe.getVeiculo() == null || !idVeiculo.equals(equipe.getVeiculo().getId()))) {
            Veiculo veiculo = veiculoRepository.findById(idVeiculo)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado."));
            equipe.vincularVeiculo(veiculo);
        }

        if (idsParceiros != null) {
            aplicarParceiros(equipe, idsParceiros);
        }

        return toComposicao(equipeRepository.save(equipe));
    }

    /**
     * Converge os parceiros ativos da equipe para a lista desejada. O responsável
     * nunca é removido por aqui; quem estiver como parceiro em outra equipe é
     * transferido automaticamente (mesma regra da alocação administrativa).
     */
    private void aplicarParceiros(Equipe equipe, List<UUID> idsParceiros) {
        Set<UUID> desejados = idsParceiros.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<EquipeMembro> ativos = equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .toList();

        Set<UUID> idsResponsaveis = ativos.stream()
                .filter(m -> m.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL)
                .map(m -> m.getColaborador().getId())
                .collect(Collectors.toSet());
        desejados.removeAll(idsResponsaveis);

        ativos.stream()
                .filter(m -> m.getFuncao() == EquipeMembro.FuncaoMembro.PARCEIRO)
                .filter(m -> !desejados.contains(m.getColaborador().getId()))
                .map(EquipeMembro::getColaborador)
                .toList()
                .forEach(equipe::encerrarVinculoMembro);

        Set<UUID> parceirosAtuais = ativos.stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .filter(m -> m.getFuncao() == EquipeMembro.FuncaoMembro.PARCEIRO)
                .map(m -> m.getColaborador().getId())
                .collect(Collectors.toSet());

        for (UUID idColaborador : desejados) {
            if (parceirosAtuais.contains(idColaborador)) {
                continue;
            }
            Colaborador colaborador = colaboradorRepository.findById(idColaborador)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado."));
            membroEquipeUseCase.processarAlocacaoUnica(equipe, colaborador, EquipeMembro.FuncaoMembro.PARCEIRO);
        }
    }

    private Equipe buscarEquipeAtiva(UUID idEquipe) {
        Equipe equipe = equipeRepository.findById(Objects.requireNonNull(idEquipe, "Equipe é obrigatória"))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada."));
        if (!equipe.isAtivo()) {
            throw new InvarianteVioladaException("A equipe está desativada.");
        }
        return equipe;
    }

    private ComposicaoEquipe toComposicao(Equipe equipe) {
        List<MembroComposicao> membros = equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .map(m -> new MembroComposicao(
                        m.getColaborador().getId(),
                        m.getColaborador().getNomeCompleto(),
                        m.getColaborador().getFuncao() != null ? m.getColaborador().getFuncao().getNome() : null,
                        m.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL
                ))
                .sorted(Comparator.comparing(MembroComposicao::responsavel).reversed()
                        .thenComparing(m -> Objects.requireNonNullElse(m.nome(), "")))
                .toList();

        MetaFaturamentoUseCases.MetaVigente meta = metaFaturamentoUseCases.metaVigente(equipe.getId());
        return new ComposicaoEquipe(
                equipe.getId(),
                equipe.getCodigo(),
                equipe.getIdUsuario(),
                equipe.getVeiculo() != null ? toVeiculo(equipe.getVeiculo()) : null,
                membros,
                meta.valorDaJanela(),
                meta.diaria()
        );
    }

    private VeiculoComposicao toVeiculo(Veiculo veiculo) {
        return new VeiculoComposicao(veiculo.getId(), veiculo.getPlaca(), veiculo.getModelo());
    }
}
