package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ConfirmacaoPerfilNormativoSstRequest;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.RegraPapelPerfilNormativoRequest;
import com.nexo.app.modules.sst.application.mappers.PerfilNormativoGovernancaSstMapper;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.PerfilNormativoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.PerfilNormativoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.PerfilNormativoGovernancaSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SST_GOVERNANCA_PERFIS;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = SST_GOVERNANCA_PERFIS)
public class PerfilNormativoGovernancaSstUseCases {

    private final PerfilNormativoGovernancaSstRepository repository;
    private final PerfilNormativoGovernancaSstMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public PerfilNormativoGovernancaSstResponse criar(PerfilNormativoGovernancaSstRequest request) {
        String codigo = SpecificationUtils.normalizarTermo(request.codigo());
        if (repository.existsByCodigoNormalizadoAndVersaoPerfilAndAtivoTrue(
                codigo, request.versaoPerfil())) {
            throw new InvarianteVioladaException(
                    "Já existe esta versão do perfil normativo. Crie uma edição posterior.");
        }
        PerfilNormativoGovernancaSst perfil = new PerfilNormativoGovernancaSst(
                request.codigo(), request.versaoPerfil(), request.escopo(), request.tipoCorpo(),
                request.jurisdicao(), request.orgaoEmissor(), request.atoNormativo(),
                request.edicao(), request.inicioVigencia(), request.fimVigencia(),
                request.urlOficial(), requisitos(request));
        return mapper.toResponse(repository.save(perfil));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public PerfilNormativoGovernancaSstResponse confirmar(
            UUID id, ConfirmacaoPerfilNormativoSstRequest request) {
        PerfilNormativoGovernancaSst perfil = buscarEntidade(id);
        exigirVersao(perfil.getVersao(), request.versao());
        perfil.confirmar(SecurityUtils.getUsuarioLogadoId());
        return mapper.toResponse(repository.save(perfil));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public PerfilNormativoGovernancaSstResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<PerfilNormativoGovernancaSstResponse> listar(
            PerfilNormativoGovernancaSstFilter filter, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                PerfilNormativoGovernancaSstSpecification.filterBy(filter), pageable).map(mapper::toResponse));
    }

    private PerfilNormativoGovernancaSst buscarEntidade(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil normativo não encontrado."));
    }

    private static Map<PapelMembroGovernancaSst, Integer> requisitos(
            PerfilNormativoGovernancaSstRequest request) {
        Map<PapelMembroGovernancaSst, Integer> resultado =
                new EnumMap<>(PapelMembroGovernancaSst.class);
        if (request.requisitosPapeis() == null) {
            return resultado;
        }
        for (RegraPapelPerfilNormativoRequest regra : request.requisitosPapeis()) {
            if (regra == null || regra.papel() == null || regra.quantidadeMinima() == null) {
                throw new InvarianteVioladaException("Informe todos os dados dos requisitos de papéis.");
            }
            if (resultado.put(regra.papel(), regra.quantidadeMinima()) != null) {
                throw new InvarianteVioladaException("O mesmo papel não pode aparecer duas vezes no perfil.");
            }
        }
        return resultado;
    }

    private static void exigirVersao(Long atual, Long esperada) {
        if (!Objects.equals(atual, esperada)) {
            throw new ConflitoGovernancaSstException(
                    "O perfil normativo foi alterado. Recarregue os dados antes de confirmar.");
        }
    }
}
