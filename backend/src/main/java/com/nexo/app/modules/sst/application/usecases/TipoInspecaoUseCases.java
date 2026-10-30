package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.TipoInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoResponse;
import com.nexo.app.modules.sst.application.mappers.TipoInspecaoMapper;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TipoInspecaoUseCases {

    private final TipoInspecaoRepository repository;
    private final TipoInspecaoMapper mapper;

    @Transactional
    public TipoInspecaoResponse criar(TipoInspecaoRequest request) {
        if (request == null) {
            throw new InvarianteVioladaException("Os dados do tipo de inspeção são obrigatórios.");
        }
        validarUnicidade(request.nome(), null);
        TipoInspecao tipo = new TipoInspecao(request.nome(), request.descricao(), request.ordem());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional
    public TipoInspecaoResponse atualizar(UUID id, TipoInspecaoRequest request) {
        if (request == null) {
            throw new InvarianteVioladaException("Os dados do tipo de inspeção são obrigatórios.");
        }
        TipoInspecao tipo = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de inspeção não encontrado: " + id));

        validarUnicidade(request.nome(), id);
        tipo.atualizar(request.nome(), request.descricao(), request.ordem());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional(readOnly = true)
    public TipoInspecaoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de inspeção não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TipoInspecaoResponse> listar(TipoInspecaoFilter filter, Pageable pageable) {
        Page<TipoInspecao> pagina = repository.findAll(TipoInspecaoSpecification.comFiltro(filter), pageable);
        return PageResponse.fromPage(pagina.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<TipoInspecaoResponse> listarAtivos() {
        return repository.listarAtivosOrdenados().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public void alternarStatus(UUID id) {
        TipoInspecao tipo = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de inspeção não encontrado: " + id));

        if (tipo.isAtivo()) {
            tipo.desativar();
        } else {
            validarUnicidade(tipo.getNome(), tipo.getId());
            tipo.ativar();
        }
        repository.save(tipo);
    }

    private void validarUnicidade(String nome, UUID idIgnorado) {
        if (nome == null || nome.isBlank()) {
            return;
        }
        String normalizado = SpecificationUtils.normalizarTermo(nome.trim());
        repository.findByNomeNormalizadoAndAtivoTrue(normalizado)
                .filter(existente -> idIgnorado == null || !existente.getId().equals(idIgnorado))
                .ifPresent(existente -> {
                    throw new InvarianteVioladaException("Já existe um tipo de inspeção ativo com o nome '" + nome.trim() + "'.");
                });
    }
}
