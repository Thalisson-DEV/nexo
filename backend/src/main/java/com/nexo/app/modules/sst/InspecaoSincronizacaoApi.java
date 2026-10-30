package com.nexo.app.modules.sst;

import com.nexo.app.modules.sincronizacao.domain.AnexoSincronizacao;
import com.nexo.app.modules.sst.application.dto.EvidenciaInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porta pública para reproduzir snapshots duráveis do app de campo no domínio SST.
 * A implementação mantém a validação e as invariantes dos casos de uso normais.
 */
public interface InspecaoSincronizacaoApi {
    InspecaoSegurancaResponse registrar(Map<String, Object> payload,
                                        List<AnexoSincronizacao> anexos,
                                        UUID idInspetor);

    AnexoDesvioResponse anexar(UUID idInspecao,
                               UUID idDesvio,
                               ContextoAnexo contexto,
                               AnexoSincronizacao anexo,
                               UUID idUsuario);
}
