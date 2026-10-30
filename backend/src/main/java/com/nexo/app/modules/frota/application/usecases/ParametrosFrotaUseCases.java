package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.ParametrosFrotaRequest;
import com.nexo.app.modules.frota.application.dto.ParametrosFrotaResponse;
import com.nexo.app.modules.frota.domain.ParametrosFrota;
import com.nexo.app.modules.frota.infrastructure.persistence.ParametrosFrotaRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Limiares da frota editáveis sem deploy. */
@Service
@RequiredArgsConstructor
public class ParametrosFrotaUseCases {

    private final ParametrosFrotaRepository repository;

    @Transactional(readOnly = true)
    public ParametrosFrotaResponse consultar() {
        return toResponse(obter());
    }

    @Transactional
    public ParametrosFrotaResponse atualizar(ParametrosFrotaRequest request) {
        ParametrosFrota parametros = obter();
        parametros.atualizar(request.faixasAlertaDias(), request.limiteKmDia());
        return toResponse(repository.save(parametros));
    }

    /** Linha única criada pela migração. */
    ParametrosFrota obter() {
        return repository.findById(ParametrosFrota.ID_UNICO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Parâmetros da frota não configurados."));
    }

    /** Maior faixa de aviso: até onde um vencimento já conta como "a vencer". */
    int diasAviso() {
        var faixas = obter().faixasAlerta();
        return faixas.isEmpty() ? 30 : faixas.get(0);
    }

    private static ParametrosFrotaResponse toResponse(ParametrosFrota parametros) {
        return new ParametrosFrotaResponse(parametros.faixasAlerta(), parametros.getLimiteKmDia());
    }
}
