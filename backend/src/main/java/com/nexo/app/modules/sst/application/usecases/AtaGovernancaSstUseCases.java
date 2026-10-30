package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.AtaGovernancaDto;
import com.nexo.app.modules.sst.application.port.GeracaoAtaGateway;
import com.nexo.app.modules.sst.domain.ArquivoAtaGeradaSst;
import com.nexo.app.modules.sst.domain.ArquivoModeloAtaSst;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.CopiaAssinadaAtaSst;
import com.nexo.app.modules.sst.domain.EvidenciaFotograficaReuniaoSst;
import com.nexo.app.modules.sst.domain.NormalizacaoTranscricaoSst;
import com.nexo.app.modules.sst.domain.OrigemVersaoAtaSst;
import com.nexo.app.modules.sst.domain.ParticipacaoReuniaoSst;
import com.nexo.app.modules.sst.domain.ReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.RevisaoTranscricaoSst;
import com.nexo.app.modules.sst.domain.StatusPresencaReuniaoSst;
import com.nexo.app.modules.sst.domain.TentativaGeracaoAtaSst;
import com.nexo.app.modules.sst.domain.TipoTentativaGeracaoAtaSst;
import com.nexo.app.modules.sst.domain.VersaoAtaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.ArquivoAtaGeradaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ArquivoModeloAtaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CopiaAssinadaAtaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaFotograficaReuniaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.NormalizacaoTranscricaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ModeloAtaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ParticipacaoReuniaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ReuniaoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.RevisaoTranscricaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TentativaGeracaoAtaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.VersaoAtaSstRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

/** Orquestra versões, IA, fotos e arquivos privados sem manter chamada externa em transação JPA. */
@Service
@RequiredArgsConstructor
public class AtaGovernancaSstUseCases {
    private static final String PROMPT = "ATA_GOVERNANCA_V1";

    private final ReuniaoGovernancaSstRepository reuniaoRepository;
    private final ParticipacaoReuniaoSstRepository participacaoRepository;
    private final RevisaoTranscricaoSstRepository transcricaoRepository;
    private final NormalizacaoTranscricaoSstRepository normalizacaoRepository;
    private final ModeloAtaSstRepository modeloRepository;
    private final VersaoAtaSstRepository versaoRepository;
    private final TentativaGeracaoAtaSstRepository tentativaRepository;
    private final EvidenciaFotograficaReuniaoSstRepository fotoRepository;
    private final CopiaAssinadaAtaSstRepository copiaRepository;
    private final ArquivoModeloAtaSstRepository arquivoModeloRepository;
    private final ArquivoAtaGeradaSstRepository arquivoGeradoRepository;
    private final GeracaoAtaGateway gateway;
    private final RenderizacaoAtaDocxService renderizador;
    private final ObjectStorageService storage;
    private final PlatformTransactionManager transactionManager;

    @Value("${ces.sst.ata-ai.enabled:false}")
    private boolean geracaoIaDisponivel;

    @Value("${ces.sst.ata-ai.normalizacao.enabled:false}")
    private boolean normalizacaoIaDisponivel;

    public com.nexo.app.modules.sst.application.dto.ReuniaoGovernancaDto.CapabilitiesResponse obterCapabilities() {
        return new com.nexo.app.modules.sst.application.dto.ReuniaoGovernancaDto.CapabilitiesResponse(
                geracaoIaDisponivel, normalizacaoIaDisponivel, true);
    }

    public AtaGovernancaDto.VersaoResponse criarManual(UUID idReuniao,
                                                        AtaGovernancaDto.VersaoManualRequest request,
                                                        UUID ator) {
        VersaoAtaSst criada = transacao().execute(status -> {
            ReuniaoGovernancaSst reuniao = buscarParaAtualizar(idReuniao);
            VersaoAtaSst base = request.idVersaoBase() == null ? null
                    : buscarVersao(idReuniao, request.idVersaoBase());
            VersaoAtaSst versao = novaVersao(reuniao, OrigemVersaoAtaSst.MANUAL,
                    request.conteudo(), base, null, null, null, ator);
            reuniaoRepository.save(reuniao);
            return versaoRepository.save(versao);
        });
        return toResponse(criada);
    }

