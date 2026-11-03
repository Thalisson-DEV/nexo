package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.DownloadEvidenciaDTO;
import com.nexo.app.modules.obras.domain.EstruturaChecklist;
import com.nexo.app.modules.obras.domain.EvidenciaPonto;
import com.nexo.app.modules.obras.domain.FichaAsBuilt;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import com.nexo.app.modules.obras.infrastructure.persistence.EvidenciaPontoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.FichaAsBuiltRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Fotos do book da obra. Valem para qualquer ponto desde o início, porque a foto do "antes" vem antes da execução, e
 * são os mesmos comandos para o web e para o app. Removida, a foto só sai de cena: o arquivo fica no armazenamento.
 */
@Service
@RequiredArgsConstructor
public class EvidenciaPontoUseCases {

    private final EvidenciaPontoRepository repository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final FichaAsBuiltRepository fichaRepository;
    private final ModeloChecklistObraUseCases modeloUseCases;
    private final ObjectStorageService objectStorage;

    @Transactional
    public UUID registrar(UUID idObra, UUID idPonto, UUID idModelo, String idItem, String legenda, MultipartFile arquivo) {
        pontoRepository.findByIdAndIdObraAndAtivoTrue(idPonto, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ponto não encontrado nesta obra."));
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Escolha a foto.");
        }
        EvidenciaPonto.ItemRespondido item = idItem == null || idItem.isBlank() ? null : itemDoModelo(idPonto, idModelo, idItem);
        byte[] conteudo = ler(arquivo);
        byte[] miniatura = FotoEvidencia.jpeg(FotoEvidencia.ler(conteudo), FotoEvidencia.LADO_MINIATURA, 0.75f);

        String nome = nomeSeguro(arquivo.getOriginalFilename());
        String contentType = arquivo.getContentType() != null ? arquivo.getContentType() : "image/jpeg";
        String base = "obras/" + idObra + "/book/" + idPonto + "/" + UUID.randomUUID();
        String chave = base + "_" + nome;
        String chaveMiniatura = base + "_miniatura.jpg";
        objectStorage.enviar(chave, new ByteArrayInputStream(conteudo), conteudo.length, contentType);
        objectStorage.enviar(chaveMiniatura, new ByteArrayInputStream(miniatura), miniatura.length, "image/jpeg");

        return repository.save(new EvidenciaPonto(idObra, idPonto, item, legenda,
                new EvidenciaPonto.Arquivo(nome, contentType, conteudo.length, chave, chaveMiniatura))).getId();
    }

    @Transactional
    public void alterarLegenda(UUID idObra, UUID idEvidencia, String legenda) {
        EvidenciaPonto evidencia = buscar(idObra, idEvidencia);
        evidencia.alterarLegenda(legenda);
        repository.save(evidencia);
    }

    @Transactional
    public void remover(UUID idObra, UUID idEvidencia) {
        EvidenciaPonto evidencia = buscar(idObra, idEvidencia);
        evidencia.desativar();
        repository.save(evidencia);
    }

    @Transactional(readOnly = true)
    public DownloadEvidenciaDTO baixar(UUID idObra, UUID idEvidencia) {
        EvidenciaPonto evidencia = buscar(idObra, idEvidencia);
        StorageObject objeto = objectStorage.baixar(evidencia.getChaveStorage());
        return new DownloadEvidenciaDTO(objeto.conteudo(), evidencia.getNomeArquivo(), evidencia.getContentType(), evidencia.getTamanhoBytes());
    }

    @Transactional(readOnly = true)
    public DownloadEvidenciaDTO baixarMiniatura(UUID idObra, UUID idEvidencia) {
        EvidenciaPonto evidencia = buscar(idObra, idEvidencia);
        StorageObject objeto = objectStorage.baixar(evidencia.getChaveMiniatura());
        return new DownloadEvidenciaDTO(objeto.conteudo(), "miniatura.jpg", "image/jpeg", objeto.tamanhoBytes());
    }

    /** O ponto segue um modelo só: o da ficha ou, antes dela, o das fotos já lançadas. */
    private EvidenciaPonto.ItemRespondido itemDoModelo(UUID idPonto, UUID idModelo, String idItem) {
        if (idModelo == null) {
            throw new InvarianteVioladaException("Informe o modelo do item.");
        }
        Optional<UUID> doPonto = fichaRepository.findByIdPontoAndAtivoTrue(idPonto).map(FichaAsBuilt::getIdModelo)
                .or(() -> repository.findFirstByIdPontoAndIdModeloIsNotNullAndAtivoTrue(idPonto).map(EvidenciaPonto::getIdModelo));
        if (doPonto.isPresent() && !doPonto.get().equals(idModelo)) {
            throw new InvarianteVioladaException("Este ponto já segue outro modelo.");
        }
        ModeloChecklistObra modelo = modeloUseCases.buscarEntidade(idModelo);
        if (modelo.getFinalidade() != ModeloChecklistObra.Finalidade.AS_BUILT) {
            throw new InvarianteVioladaException("O modelo escolhido não é de ficha As Built.");
        }
        EstruturaChecklist.ItemEvidencia item = modelo.getEstrutura().evidencias().stream()
                .filter(i -> i.id().equals(idItem))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("O item de evidência não faz parte do modelo."));
        return new EvidenciaPonto.ItemRespondido(idModelo, item.id(), item.titulo());
    }

    private EvidenciaPonto buscar(UUID idObra, UUID idEvidencia) {
        return repository.findByIdAndIdObraAndAtivoTrue(idEvidencia, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada nesta obra."));
    }

    private static byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler a foto enviada.", e);
        }
    }

    private static String nomeSeguro(String nome) {
        return nome == null || nome.isBlank() ? "foto.jpg" : nome.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
