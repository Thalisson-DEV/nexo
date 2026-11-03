package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.AnexoObraResponse;
import com.nexo.app.modules.obras.domain.DocumentoAsBuilt;
import com.nexo.app.modules.obras.domain.EstruturaChecklist;
import com.nexo.app.modules.obras.domain.EvidenciaPonto;
import com.nexo.app.modules.obras.domain.FichaAsBuilt;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.TipoAnexoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.DocumentoAsBuiltRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.EquipamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.EvidenciaPontoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.FichaAsBuiltRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemPlanejadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PlanejamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.TipoAnexoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.VaoPlanejamentoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Monta o As Built em segundo plano: as folhas do projeto com o que foi executado e, de cada ponto, a ficha e as fotos
 * do book. O arquivo entra nos anexos da obra como mais uma versão, sem tocar nas anteriores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeracaoAsBuiltService {

    private static final String TIPO_ANEXO = "AS BUILT";
    private static final DateTimeFormatter DATA_DA_FOTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DocumentoAsBuiltRepository documentoRepository;
    private final ObraRepository obraRepository;
    private final PlanejamentoObraRepository planejamentoRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final VaoPlanejamentoRepository vaoRepository;
    private final ItemPlanejadoRepository itemRepository;
    private final FichaAsBuiltRepository fichaRepository;
    private final EquipamentoObraRepository equipamentoRepository;
    private final EvidenciaPontoRepository evidenciaRepository;
    private final ModeloChecklistObraUseCases modeloUseCases;
    private final ObjectStorageService objectStorage;
    private final TipoAnexoObraRepository tipoAnexoRepository;
    private final AnexoObraUseCases anexoUseCases;
    private final DocumentoTecnicoPdf documentoPdf;

    @Async
    public void processar(UUID idDocumento) {
        DocumentoAsBuilt documento = documentoRepository.findById(idDocumento).orElse(null);
        if (documento == null) {
            return;
        }
        try {
            documento.concluir(gerar(documento));
        } catch (InvarianteVioladaException e) {
            documento.falhar(e.getMessage());
        } catch (Exception e) {
            log.warn("Falha ao gerar o As Built {}", idDocumento, e);
            documento.falhar("Não foi possível gerar o documento. Confira o projeto mestre da obra e gere de novo.");
        }
        documentoRepository.save(documento);
    }

    private UUID gerar(DocumentoAsBuilt documento) throws IOException {
        UUID idObra = documento.getIdObra();
        Obra obra = obraRepository.findById(idObra).orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada."));
        TipoAnexoObra tipo = tipoAnexoRepository.findByNomeIgnoreCase(TIPO_ANEXO)
                .orElseThrow(() -> new InvarianteVioladaException("Cadastre o tipo de anexo \"AS BUILT\" para gerar o documento."));
        UUID idMestre = planejamentoRepository.findByIdObraAndAtivoTrue(idObra).orElseThrow().getIdAnexoMestre();

        byte[] projeto;
        try (InputStream stream = anexoUseCases.baixar(idObra, idMestre).stream()) {
            projeto = stream.readAllBytes();
        }
        Set<UUID> pontosExecutados = Set.copyOf(itemRepository.pontosComExecucao(idObra));
        List<PontoPlanejamento> pontos = pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(idObra);
        List<FichaAsBuilt> fichas = fichaRepository.findByIdObraAndAtivoTrue(idObra).stream()
                .filter(f -> pontosExecutados.contains(f.getIdPonto())).toList();
        byte[] pdf = documentoPdf.asBuilt(new DocumentoTecnicoPdf.DadosAsBuilt(obra, documento.getNumeroVersao(), projeto, pontos, pontosExecutados,
                vaoRepository.findByIdObraAndAtivoTrue(idObra), Set.copyOf(itemRepository.vaosComExecucao(idObra)), fichas,
                equipamentoRepository.findByIdObraAndAtivoTrue(idObra), fotos(idObra, pontosExecutados)));

        String nome = "as-built-" + obra.getNumero().replaceAll("[^a-zA-Z0-9._-]", "_") + "-v" + documento.getNumeroVersao() + ".pdf";
        AnexoObraResponse anexo = anexoUseCases.anexarGerado(idObra, tipo, nome, "application/pdf", pdf, "As Built, versão " + documento.getNumeroVersao());
        return anexo.id();
    }

    /**
     * Fotos dos pontos executados, por ponto: primeiro os itens, na ordem do modelo, depois as avulsas. Público porque
     * o relatório de medição usa as mesmas fotos e o serviço é chamado pelo proxy do {@code @Async}.
     */
    public Map<UUID, List<DocumentoTecnicoPdf.Foto>> fotos(UUID idObra, Set<UUID> pontosExecutados) throws IOException {
        List<EvidenciaPonto> evidencias = evidenciaRepository.findByIdObraAndAtivoTrueOrderByDataCriacaoAsc(idObra).stream()
                .filter(e -> pontosExecutados.contains(e.getIdPonto()))
                .toList();
        Map<String, Integer> ordemDosItens = new HashMap<>();
        modeloUseCases.buscarEntidades(evidencias.stream().map(EvidenciaPonto::getIdModelo).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .map(ModeloChecklistObra::getEstrutura)
                .map(EstruturaChecklist::evidencias)
                .forEach(itens -> itens.forEach(item -> ordemDosItens.put(item.id(), ordemDosItens.size())));
        List<EvidenciaPonto> ordenadas = evidencias.stream()
                .sorted(Comparator.comparingInt((EvidenciaPonto e) -> e.getIdItem() == null ? Integer.MAX_VALUE : ordemDosItens.getOrDefault(e.getIdItem(), Integer.MAX_VALUE - 1)))
                .toList();
        Map<UUID, List<DocumentoTecnicoPdf.Foto>> porPonto = new HashMap<>();
        for (EvidenciaPonto evidencia : ordenadas) {
            porPonto.computeIfAbsent(evidencia.getIdPonto(), id -> new ArrayList<>()).add(foto(evidencia));
        }
        return porPonto;
    }

    private DocumentoTecnicoPdf.Foto foto(EvidenciaPonto evidencia) throws IOException {
        byte[] original;
        try (InputStream stream = objectStorage.baixar(evidencia.getChaveStorage()).conteudo()) {
            original = stream.readAllBytes();
        }
        String autoria = evidencia.getDataCriacao().atZoneSameInstant(ApontamentoExecucaoUseCases.FUSO).format(DATA_DA_FOTO)
                + (evidencia.getUsuarioCriacao() != null ? " · " + evidencia.getUsuarioCriacao() : "");
        return new DocumentoTecnicoPdf.Foto(evidencia.getTituloItem() != null ? evidencia.getTituloItem() : "Outras evidências", evidencia.getLegenda(), autoria,
                FotoEvidencia.jpeg(FotoEvidencia.ler(original), FotoEvidencia.LADO_DOCUMENTO, 0.78f));
    }
}