    public List<AtaGovernancaDto.VersaoResponse> listarVersoes(UUID idReuniao) {
        buscar(idReuniao);
        return versaoRepository.findAllByIdReuniaoAndAtivoTrueOrderByNumeroDesc(idReuniao)
                .stream().map(this::toResponse).toList();
    }

    public AtaGovernancaDto.TentativaResponse gerar(UUID idReuniao,
                                                     AtaGovernancaDto.GerarRequest request,
                                                     UUID ator) {
        InicioGeracao inicio = transacao().execute(status -> prepararGeracao(idReuniao, request, ator));
        if (inicio == null) throw new IllegalStateException("A tentativa de geração não foi preparada.");
        if (inicio.existente()) return respostaTentativa(inicio.tentativa());

        GeracaoAtaGateway.Resultado resultado = gateway.gerar(inicio.entrada());
        TentativaGeracaoAtaSst encerrada = transacao().execute(status -> concluirGeracao(inicio, resultado));
        return respostaTentativa(encerrada);
    }

    public AtaGovernancaDto.VersaoResponse aprovar(UUID idReuniao, UUID idVersao, UUID ator) {
        VersaoAtaSst aprovada = transacao().execute(status -> {
            ReuniaoGovernancaSst reuniao = buscarParaAtualizar(idReuniao);
            VersaoAtaSst versao = buscarVersao(idReuniao, idVersao);
            versao.aprovar(ator);
            reuniao.registrarAtaAprovada(versao.getId());
            reuniaoRepository.save(reuniao);
            return versaoRepository.save(versao);
        });
        return toResponse(aprovada);
    }

    public AtaGovernancaDto.EvidenciaFotoResponse anexarFoto(UUID idReuniao, MultipartFile arquivo,
                                                              String legenda, UUID ator) {
        ReuniaoGovernancaSst reuniao = buscar(idReuniao);
        if (!reuniao.permiteAlterarEvidencias()) {
            throw new InvarianteVioladaException("A reunião finalizada não aceita novas fotografias.");
        }
        ArquivoPrivadoReuniao.Recebido recebido = ArquivoPrivadoReuniao.foto(
                arquivo, "sst/governanca/reunioes/" + idReuniao + "/fotos");
        storage.enviar(recebido.objectKey(), new ByteArrayInputStream(recebido.bytes()),
                recebido.tamanho(), recebido.contentType());
        try {
            EvidenciaFotograficaReuniaoSst evidencia = fotoRepository.save(
                    new EvidenciaFotograficaReuniaoSst(idReuniao, recebido.nomeArquivo(),
                            recebido.contentType(), recebido.tamanho(), recebido.objectKey(),
                            recebido.sha256(), legenda, ator));
            return toResponse(evidencia);
        } catch (RuntimeException e) {
            storage.remover(recebido.objectKey());
            throw e;
        }
    }

    public List<AtaGovernancaDto.EvidenciaFotoResponse> listarFotos(UUID idReuniao) {
        buscar(idReuniao);
        return fotoRepository.findAllByIdReuniaoAndAtivoTrueOrderByEnviadaEmDesc(idReuniao)
                .stream().map(this::toResponse).toList();
    }

