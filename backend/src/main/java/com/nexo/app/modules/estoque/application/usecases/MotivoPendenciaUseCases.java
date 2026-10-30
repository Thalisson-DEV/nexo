package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.MotivoPendenciaRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoPendenciaResponse;
import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import com.nexo.app.modules.estoque.infrastructure.persistence.MotivoPendenciaRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MotivoPendenciaUseCases {
    private final MotivoPendenciaRepository repository;

    @Transactional(readOnly = true)
    public List<MotivoPendenciaResponse> listar() {
        return repository.findAll().stream().map(MotivoPendenciaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MotivoPendenciaResponse> listarAtivos() {
        return repository.findByAtivoTrueOrderByNomeAsc().stream().map(MotivoPendenciaResponse::de).toList();
    }

    @Transactional
    public MotivoPendenciaResponse criar(MotivoPendenciaRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome().trim())) {
            throw new InvarianteVioladaException("Já existe um motivo de pendência com este nome.");
        }
        return MotivoPendenciaResponse.de(repository.save(new MotivoPendencia(request.nome(), request.descricao())));
    }

    @Transactional
    public MotivoPendenciaResponse atualizar(UUID id, MotivoPendenciaRequest request) {
        MotivoPendencia motivo = buscar(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().trim(), id)) {
            throw new InvarianteVioladaException("Já existe um motivo de pendência com este nome.");
        }
        motivo.atualizar(request.nome(), request.descricao());
        return MotivoPendenciaResponse.de(repository.save(motivo));
    }

    @Transactional
    public void ativar(UUID id) { buscar(id).ativar(); }

    @Transactional
    public void desativar(UUID id) { buscar(id).desativar(); }

    public MotivoPendencia buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de pendência não encontrado."));
    }
}
