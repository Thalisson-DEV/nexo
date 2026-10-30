package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ModeloAtaSstDto;
import com.nexo.app.modules.sst.domain.ModeloAtaSst;
import com.nexo.app.modules.sst.domain.ArquivoModeloAtaSst;
import com.nexo.app.modules.sst.domain.StatusModeloAtaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.ModeloAtaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ArquivoModeloAtaSstRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModeloAtaSstUseCases {
    private final ModeloAtaSstRepository repository;
    private final ArquivoModeloAtaSstRepository arquivoRepository;
    private final ArquivoDocumentoSstService arquivoService;

    @Transactional
    public ModeloAtaSstDto.ArquivoResponse enviarBaseDocx(UUID id, MultipartFile arquivo,
                                                           String mapeamentoSlots, UUID ator) {
        ModeloAtaSst modelo = buscar(id);
        ArquivoDocumentoSstService.ArquivoProcessado processado = arquivoService.processar(arquivo);
        if (!ArquivoModeloAtaSst.DOCX.equals(processado.metadados().contentType())) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "A base do modelo deve ser um arquivo DOCX válido.");
        }
        try (java.io.InputStream is = arquivo.getInputStream();
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            // valida estrutura básica
        } catch (Exception e) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException("O arquivo enviado não é um documento DOCX válido ou está corrompido.");
        }
        arquivoService.enviar(processado);
        try {
            ArquivoModeloAtaSst base = new ArquivoModeloAtaSst(modelo.getId(),
                    processado.metadados().nomeArquivo(), processado.metadados().tamanhoBytes(),
                    processado.metadados().objectKey(), processado.metadados().sha256(), mapeamentoSlots);
            base.validar(ator);
            base = arquivoRepository.save(base);
            modelo.vincularArquivoDocx(base.getId(), base.getSha256());
            repository.save(modelo);
            return new ModeloAtaSstDto.ArquivoResponse(base.getId(), base.getNomeArquivo(),
                    base.getContentType(), base.getTamanhoBytes(), base.getSha256(), base.validado());
        } catch (RuntimeException exception) {
            arquivoService.remover(processado.metadados().objectKey());
            throw exception;
        }
    }

    @Transactional
    public ModeloAtaSstDto.Response criar(ModeloAtaSstDto.Request request) {
        ModeloAtaSst modelo = new ModeloAtaSst(request.nome(), request.tipoCorpo(), request.idBaseAplicavel(),
                request.orientacoesGerais());
        return toResponse(repository.save(modelo));
    }

    @Transactional
    public ModeloAtaSstDto.Response atualizar(UUID id, ModeloAtaSstDto.AtualizarRequest request) {
        ModeloAtaSst modelo = buscar(id);
        modelo.atualizarRascunho(request.nome(), request.orientacoesGerais());
        return toResponse(repository.save(modelo));
    }

    @Transactional
    public ModeloAtaSstDto.Response duplicar(UUID id) {
        return toResponse(repository.save(buscar(id).duplicar()));
    }

    @Transactional
    public ModeloAtaSstDto.Response criarRevisao(UUID id) {
        return toResponse(repository.save(buscar(id).criarNovaRevisao()));
    }

    @Transactional
    public ModeloAtaSstDto.Response publicar(UUID id, UUID idUsuario) {
        ModeloAtaSst modelo = buscar(id);
        repository.findFirstByFamiliaIdAndStatusAndAtivoTrueOrderByRevisaoDesc(
                        modelo.getFamiliaId(), StatusModeloAtaSst.PUBLICADO)
                .filter(atual -> !atual.getId().equals(modelo.getId()))
                .ifPresent(atual -> atual.retirar(idUsuario));
        modelo.publicar(idUsuario);
        return toResponse(repository.save(modelo));
    }

    @Transactional
    public ModeloAtaSstDto.Response retirar(UUID id, UUID idUsuario) {
        ModeloAtaSst modelo = buscar(id);
        modelo.retirar(idUsuario);
        return toResponse(repository.save(modelo));
    }

    @Transactional(readOnly = true)
    public ModeloAtaSstDto.Response buscarPorId(UUID id) {
        return toResponse(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<ModeloAtaSstDto.Response> listar() {
        return repository.findAllByAtivoTrueOrderByNomeAscRevisaoDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ModeloAtaSstDto.Response> listarAplicaveis(TipoCorpoGovernancaSst tipo, UUID idBase) {
        return repository.buscarPublicadosAplicaveis(tipo, StatusModeloAtaSst.PUBLICADO, idBase)
                .stream().map(this::toResponse).toList();
    }



    private ModeloAtaSst buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Modelo de ata não encontrado."));
    }

    private ModeloAtaSstDto.Response toResponse(ModeloAtaSst m) {
        ArquivoModeloAtaSst arquivo = m.getArquivoDocxDetails();
        String nomeArquivoDocx = arquivo != null ? arquivo.getNomeArquivo() : null;
        String statusValidacaoDocx = arquivo == null ? "SEM_ARQUIVO" : (arquivo.validado() ? "VALIDO" : "PENDENTE_MAPEAMENTO");
        List<String> errosValidacaoDocx = List.of();
        Integer slotsMapeados = arquivo != null && arquivo.getMapeamentoSlots() != null ? 0 : 0;
        List<String> slotsObrigatoriosPendentes = List.of();
        boolean modeloPublicavel = m.getStatus() == StatusModeloAtaSst.RASCUNHO && arquivo != null && arquivo.validado();

        return new ModeloAtaSstDto.Response(m.getId(), m.getFamiliaId(), m.getRevisao(),
                m.getIdRevisaoAnterior(), m.getIdModeloDuplicado(), m.getTipoCorpo(), m.getIdBaseAplicavel(),
                m.getNome(), m.getOrientacoesGerais(), m.getStatus(), m.isModeloInicial(),
                m.getIdArquivoDocx(), nomeArquivoDocx, m.getHashArquivoDocx(), statusValidacaoDocx,
                errosValidacaoDocx, slotsMapeados, slotsObrigatoriosPendentes, modeloPublicavel, m.getPublicadoEm(),
                m.getRetiradoEm(), m.getVersao(), m.getDataCriacao());
    }
}