    public AtaGovernancaDto.Download baixarFoto(UUID idReuniao, UUID idFoto) {
        buscar(idReuniao);
        EvidenciaFotograficaReuniaoSst foto = fotoRepository.findByIdAndIdReuniaoAndAtivoTrue(idFoto, idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fotografia não encontrada."));
        StorageObject objeto = storage.baixar(foto.getObjectKey());
        return new AtaGovernancaDto.Download(objeto.conteudo(), foto.getNomeArquivo(),
                foto.getContentType(), foto.getTamanhoBytes());
    }

    public void removerFoto(UUID idReuniao, UUID idFoto, UUID ator) {
        String objectKey = transacao().execute(status -> {
            ReuniaoGovernancaSst reuniao = buscarParaAtualizar(idReuniao);
            if (!reuniao.permiteAlterarEvidencias()) {
                throw new InvarianteVioladaException(
                        "Reabra a reunião de forma motivada antes de retirar a fotografia.");
            }
            EvidenciaFotograficaReuniaoSst foto = fotoRepository
                    .findByIdAndIdReuniaoAndAtivoTrue(idFoto, idReuniao)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Fotografia não encontrada."));
            foto.remover(ator);
            fotoRepository.save(foto);
            return foto.getObjectKey();
        });
        if (objectKey != null) {
            storage.remover(objectKey);
        }
    }

    public AtaGovernancaDto.ArquivoResponse anexarCopiaAssinada(UUID idReuniao, UUID idVersao,
                                                                 MultipartFile arquivo, UUID ator) {
        ReuniaoGovernancaSst reuniao = buscar(idReuniao);
        VersaoAtaSst versao = buscarVersao(idReuniao, idVersao);
        if (!versao.aprovada() || !idVersao.equals(reuniao.getIdVersaoAtaAprovada())) {
            throw new InvarianteVioladaException("A cópia assinada deve pertencer à versão aprovada atual.");
        }
        if (copiaRepository.existsByIdReuniaoAndIdVersaoAtaAndAtivoTrue(idReuniao, idVersao)) {
            throw new ConflitoGovernancaSstException("A versão já possui uma cópia assinada.");
        }
        ArquivoPrivadoReuniao.Recebido recebido = ArquivoPrivadoReuniao.documentoAssinado(
                arquivo, "sst/governanca/reunioes/" + idReuniao + "/assinadas");
        storage.enviar(recebido.objectKey(), new ByteArrayInputStream(recebido.bytes()),
                recebido.tamanho(), recebido.contentType());
        try {
            CopiaAssinadaAtaSst copia = copiaRepository.save(new CopiaAssinadaAtaSst(idReuniao, idVersao,
                    recebido.nomeArquivo(), recebido.contentType(), recebido.tamanho(), recebido.objectKey(),
                    recebido.sha256(), ator));
            return arquivo(copia);
        } catch (RuntimeException e) {
            storage.remover(recebido.objectKey());
            throw e;
        }
    }

    public AtaGovernancaDto.Download baixarCopiaAssinada(UUID idReuniao, UUID idVersao) {
        buscar(idReuniao);
        CopiaAssinadaAtaSst copia = copiaRepository
                .findByIdReuniaoAndIdVersaoAtaAndAtivoTrue(idReuniao, idVersao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cópia assinada não encontrada."));
        StorageObject objeto = storage.baixar(copia.getObjectKey());
        return new AtaGovernancaDto.Download(objeto.conteudo(), copia.getNomeArquivo(),
                copia.getContentType(), copia.getTamanhoBytes());
    }

    public AtaGovernancaDto.ArquivoResponse gerarDocx(UUID idReuniao, UUID idVersao, UUID ator) {
        DadosRenderizacao dados = carregarDadosRenderizacao(idReuniao, idVersao);
        var existente = arquivoGeradoRepository
                .findByIdVersaoAtaAndIdModeloAtaAndRevisaoModeloAtaAndAtivoTrue(
                        idVersao, dados.versao().getIdModeloAta(), dados.versao().getRevisaoModeloAta());
        if (existente.isPresent()) return arquivo(existente.get());

        StorageObject base = storage.baixar(dados.arquivoModelo().getObjectKey());
        byte[] docx;
        try (InputStream entrada = base.conteudo()) {
            docx = renderizador.renderizar(entrada, dados.dadosAta());
        } catch (java.io.IOException e) {
            throw new com.nexo.app.shared.domain.exception.ArmazenamentoException(
                    "Não foi possível fechar a base DOCX.", e);
        }
        String hash = sha256(docx);
        String nome = "ata-" + idReuniao + "-v" + dados.versao().getNumero() + ".docx";
        String chave = "sst/governanca/reunioes/" + idReuniao + "/geradas/" + hash + ".docx";
        storage.enviar(chave, new ByteArrayInputStream(docx), docx.length, ArquivoModeloAtaSst.DOCX);
        try {
            ArquivoAtaGeradaSst gerado = arquivoGeradoRepository.save(new ArquivoAtaGeradaSst(
                    idReuniao, idVersao, dados.versao().getIdModeloAta(),
                    dados.versao().getRevisaoModeloAta(), dados.arquivoModelo().getSha256(), nome,
                    docx.length, chave, hash, ator));
            return arquivo(gerado);
        } catch (RuntimeException e) {
            storage.remover(chave);
            throw e;
        }
    }

    public AtaGovernancaDto.Download baixarDocx(UUID idReuniao, UUID idArquivo) {
        buscar(idReuniao);
        ArquivoAtaGeradaSst arquivo = arquivoGeradoRepository
                .findByIdAndIdReuniaoAndAtivoTrue(idArquivo, idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("DOCX da ata não encontrado."));
        StorageObject objeto = storage.baixar(arquivo.getObjectKey());
        return new AtaGovernancaDto.Download(objeto.conteudo(), arquivo.getNomeArquivo(),
                arquivo.getContentType(), arquivo.getTamanhoBytes());
    }

    public void finalizar(UUID idReuniao, UUID ator) {
        transacao().executeWithoutResult(status -> {
            ReuniaoGovernancaSst reuniao = buscarParaAtualizar(idReuniao);
            boolean assinada = reuniao.getIdVersaoAtaAprovada() != null
                    && copiaRepository.existsByIdReuniaoAndIdVersaoAtaAndAtivoTrue(
                    idReuniao, reuniao.getIdVersaoAtaAprovada());
            reuniao.finalizar(assinada, ator);
            reuniaoRepository.save(reuniao);
        });
    }

    public void reabrir(UUID idReuniao, String motivo, UUID ator) {
        transacao().executeWithoutResult(status -> {
            ReuniaoGovernancaSst reuniao = buscarParaAtualizar(idReuniao);
            reuniao.reabrir(motivo, ator);
            reuniaoRepository.save(reuniao);
        });
    }

    private InicioGeracao prepararGeracao(UUID idReuniao, AtaGovernancaDto.GerarRequest request, UUID ator) {
        ReuniaoGovernancaSst reuniao = buscarParaAtualizar(idReuniao);
        RevisaoTranscricaoSst transcricao = transcricaoRepository
                .findFirstByIdReuniaoAndConfirmadaEmIsNotNullAndAtivoTrueOrderByNumeroDesc(idReuniao)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "Confirme a transcrição antes de solicitar a composição assistida."));
        NormalizacaoTranscricaoSst normalizacao = normalizacaoRepository
                .findFirstByIdReuniaoAndConfirmadaEmIsNotNullAndAtivoTrueOrderByNumeroDesc(idReuniao)
                .orElse(null);
        VersaoAtaSst base = request.idVersaoBase() == null ? null : buscarVersao(idReuniao, request.idVersaoBase());
        String hash = ArquivoPrivadoReuniao.sha256(transcricao.getHashConteudo() + "|"
                + (normalizacao == null ? "SEM_NORMALIZACAO" : normalizacao.getSha256Conteudo()) + "|"
                + reuniao.getSnapshotModeloAta() + "|" + (base == null ? "" : base.getSha256Conteudo()) + "|"
                + (request.instrucaoCorrecao() == null ? "" : request.instrucaoCorrecao()));
        var anterior = tentativaRepository.findByIdReuniaoAndClientRequestId(idReuniao, request.clientRequestId());
        if (anterior.isPresent()) {
            if (!anterior.get().mesmosInsumos(hash)) {
                throw new ConflitoGovernancaSstException("clientRequestId já foi usado com outros insumos.");
            }
            return new InicioGeracao(anterior.get(), null, null, null, true);
        }
        TentativaGeracaoAtaSst tentativa = tentativaRepository.saveAndFlush(new TentativaGeracaoAtaSst(
                idReuniao, request.clientRequestId(), hash,
                base == null ? TipoTentativaGeracaoAtaSst.GERACAO : TipoTentativaGeracaoAtaSst.CORRECAO,
                base == null ? null : base.getId(), transcricao.getId(), ator));
        String insumo = transcricao.getConteudo();
        if (normalizacao != null) insumo += "\n\nNORMALIZAÇÃO CONFIRMADA:\n" + normalizacao.getConteudoEstruturado();
        List<ParticipacaoReuniaoSst> participantes = participacaoRepository
                .findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(idReuniao);
        var entrada = new GeracaoAtaGateway.Entrada(PROMPT, reuniao.getTipoCorpo().name(),
                reuniao.getTipo().name(), reuniao.getDataHora(), reuniao.getLocalOuMeio(), reuniao.getPauta(),
                participantes.stream().map(p -> new GeracaoAtaGateway.Participante(
                        p.getNomeSnapshot(), p.getPapelSnapshot(), p.getPresenca().name())).toList(),
                insumo, reuniao.getSnapshotModeloAta(), base == null ? null : base.getConteudo(),
                request.instrucaoCorrecao());
        return new InicioGeracao(tentativa, reuniao, entrada, base, false);
    }

