package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.HistoricoImportacaoResponse;
import com.nexo.app.modules.obras.application.mappers.ImportacaoObraMapper;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta do histórico das importações de planilha de Obras e acompanhamento das que estão em processamento.
 */
@Service
@RequiredArgsConstructor
public class HistoricoImportacaoUseCases {

    private final ImportacaoObraRepository repository;
    private final ImportacaoObraMapper mapper;

    @Transactional(readOnly = true)
    public HistoricoImportacaoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Importação não encontrada com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoricoImportacaoResponse> listar(ImportacaoObra.Tipo tipo, Pageable pageable) {
        return PageResponse.fromPage(repository.findByTipoOrderByDataCriacaoDesc(tipo, pageable).map(mapper::toResponse));
    }
}
