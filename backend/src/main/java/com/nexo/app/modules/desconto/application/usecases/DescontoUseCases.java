package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.DescontoBaixaRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoFilter;
import com.nexo.app.modules.desconto.application.dto.DescontoItemRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoResponse;
import com.nexo.app.modules.desconto.application.dto.DescontoValidacaoRequest;
import com.nexo.app.modules.desconto.application.mappers.DescontoMapper;
import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoSpecification;
import com.nexo.app.modules.desconto.application.dto.DescontoAnexoDownload;
import com.nexo.app.modules.desconto.domain.DescontoAnexo;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.DescontoBaixadoEvent;
import com.nexo.app.shared.domain.events.DescontoRejeitadoEvent;
import com.nexo.app.shared.domain.events.DescontoSolicitadoEvent;
import com.nexo.app.shared.domain.events.DescontoValidadoEvent;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DescontoUseCases {

    private static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.ofEntries(
            // Imagens
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/heic", "heic"),
            Map.entry("image/heif", "heif"),

            // Documentos
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),

            // Áudio (incluindo OGG e formatos comuns)
            Map.entry("audio/ogg", "ogg"),
            Map.entry("application/ogg", "ogg"),
            Map.entry("audio/opus", "opus"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/wave", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/aac", "aac"),
            Map.entry("audio/x-aac", "aac"),
            Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/m4a", "m4a"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/webm", "weba"),
            Map.entry("audio/flac", "flac"),
            Map.entry("audio/x-flac", "flac"),
            Map.entry("audio/x-ms-wma", "wma"),

            // Vídeo (formatos comuns)
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("video/quicktime", "mov"),
            Map.entry("video/x-msvideo", "avi"),
            Map.entry("video/msvideo", "avi"),
            Map.entry("video/avi", "avi"),
            Map.entry("video/x-matroska", "mkv"),
            Map.entry("video/mpeg", "mpeg"),
            Map.entry("video/3gpp", "3gp"),
            Map.entry("video/3gpp2", "3g2"),
            Map.entry("video/x-ms-wmv", "wmv"),
            Map.entry("video/ogg", "ogv")
    );

    private final DescontoRepository repository;
    private final DescontoMapper mapper;
    private final MotivoDescontoUseCases motivoUseCases;
    private final TipoEquipamentoUseCases equipamentoUseCases;
    private final ObjectStorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DescontoResponse solicitar(DescontoRequest request, UUID usuarioSolicitanteId) {
        var motivo = motivoUseCases.buscarEntity(request.motivoId());

        var desconto = new Desconto(
                request.colaboradorId(),
                motivo,
                request.observacoes(),
                request.quantidadeParcelas(),
                request.dataOcorrencia(),
                usuarioSolicitanteId
        );
        repository.save(desconto);

        // O módulo apenas anuncia o fato. Quem (e se alguém) será notificado é decidido
        // pelas regras configuradas para este gatilho, no módulo de notificação.
        eventPublisher.publishEvent(new DescontoSolicitadoEvent(
                desconto.getId(),
                usuarioSolicitanteId,
                motivo.getDescricao(),
                request.quantidadeParcelas(),
                request.dataOcorrencia() == null ? null : request.dataOcorrencia().toString(),
                desconto.getColaboradorId()));

        return mapper.toResponse(desconto);
    }

    @Transactional
    public DescontoResponse atualizar(UUID id, DescontoRequest request) {
        var desconto = buscarEntity(id);
        var motivo = motivoUseCases.buscarEntity(request.motivoId());
        desconto.atualizar(request.colaboradorId(), motivo, request.observacoes(), request.quantidadeParcelas(), request.dataOcorrencia());
        repository.save(desconto);
        return mapper.toResponse(desconto);
    }

    @Transactional
    public void adicionarItem(UUID id, DescontoItemRequest request) {
        var desconto = buscarEntity(id);
        var equipamento = equipamentoUseCases.buscarEntity(request.tipoEquipamentoId());
        desconto.adicionarItem(request.descricaoMaterial(), equipamento, request.valor());
        repository.save(desconto);
    }

    @Transactional
    public void atualizarItem(UUID id, UUID itemId, DescontoItemRequest request) {
        var desconto = buscarEntity(id);
        var equipamento = equipamentoUseCases.buscarEntity(request.tipoEquipamentoId());
        desconto.atualizarItem(itemId, request.descricaoMaterial(), equipamento, request.valor());
        repository.save(desconto);
    }

    @Transactional
    public void removerItem(UUID id, UUID itemId) {
        var desconto = buscarEntity(id);
        desconto.removerItem(itemId);
        repository.save(desconto);
    }

    @Transactional
    public void adicionarAnexo(UUID id, MultipartFile arquivo) {
        var desconto = buscarEntity(id);
        
        String contentType = resolverContentType(arquivo);
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }
        
        String nomeOriginal = arquivo.getOriginalFilename();
        String extensao = TIPOS_PERMITIDOS.get(contentType);
        String nomeSeguro = (nomeOriginal == null || nomeOriginal.isBlank())
                ? "arquivo." + extensao
                : StringUtils.getFilename(StringUtils.cleanPath(nomeOriginal));
        if (nomeSeguro == null || nomeSeguro.isBlank()) {
            nomeSeguro = "arquivo." + extensao;
        }
        if (nomeSeguro.length() > 255) nomeSeguro = nomeSeguro.substring(nomeSeguro.length() - 255);

        String chave = "descontos/" + id + "/" + UUID.randomUUID() + "." + extensao;
        
        try {
            storageService.enviar(chave, arquivo.getInputStream(), arquivo.getSize(), contentType);
            desconto.adicionarAnexo(nomeSeguro, chave, contentType, arquivo.getSize());
            repository.save(desconto);
        } catch (IOException e) {
            throw new ArmazenamentoException("Erro ao processar o arquivo de anexo", e);
        }
    }

    @Transactional
    public void removerAnexo(UUID id, UUID anexoId) {
        var desconto = buscarEntity(id);
        // Find the attachment to get the URL/key to delete from storage
        var anexo = desconto.getAnexos().stream()
                .filter(a -> a.getId().equals(anexoId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado."));
                
        desconto.removerAnexo(anexoId);
        repository.saveAndFlush(desconto);
        
        try {
            storageService.remover(anexo.getUrl());
        } catch (RuntimeException e) {
            log.warn("Anexo {} removido do banco, mas falhou a exclusão do objeto '{}' no R2: {}",
                    anexoId, anexo.getUrl(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public DescontoAnexoDownload baixarAnexo(UUID id, UUID anexoId) {
        var desconto = buscarEntity(id);
        var anexo = desconto.getAnexos().stream()
                .filter(a -> a.getId().equals(anexoId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado."));

        StorageObject objeto = storageService.baixar(anexo.getUrl());
        return new DescontoAnexoDownload(objeto.conteudo(), anexo.getContentType(), anexo.getTamanhoBytes(), anexo.getNomeArquivo());
    }

    @Transactional
    public void validar(UUID id, UUID usuarioValidadorId, DescontoValidacaoRequest request) {
        var desconto = buscarEntity(id);
        desconto.validar(usuarioValidadorId, request.valorValidado());
        repository.save(desconto);

        // A validação é o ponto em que o termo troca de mãos: sai de quem valida e
        // passa a depender de quem faz a baixa. Quem é essa pessoa não está escrito
        // aqui — é a regra de notificação que a endereça, por permissão.
        eventPublisher.publishEvent(new DescontoValidadoEvent(
                desconto.getId(),
                desconto.getUsuarioSolicitanteId(),
                usuarioValidadorId,
                desconto.getMotivo().getDescricao(),
                desconto.getQuantidadeParcelas(),
                desconto.getValorValidado(),
                desconto.getColaboradorId()));
    }

    @Transactional
    public void baixar(UUID id, UUID usuarioBaixaId, DescontoBaixaRequest request) {
        var desconto = buscarEntity(id);
        desconto.baixar(usuarioBaixaId, request.valorBaixado());
        repository.save(desconto);

        eventPublisher.publishEvent(new DescontoBaixadoEvent(
                desconto.getId(),
                desconto.getUsuarioSolicitanteId(),
                usuarioBaixaId,
                desconto.getMotivo().getDescricao(),
                desconto.getQuantidadeParcelas(),
                desconto.getValorBaixado(),
                desconto.getColaboradorId()));
    }

    @Transactional
    public void rejeitar(UUID id, UUID usuarioRejeicaoId, com.nexo.app.modules.desconto.application.dto.DescontoRejeicaoRequest request) {
        var desconto = buscarEntity(id);
        desconto.rejeitar(usuarioRejeicaoId, request.motivoRejeicao());
        repository.save(desconto);

        eventPublisher.publishEvent(new DescontoRejeitadoEvent(
                desconto.getId(),
                desconto.getUsuarioSolicitanteId(),
                usuarioRejeicaoId,
                desconto.getMotivo().getDescricao(),
                desconto.getMotivoRejeicao(),
                desconto.getColaboradorId()));
    }

    @Transactional(readOnly = true)
    public DescontoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DescontoResponse> listar(DescontoFilter filter, Pageable pageable) {
        Page<Desconto> page = repository.findAll(DescontoSpecification.comFiltros(filter), pageable);
        return PageResponse.fromPage(page.map(mapper::toResponse));
    }

    protected Desconto buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Desconto não encontrado."));
    }

    private String resolverContentType(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        String contentType = normalizar(arquivo.getContentType());
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType)) {
            contentType = inferirPorExtensao(arquivo.getOriginalFilename());
        }
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType)) {
            throw new InvarianteVioladaException(
                    "Tipo de arquivo não suportado. Aceitos: imagens (JPEG, PNG, WebP, HEIC), documentos (PDF, DOC, DOCX), áudios (OGG, MP3, WAV, AAC, M4A, etc.) e vídeos (MP4, WebM, MOV, AVI, MKV, etc.).");
        }
        return contentType;
    }

    private static String normalizar(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int ponto = contentType.indexOf(';');
        return (ponto >= 0 ? contentType.substring(0, ponto) : contentType).trim().toLowerCase();
    }

    private static String inferirPorExtensao(String nomeArquivo) {
        String ext = normalizar(StringUtils.getFilenameExtension(nomeArquivo));
        if (ext == null) return null;
        return switch (ext) {
            // Imagens
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";

            // Documentos
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            // Áudios
            case "ogg", "oga" -> "audio/ogg";
            case "opus" -> "audio/opus";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "aac" -> "audio/aac";
            case "m4a" -> "audio/x-m4a";
            case "weba" -> "audio/webm";
            case "flac" -> "audio/flac";
            case "wma" -> "audio/x-ms-wma";

            // Vídeos
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "mpeg", "mpg" -> "video/mpeg";
            case "3gp" -> "video/3gpp";
            case "3g2" -> "video/3gpp2";
            case "wmv" -> "video/x-ms-wmv";
            case "ogv" -> "video/ogg";
            default -> null;
        };
    }
}