    private TentativaGeracaoAtaSst concluirGeracao(InicioGeracao inicio, GeracaoAtaGateway.Resultado resultado) {
        TentativaGeracaoAtaSst tentativa = tentativaRepository.findById(inicio.tentativa().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tentativa de composição não encontrada."));
        if (!resultado.sucesso()) {
            tentativa.falhar(resultado.codigoFalha());
            return tentativaRepository.save(tentativa);
        }
        ReuniaoGovernancaSst reuniao = buscarParaAtualizar(tentativa.getIdReuniao());
        VersaoAtaSst versao = novaVersao(reuniao, OrigemVersaoAtaSst.IA, resultado.conteudo(),
                inicio.base(), tentativa.getIdRevisaoTranscricao(),
                inicio.entrada().instrucaoCorrecao(), PROMPT, tentativa.getIdSolicitante());
        versaoRepository.save(versao);
        reuniaoRepository.save(reuniao);
        tentativa.concluir(versao.getId());
        return tentativaRepository.save(tentativa);
    }

    private VersaoAtaSst novaVersao(ReuniaoGovernancaSst reuniao, OrigemVersaoAtaSst origem,
                                     String conteudo, VersaoAtaSst base, UUID idTranscricao,
                                     String instrucao, String versaoPrompt, UUID ator) {
        return new VersaoAtaSst(reuniao.getId(), reuniao.reservarNumeroVersaoAta(), origem,
                conteudo, ArquivoPrivadoReuniao.sha256(conteudo.trim()),
                base == null ? null : base.getId(), idTranscricao, reuniao.getIdModeloAta(),
                reuniao.getRevisaoModeloAta(), reuniao.getSnapshotModeloAta(), instrucao, versaoPrompt, ator);
    }

    private DadosRenderizacao carregarDadosRenderizacao(UUID idReuniao, UUID idVersao) {
        ReuniaoGovernancaSst reuniao = buscar(idReuniao);
        VersaoAtaSst versao = buscarVersao(idReuniao, idVersao);
        if (!versao.aprovada()) throw new InvarianteVioladaException("Aprove a versão antes de gerar o DOCX.");
        var modeloAta = modeloRepository.findByIdAndAtivoTrue(reuniao.getIdModeloAta())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Modelo da ata não encontrado."));
        ArquivoModeloAtaSst modelo = modeloAta.getIdArquivoDocx() == null
                ? arquivoModeloRepository.findFirstByIdModeloAndAtivoTrueOrderByDataCriacaoDesc(reuniao.getIdModeloAta())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "A base DOCX publicada do modelo não foi encontrada."))
                : arquivoModeloRepository.findByIdAndIdModeloAndAtivoTrue(
                        modeloAta.getIdArquivoDocx(), reuniao.getIdModeloAta())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "A revisão DOCX vinculada ao modelo não foi encontrada."));
        List<RenderizacaoAtaDocxService.ParticipanteAta> participantes = participacaoRepository
                .findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(idReuniao).stream()
                .map(p -> new RenderizacaoAtaDocxService.ParticipanteAta(p.getNomeSnapshot(), p.getPapelSnapshot(),
                        p.getPresenca() == StatusPresencaReuniaoSst.PRESENTE,
                        p.getPresenca() == StatusPresencaReuniaoSst.AUSENTE_JUSTIFICADA,
                        p.getJustificativaAusencia())).toList();
        OffsetDateTime fim = reuniao.getDataHora().plusMinutes(reuniao.getDuracaoMinutos());
        var dados = new RenderizacaoAtaDocxService.DadosAta(reuniao.getTipoCorpo(),
                String.valueOf(versao.getNumero()), reuniao.getTipo().name(), reuniao.getIdCorpo().toString(),
                reuniao.getDataHora(), fim, versao.getConteudo(), participantes);
        return new DadosRenderizacao(reuniao, versao, modelo, dados);
    }

    private ReuniaoGovernancaSst buscar(UUID id) {
        return reuniaoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
    }
    private ReuniaoGovernancaSst buscarParaAtualizar(UUID id) {
        return reuniaoRepository.findAtivaByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
    }
    private VersaoAtaSst buscarVersao(UUID idReuniao, UUID idVersao) {
        return versaoRepository.findByIdAndIdReuniaoAndAtivoTrue(idVersao, idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão da ata não encontrada."));
    }

    private AtaGovernancaDto.TentativaResponse respostaTentativa(TentativaGeracaoAtaSst t) {
        VersaoAtaSst v = t.getIdVersaoGerada() == null ? null : versaoRepository.findById(t.getIdVersaoGerada()).orElse(null);
        return new AtaGovernancaDto.TentativaResponse(t.getId(), t.getClientRequestId(), t.getStatus(),
                t.getIdVersaoGerada(), t.getCodigoFalha(), v == null ? null : toResponse(v));
    }
    private AtaGovernancaDto.VersaoResponse toResponse(VersaoAtaSst v) {
        return new AtaGovernancaDto.VersaoResponse(v.getId(), v.getNumero(), v.getOrigem(), v.getStatus(),
                v.getConteudo(), v.getSha256Conteudo(), v.getIdVersaoBase(), v.getIdRevisaoTranscricao(),
                v.getIdModeloAta(), v.getRevisaoModeloAta(), v.getSnapshotModeloAta(), v.getInstrucaoCorrecao(),
                v.getVersaoPrompt(), v.getIdAutorOuSolicitante(), v.getAprovadaEm(),
                v.getIdUsuarioAprovacao(), v.getDataCriacao());
    }
    private AtaGovernancaDto.EvidenciaFotoResponse toResponse(EvidenciaFotograficaReuniaoSst e) {
        return new AtaGovernancaDto.EvidenciaFotoResponse(e.getId(), e.getNomeArquivo(), e.getContentType(),
                e.getTamanhoBytes(), e.getSha256(), e.getLegenda(), e.getIdAutor(), e.getEnviadaEm());
    }
    private AtaGovernancaDto.ArquivoResponse arquivo(CopiaAssinadaAtaSst a) {
        return new AtaGovernancaDto.ArquivoResponse(a.getId(), a.getIdVersaoAta(), a.getNomeArquivo(),
                a.getContentType(), a.getTamanhoBytes(), a.getSha256(), a.getEnviadaEm());
    }
    private AtaGovernancaDto.ArquivoResponse arquivo(ArquivoAtaGeradaSst a) {
        return new AtaGovernancaDto.ArquivoResponse(a.getId(), a.getIdVersaoAta(), a.getNomeArquivo(),
                a.getContentType(), a.getTamanhoBytes(), a.getSha256(), a.getGeradoEm());
    }
    private TransactionTemplate transacao() { return new TransactionTemplate(transactionManager); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponível.", e); }
    }

    private record InicioGeracao(TentativaGeracaoAtaSst tentativa, ReuniaoGovernancaSst reuniao,
                                 GeracaoAtaGateway.Entrada entrada, VersaoAtaSst base, boolean existente) {}
    private record DadosRenderizacao(ReuniaoGovernancaSst reuniao, VersaoAtaSst versao,
                                     ArquivoModeloAtaSst arquivoModelo,
                                     RenderizacaoAtaDocxService.DadosAta dadosAta) {}
}
