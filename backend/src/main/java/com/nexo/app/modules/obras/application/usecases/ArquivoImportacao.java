package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.CarteiraImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.KitImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.OrcamentoImportacaoLinha;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Utilitários comuns às importações de planilha de Obras.
 */
final class ArquivoImportacao {

    private ArquivoImportacao() {
    }

    static byte[] bytes(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Selecione uma planilha preenchida.");
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    static boolean comErro(Object registro) {
        ImportacaoStatus status = switch (registro) {
            case KitImportacaoLinha k -> k.status();
            case CarteiraImportacaoLinha c -> c.status();
            case OrcamentoImportacaoLinha o -> o.status();
            default -> null;
        };
        return status == ImportacaoStatus.COM_ERRO;
    }
}
