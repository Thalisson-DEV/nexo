package com.nexo.app.modules.estoque;

import com.nexo.app.modules.estoque.application.mappers.MaterialMapper;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface de comunicação do módulo de estoque com outros módulos.
 */
@Component
@RequiredArgsConstructor
public class EstoqueApi {

    private final MaterialRepository materialRepository;
    private final EstoqueRepository estoqueRepository;
    private final MaterialMapper materialMapper;

    /**
     * Valida o item de material de uma execução de campo.
     *
     * <p>Materiais seriados exigem o número de série na aplicação. A disponibilidade no
     * saldo da equipe <b>não</b> bloqueia o lançamento: o débito é permissivo e o saldo
     * pode ficar negativo, o que é sinalizado ao almoxarifado nas entregas à equipe.</p>
     */
    public void validarDisponibilidadeMaterialEquipe(UUID idMaterial, String numSerie, UUID idEquipe, OperacaoMaterial operacao) {
        Material material = materialRepository.findById(idMaterial)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + idMaterial));

        if (OperacaoMaterial.APLICADO.equals(operacao) && material.isDetalhaSerie()
                && (numSerie == null || numSerie.isBlank())) {
            throw new InvarianteVioladaException("O número de série é obrigatório para o material: " + material.getDescricao());
        }
    }

    /**
     * Busca os dados de um material por ID para exibição.
     */
    public Optional<MaterialResponse> buscarMaterialPorIdResponse(UUID id) {
        return materialRepository.findById(id).map(materialMapper::toResponse);
    }

    /**
     * Busca um material ativo pelo código, sem diferenciar maiúsculas (usado nas importações de planilha).
     */
    public Optional<MaterialResponse> buscarMaterialAtivoPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return materialRepository.findByCodigoIgnoreCase(codigo.trim())
                .filter(Material::isAtivo)
                .map(materialMapper::toResponse);
    }

    /**
     * Busca vários materiais de uma vez, para quem monta listas. Ids sem material ficam fora do resultado.
     */
    public List<MaterialResponse> buscarMateriaisPorIdsResponse(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return materialRepository.findAllById(ids).stream().map(materialMapper::toResponse).toList();
    }

    /**
     * Busca o ID do estoque vinculado a uma equipe.
     */
    public Optional<UUID> buscarIdEstoquePorEquipe(UUID idEquipe) {
        if (idEquipe == null) return Optional.empty();
        return estoqueRepository.findByIdEquipe(idEquipe).map(Estoque::getId);
    }
}
