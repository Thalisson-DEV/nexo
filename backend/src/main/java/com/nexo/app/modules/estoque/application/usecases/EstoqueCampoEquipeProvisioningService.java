package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EstoqueCampoEquipeProvisioningService {

    private static final String TIPO_CAMPO = "CAMPO";

    private final EstoqueRepository estoqueRepository;
    private final TipoEstoqueRepository tipoEstoqueRepository;

    public Estoque resolver(UUID idEquipe) {
        if (idEquipe == null) {
            throw new InvarianteVioladaException("A equipe responsavel pela custodia e obrigatoria.");
        }
        Estoque estoque = estoqueRepository.findByIdEquipe(idEquipe)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "A equipe nao possui estoque de campo vinculado. Regularize o cadastro da equipe."));
        validarEstoqueExistente(estoque, idEquipe);
        return estoque;
    }

    public Estoque provisionar(UUID idEquipe, String codigoEquipe, UUID idBaseOperacional) {
        Estoque existente = estoqueRepository.findByIdEquipe(idEquipe).orElse(null);
        if (existente != null) {
            validarEstoqueExistente(existente, idEquipe);
            return existente;
        }

        TipoEstoque tipoCampo = tipoEstoqueRepository.findByNomeIgnoreCase(TIPO_CAMPO)
                .filter(TipoEstoque::isAtivo)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "O tipo de estoque CAMPO deve existir e estar ativo para criar a equipe."));

        Estoque estoque = new Estoque(codigoEquipe, tipoCampo);
        estoque.vincularEquipe(idEquipe);
        estoque.vincularBaseOperacional(idBaseOperacional);

        try {
            return estoqueRepository.saveAndFlush(estoque);
        } catch (DataIntegrityViolationException exception) {
            throw new InvarianteVioladaException(
                    "Nao foi possivel criar o estoque de campo da equipe. Verifique se o codigo ou a equipe ja possuem estoque vinculado.");
        }
    }

    private void validarEstoqueExistente(Estoque estoque, UUID idEquipe) {
        if (!estoque.isAtivo() || !estoque.isTipoCampo()) {
            throw new InvarianteVioladaException(
                    "A equipe " + idEquipe + " possui estoque vinculado, mas ele deve estar ativo e ser do tipo CAMPO.");
        }
    }
}
