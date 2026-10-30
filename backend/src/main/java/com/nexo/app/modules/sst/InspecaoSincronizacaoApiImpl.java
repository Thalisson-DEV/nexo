package com.nexo.app.modules.sst;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.sincronizacao.domain.AnexoSincronizacao;
import com.nexo.app.modules.sst.application.dto.EvidenciaInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaResponse;
import com.nexo.app.modules.sst.application.dto.RegistrarInspecaoRequest;
import com.nexo.app.modules.sst.application.usecases.EvidenciaInspecaoUseCases;
import com.nexo.app.modules.sst.application.usecases.AnexoDesvioUseCases;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.modules.sst.application.usecases.InspecaoSegurancaUseCases;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador do escrow de sincronização para SST.
 *
 * <p>O payload aceito é exatamente o snapshot serializado de
 * {@link RegistrarInspecaoRequest}. Os anexos precisam ser fotos e são enviados primeiro
 * pelo caso de uso de evidências; seus IDs remotos substituem os IDs locais no campo
 * {@code evidencias}. Para uma operação ANEXO o payload deve conter {@code idInspecao}.
 * Este adaptador não implementa merge ou correção de inspeção já registrada.</p>
 */
@Service
@RequiredArgsConstructor
public class InspecaoSincronizacaoApiImpl implements InspecaoSincronizacaoApi {
    private final ObjectMapper objectMapper;
    private final InspecaoSegurancaUseCases inspecaoUseCases;
    private final EvidenciaInspecaoUseCases evidenciaUseCases;
    private final AnexoDesvioUseCases anexoDesvioUseCases;

    @Override
    @Transactional
    public InspecaoSegurancaResponse registrar(Map<String, Object> payload,
                                               List<AnexoSincronizacao> anexos,
                                               UUID idInspetor) {
        ObjectNode snapshot = objectMapper.convertValue(payload, ObjectNode.class);
        ArrayNode evidencias = objectMapper.createArrayNode();
        for (AnexoSincronizacao anexo : anexos == null ? List.<AnexoSincronizacao>of() : anexos) {
            EvidenciaInspecaoResponse enviada = evidenciaUseCases.enviar(multipart(anexo), idInspetor);
            evidencias.add(enviada.id().toString());
        }
        snapshot.set("evidencias", evidencias);
        RegistrarInspecaoRequest request = objectMapper.convertValue(snapshot, RegistrarInspecaoRequest.class);
        return inspecaoUseCases.registrar(request, idInspetor);
    }

    @Override
    @Transactional
    public AnexoDesvioResponse anexar(UUID idInspecao,
                                     UUID idDesvio,
                                     ContextoAnexo contexto,
                                     AnexoSincronizacao anexo,
                                     UUID idUsuario) {
        if (anexo == null) {
            throw new InvarianteVioladaException("O anexo da sincronização é obrigatório.");
        }
        return anexoDesvioUseCases.anexar(idInspecao, idDesvio, contexto, multipart(anexo));
    }

    private MultipartFile multipart(AnexoSincronizacao anexo) {
        return new MultipartFile() {
            @Override public String getName() { return "arquivo"; }
            @Override public String getOriginalFilename() { return anexo.getNome(); }
            @Override public String getContentType() { return anexo.getContentType(); }
            @Override public boolean isEmpty() { return anexo.getConteudo().length == 0; }
            @Override public long getSize() { return anexo.getTamanhoBytes(); }
            @Override public byte[] getBytes() { return anexo.getConteudo(); }
            @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(anexo.getConteudo()); }
            @Override public void transferTo(java.io.File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), anexo.getConteudo()); }
        };
    }
}
