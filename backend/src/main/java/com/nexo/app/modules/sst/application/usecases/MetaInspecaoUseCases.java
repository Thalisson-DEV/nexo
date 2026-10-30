package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.FuncaoApi;
import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoResponse;
import com.nexo.app.modules.sst.application.mappers.MetaInspecaoMapper;
import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.JanelaMeta;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.METAS_INSPECAO;

/**
 * Orquestra o cadastro das metas de inspeção por função ou por colaborador.
 *
 * <p>O alvo é validado contra o módulo de RH e não pode ser trocado depois — para
 * apontar a meta a outro alvo, cadastre outra meta. Duas metas ativas para o mesmo
 * alvo com vigências sobrepostas são rejeitadas, pois tornariam a cobrança ambígua.</p>
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = METAS_INSPECAO)
public class MetaInspecaoUseCases {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final MetaInspecaoRepository repository;
    private final TipoInspecaoRepository tipoInspecaoRepository;
    private final MetaInspecaoMapper mapper;
    private final FuncaoApi funcaoApi;
    private final ColaboradorApi colaboradorApi;

    @Transactional
    @CacheEvict(allEntries = true)
    public MetaInspecaoResponse criar(MetaInspecaoRequest request) {
        validarAlvo(request.idFuncao(), request.idColaborador());
        validarTipoInspecao(request.idTipoInspecao());

        MetaInspecao meta = new MetaInspecao(
                request.idTipoInspecao(), request.idFuncao(), request.idColaborador(), request.quantidade(),
                request.periodicidade(), request.vigenciaInicio(), request.vigenciaFim(), request.descricao());

        validarSobreposicao(meta, null);

        return hidratar(repository.save(meta));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public MetaInspecaoResponse atualizar(UUID id, MetaInspecaoRequest request) {
        MetaInspecao meta = buscarEntidade(id);
        validarAlvoImutavel(meta, request);

        meta.atualizar(request.quantidade(), request.periodicidade(),
                request.vigenciaInicio(), request.vigenciaFim(), request.descricao());

        validarSobreposicao(meta, id);

        return hidratar(repository.save(meta));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        MetaInspecao meta = buscarEntidade(id);
        meta.desativar();
        repository.save(meta);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        MetaInspecao meta = buscarEntidade(id);
        meta.ativar();
        validarSobreposicao(meta, id);
        repository.save(meta);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public MetaInspecaoResponse buscarPorId(UUID id) {
        return hidratar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<MetaInspecaoResponse> listar(MetaInspecaoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(MetaInspecaoSpecification.filterBy(filter), pageable)
                        .map(this::hidratar)
        );
    }

    private void validarAlvo(UUID idFuncao, UUID idColaborador) {
        if (idFuncao != null && !funcaoApi.existeFuncao(idFuncao)) {
            throw new RecursoNaoEncontradoException("Função não encontrada ou inativa com ID: " + idFuncao);
        }
        if (idColaborador != null && !colaboradorApi.existeColaborador(idColaborador)) {
            throw new RecursoNaoEncontradoException("Colaborador não encontrado ou inativo com ID: " + idColaborador);
        }
    }

    private void validarTipoInspecao(UUID idTipoInspecao) {
        if (idTipoInspecao == null) {
            throw new InvarianteVioladaException("O tipo de inspeção é obrigatório.");
        }
        TipoInspecao tipo = tipoInspecaoRepository.findById(idTipoInspecao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de inspeção não encontrado com ID: " + idTipoInspecao));
        if (!tipo.isAtivo()) {
            throw new InvarianteVioladaException("O tipo de inspeção selecionado está inativo.");
        }
    }

    private void validarAlvoImutavel(MetaInspecao meta, MetaInspecaoRequest request) {
        boolean mesmoAlvo = (meta.getEscopo() == EscopoMeta.FUNCAO
                ? meta.getIdFuncao().equals(request.idFuncao()) && request.idColaborador() == null
                : meta.getIdColaborador().equals(request.idColaborador()) && request.idFuncao() == null)
                && meta.getIdTipoInspecao().equals(request.idTipoInspecao());

        if (!mesmoAlvo) {
            throw new InvarianteVioladaException(
                    "O alvo e o tipo de inspeção de uma meta não podem ser alterados. Cadastre uma nova meta para o outro alvo/tipo.");
        }
    }

    private void validarSobreposicao(MetaInspecao meta, UUID idIgnorar) {
        List<MetaInspecao> candidatas = meta.getEscopo() == EscopoMeta.FUNCAO
                ? repository.findByAtivoTrueAndIdFuncao(meta.getIdFuncao())
                : repository.findByAtivoTrueAndIdColaborador(meta.getIdColaborador());

        boolean conflito = candidatas.stream()
                .filter(outra -> idIgnorar == null || !idIgnorar.equals(outra.getId()))
                .anyMatch(meta::conflitaCom);

        if (conflito) {
            throw new InvarianteVioladaException(
                    "Já existe uma meta ativa para este alvo com vigência sobreposta. "
                            + "Encerre ou ajuste a meta existente antes de cadastrar outra.");
        }
    }

    private MetaInspecaoResponse hidratar(MetaInspecao meta) {
        MetaInspecaoResponse base = mapper.toResponse(meta);
        Optional<JanelaMeta> janela = meta.janelaEm(LocalDate.now(ZONA));
        String nomeTipo = tipoInspecaoRepository.findById(meta.getIdTipoInspecao())
                .map(TipoInspecao::getNome).orElse(null);

        return new MetaInspecaoResponse(
                base.id(), meta.getIdTipoInspecao(), nomeTipo, base.escopo(),
                base.idFuncao(), nomeFuncao(meta.getIdFuncao()),
                base.idColaborador(), nomeColaborador(meta.getIdColaborador()),
                base.quantidade(), base.periodicidade(),
                base.vigenciaInicio(), base.vigenciaFim(),
                base.descricao(), base.ativo(),
                janela.map(JanelaMeta::inicio).orElse(null),
                janela.map(JanelaMeta::fim).orElse(null));
    }

    private String nomeFuncao(UUID idFuncao) {
        return idFuncao == null ? null
                : funcaoApi.buscarPorIdResponse(idFuncao).map(FuncaoResponse::nome).orElse(null);
    }

    private String nomeColaborador(UUID idColaborador) {
        return idColaborador == null ? null
                : colaboradorApi.buscarPorIdResponse(idColaborador)
                        .map(ColaboradorResponse::nomeCompleto).orElse(null);
    }

    private MetaInspecao buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Meta de inspeção não encontrada com ID: " + id));
    }
}
