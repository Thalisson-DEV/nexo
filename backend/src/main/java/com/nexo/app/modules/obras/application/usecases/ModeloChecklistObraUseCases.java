package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraFilter;
import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraRequest;
import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraResponse;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import com.nexo.app.modules.obras.infrastructure.persistence.EvidenciaPontoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.FichaAsBuiltRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ModeloChecklistObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ModeloChecklistObraSpecification;
import com.nexo.app.modules.obras.infrastructure.persistence.VisitaPreviaObraRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Modelos de checklist da documentação técnica. Como nos formulários de inspeção do SST, depois do primeiro uso só
 * título e descrição mudam; para mudar a estrutura, duplica-se o modelo.
 */
@Service
@RequiredArgsConstructor
public class ModeloChecklistObraUseCases {

    private final ModeloChecklistObraRepository repository;
    private final VisitaPreviaObraRepository visitaRepository;
    private final FichaAsBuiltRepository fichaRepository;
    private final EvidenciaPontoRepository evidenciaRepository;

    @Transactional
    public ModeloChecklistObraResponse criar(ModeloChecklistObraRequest request) {
        exigirTituloLivre(request.finalidade(), request.titulo(), null);
        return paraResponse(repository.save(new ModeloChecklistObra(request.finalidade(), request.titulo(), request.descricao(), request.estrutura())));
    }

    @Transactional
    public ModeloChecklistObraResponse atualizar(UUID id, ModeloChecklistObraRequest request) {
        ModeloChecklistObra modelo = buscarEntidade(id);
        if (modelo.getFinalidade() != request.finalidade()) {
            throw new InvarianteVioladaException("A finalidade não muda depois do cadastro.");
        }
        exigirTituloLivre(modelo.getFinalidade(), request.titulo(), id);
        if (!modelo.getEstrutura().equals(request.estrutura())) {
            if (emUso(id)) {
                throw new InvarianteVioladaException("Este modelo já foi usado: duplique-o para mudar as perguntas e as evidências.");
            }
            modelo.alterarEstrutura(request.estrutura());
        }
        modelo.atualizarDados(request.titulo(), request.descricao());
        return paraResponse(repository.save(modelo));
    }

    @Transactional(readOnly = true)
    public ModeloChecklistObraResponse buscarPorId(UUID id) {
        return paraResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ModeloChecklistObraResponse> listar(ModeloChecklistObraFilter filter, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(ModeloChecklistObraSpecification.filterBy(filter), pageable).map(this::paraResponse));
    }

    @Transactional
    public ModeloChecklistObraResponse duplicar(UUID id) {
        ModeloChecklistObra origem = buscarEntidade(id);
        String titulo = tituloDaCopia(origem);
        return paraResponse(repository.save(new ModeloChecklistObra(origem.getFinalidade(), titulo, origem.getDescricao(), origem.getEstrutura())));
    }

    @Transactional
    public void desativar(UUID id) {
        ModeloChecklistObra modelo = buscarEntidade(id);
        modelo.desativar();
        repository.save(modelo);
    }

    @Transactional
    public void ativar(UUID id) {
        ModeloChecklistObra modelo = buscarEntidade(id);
        exigirTituloLivre(modelo.getFinalidade(), modelo.getTitulo(), id);
        modelo.ativar();
        repository.save(modelo);
    }

    ModeloChecklistObra buscarEntidade(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Modelo de checklist não encontrado."));
    }

    List<ModeloChecklistObra> buscarEntidades(Collection<UUID> ids) {
        return repository.findAllById(ids);
    }

    private String tituloDaCopia(ModeloChecklistObra origem) {
        String base = origem.getTitulo().length() > 130 ? origem.getTitulo().substring(0, 130) : origem.getTitulo();
        String titulo = base + " (cópia)";
        for (int n = 2; repository.existsByFinalidadeAndTituloIgnoreCaseAndAtivoTrue(origem.getFinalidade(), titulo); n++) {
            titulo = base + " (cópia " + n + ")";
        }
        return titulo;
    }

    private void exigirTituloLivre(ModeloChecklistObra.Finalidade finalidade, String titulo, UUID id) {
        boolean ocupado = id == null
                ? repository.existsByFinalidadeAndTituloIgnoreCaseAndAtivoTrue(finalidade, titulo.trim())
                : repository.existsByFinalidadeAndTituloIgnoreCaseAndAtivoTrueAndIdNot(finalidade, titulo.trim(), id);
        if (ocupado) {
            throw new InvarianteVioladaException("Já existe um modelo ativo com o título " + titulo.trim() + ".");
        }
    }

    private boolean emUso(UUID id) {
        return visitaRepository.existsByIdModelo(id) || fichaRepository.existsByIdModelo(id) || evidenciaRepository.existsByIdModelo(id);
    }

    private ModeloChecklistObraResponse paraResponse(ModeloChecklistObra modelo) {
        return new ModeloChecklistObraResponse(modelo.getId(), modelo.getFinalidade(), modelo.getTitulo(), modelo.getDescricao(), modelo.getEstrutura(),
                modelo.getEstrutura().grupos().size(), modelo.getEstrutura().grupos().stream().mapToInt(g -> g.perguntas().size()).sum(),
                emUso(modelo.getId()), modelo.isAtivo());
    }
}
