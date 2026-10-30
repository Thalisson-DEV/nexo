package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.CategoriaDocumentoSstFilter;
import com.nexo.app.modules.sst.application.dto.CategoriaDocumentoSstRequest;
import com.nexo.app.modules.sst.application.dto.CategoriaDocumentoSstResponse;
import com.nexo.app.modules.sst.domain.CategoriaDocumentoSst;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.infrastructure.persistence.CategoriaDocumentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CategoriaDocumentoSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoriaDocumentoSstUseCases {

    private final CategoriaDocumentoSstRepository repository;

    @Transactional
    public CategoriaDocumentoSstResponse criar(CategoriaDocumentoSstRequest request, UUID ator) {
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCodigoAndAtivoTrue(codigo)) {
            throw new ConflitoGovernancaSstException(
                    "Já existe uma categoria ativa com este código; crie uma nova revisão.");
        }
        CategoriaDocumentoSst categoria = construir(request, ator, 1);
        return montar(repository.save(categoria));
    }

    @Transactional
    public CategoriaDocumentoSstResponse versionar(UUID id, CategoriaDocumentoSstRequest request,
                                                    UUID ator) {
        CategoriaDocumentoSst anterior = buscar(id);
        if (!anterior.getCodigo().equals(request.codigo().trim().toUpperCase(Locale.ROOT))) {
            throw new ConflitoGovernancaSstException("O código não pode mudar entre revisões da categoria.");
        }
        CategoriaDocumentoSst proxima = anterior.criarProximaVersao(
                request.nome(), request.descricao(), request.contexto(), request.natureza(),
                request.escopoTitular(), request.jurisdicao(), request.autoridadeEmissora(),
                request.identificadorFonte(), request.edicaoFonte(), request.urlFonte(),
                request.vigenciaInicio(), request.vigenciaFim(), request.confirmada(),
                request.confirmada() ? ator : null, request.exigeNumero(), request.exigeEmissor(),
                request.exigeDataEmissao(), request.exigeDataValidade(), request.semValidade(),
                request.obrigatoria(), request.retencaoMinimaAnos(), request.referenciaRetencao(),
                request.faixasAlertaDias());
        repository.save(anterior);
        return montar(repository.save(proxima));
    }

    @Transactional
    public CategoriaDocumentoSstResponse confirmar(UUID id, UUID ator) {
        CategoriaDocumentoSst categoria = buscar(id);
        categoria.confirmar(ator);
        return montar(repository.save(categoria));
    }

    @Transactional(readOnly = true)
    public CategoriaDocumentoSstResponse buscarPorId(UUID id) {
        return montar(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoriaDocumentoSstResponse> listar(
            CategoriaDocumentoSstFilter filter, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                CategoriaDocumentoSstSpecification.comFiltros(filter), pageable).map(this::montar));
    }

    private CategoriaDocumentoSst construir(CategoriaDocumentoSstRequest request, UUID ator,
                                             int revisao) {
        return new CategoriaDocumentoSst(
                request.codigo(), revisao, request.nome(), request.descricao(), request.contexto(),
                request.natureza(), request.escopoTitular(), request.jurisdicao(),
                request.autoridadeEmissora(), request.identificadorFonte(), request.edicaoFonte(),
                request.urlFonte(), request.vigenciaInicio(), request.vigenciaFim(),
                request.confirmada(), request.confirmada() ? ator : null, request.exigeNumero(),
                request.exigeEmissor(), request.exigeDataEmissao(), request.exigeDataValidade(),
                request.semValidade(), request.obrigatoria(), request.retencaoMinimaAnos(),
                request.referenciaRetencao(), request.faixasAlertaDias());
    }

    private CategoriaDocumentoSst buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Categoria documental não encontrada ou inativa."));
    }

    private CategoriaDocumentoSstResponse montar(CategoriaDocumentoSst categoria) {
        return new CategoriaDocumentoSstResponse(
                categoria.getId(), categoria.getCodigo(), categoria.getRevisaoCategoria(),
                categoria.getNome(), categoria.getDescricao(), categoria.getContexto(),
                categoria.getNatureza(), categoria.getEscopoTitular(), categoria.getJurisdicao(),
                categoria.getAutoridadeEmissora(), categoria.getIdentificadorFonte(),
                categoria.getEdicaoFonte(), categoria.getUrlFonte(), categoria.getVigenciaInicio(),
                categoria.getVigenciaFim(), categoria.isConfirmada(), categoria.getDataConfirmacao(),
                categoria.isExigeNumero(), categoria.isExigeEmissor(),
                categoria.isExigeDataEmissao(), categoria.isExigeDataValidade(),
                categoria.isSemValidade(), categoria.isObrigatoria(),
                categoria.getRetencaoMinimaAnos(), categoria.getReferenciaRetencao(),
                new LinkedHashSet<>(categoria.getFaixasAlertaDias()), categoria.isAtivo(),
                categoria.getVersao());
    }
}
