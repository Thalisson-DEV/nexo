package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.ArquivoDocumentoFrota;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaRequest;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaResponse;
import com.nexo.app.modules.frota.domain.DocumentoFrota;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.Implemento;
import com.nexo.app.modules.frota.domain.SituacaoVencimento;
import com.nexo.app.modules.frota.infrastructure.persistence.DocumentoFrotaRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.DocumentoFrotaSpecification;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.ImplementoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Documentos de veículos e implementos, com arquivo no armazenamento de objetos. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentoFrotaUseCases {

    private static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final DocumentoFrotaRepository repository;
    private final FichaVeiculoRepository fichaRepository;
    private final ImplementoRepository implementoRepository;
    private final ParametrosFrotaUseCases parametros;
    private final ObjectStorageService objectStorage;

    @Transactional(readOnly = true)
    public PageResponse<DocumentoFrotaResponse> listar(DocumentoFrotaFilter filtro, Pageable pageable) {
        List<UUID> veiculosEscopo = null;
        List<UUID> implementosEscopo = null;
        if (filtro != null && !filtro.idsBase().isEmpty()) {
            veiculosEscopo = fichaRepository.findAllByIdBaseOperacionalIn(filtro.idsBase()).stream()
                    .map(FichaVeiculo::getIdVeiculo).toList();
            implementosEscopo = implementoRepository.findAllByIdBaseOperacionalIn(filtro.idsBase()).stream()
                    .map(Implemento::getId).toList();
        }
        LocalDate hoje = LocalDate.now();
        int diasAviso = parametros.diasAviso();
        Page<DocumentoFrota> pagina = repository.findAll(
                DocumentoFrotaSpecification.of(filtro, veiculosEscopo, implementosEscopo, hoje, diasAviso), pageable);
        return PageResponse.fromPage(new PageImpl<>(montar(pagina.getContent(), hoje, diasAviso),
                pagina.getPageable(), pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public DocumentoFrotaResponse buscarPorId(UUID id) {
        return montar(List.of(buscarEntidade(id)), LocalDate.now(), parametros.diasAviso()).get(0);
    }

    /** Cria o documento e, se veio arquivo, já o anexa. */
    @Transactional
    public DocumentoFrotaResponse criar(DocumentoFrotaRequest request, MultipartFile arquivo) {
        var dados = dados(request);
        DocumentoFrota documento;
        if (request.idVeiculo() != null && request.idImplemento() != null) {
            throw new InvarianteVioladaException("Um documento pertence a um veículo ou a um implemento, não aos dois.");
        } else if (request.idVeiculo() != null) {
            var ficha = fichaRepository.findByIdVeiculo(request.idVeiculo())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado na frota."));
            documento = DocumentoFrota.doVeiculo(ficha.getIdVeiculo(), dados);
        } else if (request.idImplemento() != null) {
            var implemento = implementoRepository.findById(request.idImplemento())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Implemento não encontrado."));
            documento = DocumentoFrota.doImplemento(implemento.getId(), dados);
        } else {
            throw new InvarianteVioladaException("Informe o veículo ou o implemento do documento.");
        }
        DocumentoFrota salvo = repository.save(documento);
        if (arquivo != null && !arquivo.isEmpty()) {
            enviarArquivo(salvo, arquivo);
        }
        return montar(List.of(salvo), LocalDate.now(), parametros.diasAviso()).get(0);
    }

    @Transactional
    public DocumentoFrotaResponse atualizar(UUID id, DocumentoFrotaRequest request) {
        DocumentoFrota documento = buscarEntidade(id);
        documento.atualizar(dados(request));
        return montar(List.of(repository.save(documento)), LocalDate.now(), parametros.diasAviso()).get(0);
    }

    @Transactional
    public DocumentoFrotaResponse anexar(UUID id, MultipartFile arquivo) {
        DocumentoFrota documento = buscarEntidade(id);
        if (!documento.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível anexar a um documento excluído.");
        }
        enviarArquivo(documento, arquivo);
        return montar(List.of(documento), LocalDate.now(), parametros.diasAviso()).get(0);
    }

    @Transactional(readOnly = true)
    public ArquivoDocumentoFrota baixar(UUID id) {
        DocumentoFrota documento = buscarEntidade(id);
        if (!documento.possuiArquivo()) {
            throw new RecursoNaoEncontradoException("Este documento não tem arquivo anexado.");
        }
        var objeto = objectStorage.baixar(documento.getArquivoChave());
        return new ArquivoDocumentoFrota(documento.getArquivoNome(), documento.getArquivoContentType(),
                documento.getArquivoTamanhoBytes(), objeto.conteudo());
    }

    /** Exclusão lógica: o documento sai das listas e dos avisos, o arquivo fica para auditoria. */
    @Transactional
    public void excluir(UUID id) {
        DocumentoFrota documento = buscarEntidade(id);
        documento.desativar();
        repository.save(documento);
    }

    private void enviarArquivo(DocumentoFrota documento, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }
        String contentType = resolverContentType(arquivo);
        String chave = "frota/documentos/" + documento.getId() + "/" + UUID.randomUUID()
                + "." + TIPOS_PERMITIDOS.get(contentType);
        String sha256;
        try (InputStream entrada = arquivo.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream comHash = new DigestInputStream(entrada, digest)) {
                objectStorage.enviar(chave, comHash, arquivo.getSize(), contentType);
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
        try {
            documento.anexarArquivo(chave, nomeSeguro(arquivo, contentType), contentType, arquivo.getSize(), sha256);
            repository.saveAndFlush(documento);
        } catch (RuntimeException e) {
            removerSilenciosamente(chave);
            throw e;
        }
    }

    private DocumentoFrota buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
    }

    private static DocumentoFrota.DadosDocumento dados(DocumentoFrotaRequest request) {
        return new DocumentoFrota.DadosDocumento(request.tipo(), request.descricao(), request.numero(),
                request.dataEmissao(), request.dataVencimento(), request.observacao());
    }

    private List<DocumentoFrotaResponse> montar(List<DocumentoFrota> documentos, LocalDate hoje, int diasAviso) {
        var idsVeiculo = documentos.stream().map(DocumentoFrota::getIdVeiculo).filter(java.util.Objects::nonNull).toList();
        var idsImplemento = documentos.stream().map(DocumentoFrota::getIdImplemento).filter(java.util.Objects::nonNull).toList();
        Map<UUID, String> placas = idsVeiculo.isEmpty() ? Map.of()
                : fichaRepository.findAllByIdVeiculoIn(idsVeiculo).stream()
                        .collect(Collectors.toMap(FichaVeiculo::getIdVeiculo, FichaVeiculo::getPlaca));
        Map<UUID, String> implementos = idsImplemento.isEmpty() ? Map.of()
                : implementoRepository.findAllById(idsImplemento).stream()
                        .collect(Collectors.toMap(Implemento::getId, Implemento::getIdentificacao));
        return documentos.stream().map(d -> new DocumentoFrotaResponse(
                d.getId(),
                d.getIdVeiculo(),
                d.getIdVeiculo() == null ? null : placas.get(d.getIdVeiculo()),
                d.getIdImplemento(),
                d.getIdImplemento() == null ? null : implementos.get(d.getIdImplemento()),
                d.getTipo(),
                d.getTipo().getRotulo(),
                d.getDescricao(),
                d.getNumero(),
                d.getDataEmissao(),
                d.getDataVencimento(),
                SituacaoVencimento.calcular(d.getDataVencimento(), hoje, diasAviso),
                d.diasParaVencer(hoje),
                d.getObservacao(),
                d.possuiArquivo(),
                d.getArquivoNome(),
                d.getArquivoContentType(),
                d.getArquivoTamanhoBytes()
        )).toList();
    }

    private static String resolverContentType(MultipartFile arquivo) {
        String informado = arquivo.getContentType() == null ? null
                : arquivo.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (informado != null && TIPOS_PERMITIDOS.containsKey(informado)) {
            return informado;
        }
        String extensao = StringUtils.getFilenameExtension(arquivo.getOriginalFilename());
        if (extensao != null) {
            String ext = extensao.toLowerCase(Locale.ROOT);
            if (ext.equals("jpeg")) ext = "jpg";
            for (var entrada : TIPOS_PERMITIDOS.entrySet()) {
                if (entrada.getValue().equals(ext)) return entrada.getKey();
            }
        }
        throw new InvarianteVioladaException("Tipo de arquivo não suportado. Envie PDF ou imagem (JPG, PNG, WEBP).");
    }

    private static String nomeSeguro(MultipartFile arquivo, String contentType) {
        String original = arquivo.getOriginalFilename();
        String nome = StringUtils.hasText(original) ? StringUtils.getFilename(StringUtils.cleanPath(original)) : null;
        if (!StringUtils.hasText(nome)) {
            nome = "documento." + TIPOS_PERMITIDOS.get(contentType);
        }
        return nome.length() <= 255 ? nome : nome.substring(nome.length() - 255);
    }

    private void removerSilenciosamente(String chave) {
        try {
            objectStorage.remover(chave);
        } catch (RuntimeException e) {
            log.warn("Não foi possível remover o objeto órfão {}: {}", chave, e.getMessage());
        }
    }
}
