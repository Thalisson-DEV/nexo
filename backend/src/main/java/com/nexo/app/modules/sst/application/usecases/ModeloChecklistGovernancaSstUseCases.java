package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaFilter;
import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaResponse;
import com.nexo.app.modules.sst.application.dto.DuplicarModeloChecklistGovernancaRequest;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.ModeloChecklistGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.ExecucaoChecklistGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ModeloChecklistGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ModeloChecklistGovernancaSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ModeloChecklistGovernancaSstUseCases {

    private final ModeloChecklistGovernancaSstRepository repository;
    private final FormularioInspecaoRepository formularioRepository;
    private final ExecucaoChecklistGovernancaSstRepository execucaoRepository;
    private final FormularioInspecaoUseCases formularioUseCases;

    @Transactional
    public ModeloChecklistGovernancaResponse criar(ModeloChecklistGovernancaRequest request) {
        FormularioInspecao formulario = buscarFormulario(request.idFormulario());
        formulario.validarEstrutura();
        ModeloChecklistGovernancaSst modelo = new ModeloChecklistGovernancaSst(
                formulario.getId(), request.tipoCorpo(), request.nome(), request.orientacao(),
                request.referenciaNormativa());
        return montar(repository.save(modelo), formulario);
    }

    @Transactional
    public ModeloChecklistGovernancaResponse atualizar(UUID id,
                                                        ModeloChecklistGovernancaRequest request) {
        ModeloChecklistGovernancaSst modelo = buscar(id);
        conferirVersao(modelo, request.versaoEsperada());
        if (!modelo.getIdFormulario().equals(request.idFormulario())
                || modelo.getTipoCorpo() != request.tipoCorpo()) {
            throw new InvarianteVioladaException(
                    "Formulário e tipo do corpo não podem ser trocados. Crie outro modelo.");
        }
        modelo.atualizar(request.nome(), request.orientacao(), request.referenciaNormativa());
        return montar(repository.save(modelo), buscarFormulario(modelo.getIdFormulario()));
    }

    @Transactional
    public ModeloChecklistGovernancaResponse duplicar(
            UUID id, DuplicarModeloChecklistGovernancaRequest request) {
        ModeloChecklistGovernancaSst original = buscar(id);
        var formularioDuplicado = formularioUseCases.duplicar(original.getIdFormulario());
        ModeloChecklistGovernancaSst copia = new ModeloChecklistGovernancaSst(
                formularioDuplicado.id(), original.getTipoCorpo(), request.nome(),
                request.orientacao() == null ? original.getOrientacao() : request.orientacao(),
                request.referenciaNormativa() == null
                        ? original.getReferenciaNormativa() : request.referenciaNormativa());
        return montar(repository.save(copia), buscarFormulario(formularioDuplicado.id()));
    }

    @Transactional
    public void desativar(UUID id, Long versaoEsperada) {
        ModeloChecklistGovernancaSst modelo = buscar(id);
        conferirVersao(modelo, versaoEsperada);
        modelo.desativar();
        repository.save(modelo);
    }

    @Transactional
    public void ativar(UUID id, Long versaoEsperada) {
        ModeloChecklistGovernancaSst modelo = buscar(id);
        conferirVersao(modelo, versaoEsperada);
        buscarFormulario(modelo.getIdFormulario());
        modelo.ativar();
        repository.save(modelo);
    }

    private static void conferirVersao(ModeloChecklistGovernancaSst modelo, Long esperada) {
        if (esperada == null || !Objects.equals(modelo.getVersao(), esperada)) {
            throw new ConflitoGovernancaSstException(
                    "O modelo foi alterado por outra pessoa. Atualize os dados antes de continuar.");
        }
    }

    @Transactional(readOnly = true)
    public ModeloChecklistGovernancaResponse buscarPorId(UUID id) {
        ModeloChecklistGovernancaSst modelo = buscar(id);
        return montar(modelo, buscarFormulario(modelo.getIdFormulario()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ModeloChecklistGovernancaResponse> listar(
            ModeloChecklistGovernancaFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                ModeloChecklistGovernancaSstSpecification.comFiltros(filtro), pageable)
                .map(modelo -> montar(modelo, buscarFormulario(modelo.getIdFormulario()))));
    }

    ModeloChecklistGovernancaSst buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Modelo de checklist de governança não encontrado."));
    }

    private FormularioInspecao buscarFormulario(UUID id) {
        return formularioRepository.findById(id)
                .filter(FormularioInspecao::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Formulário de inspeção não encontrado ou inativo."));
    }

    private ModeloChecklistGovernancaResponse montar(ModeloChecklistGovernancaSst modelo,
                                                      FormularioInspecao formulario) {
        return new ModeloChecklistGovernancaResponse(
                modelo.getId(), modelo.getIdFormulario(), formulario.getTitulo(),
                modelo.getTipoCorpo(), modelo.getNome(), modelo.getOrientacao(),
                modelo.getReferenciaNormativa(), modelo.isAtivo(),
                modelo.getId() != null && execucaoRepository.existsByIdFormulario(modelo.getIdFormulario()),
                modelo.getVersao(), modelo.getDataCriacao());
    }
}
