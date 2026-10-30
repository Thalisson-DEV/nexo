package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

/** Resolve e congela uma identificacao humana para o autor do evento. */
@Service
@RequiredArgsConstructor
public class ResolvedorAutorEventoSolicitacao {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)^USUARIO\\s+[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    public String resolver(UUID idUsuario, String snapshotExistente) {
        if (idUsuario == null) {
            return textoValido(snapshotExistente) ? snapshotExistente.trim() : "SISTEMA";
        }
        return colaboradorApi.buscarNomeCompletoPorUsuario(idUsuario)
                .filter(ResolvedorAutorEventoSolicitacao::textoValido)
                .map(String::trim)
                .or(() -> usuarioApi.buscarNomePorId(idUsuario)
                        .filter(ResolvedorAutorEventoSolicitacao::textoValido)
                        .map(String::trim))
                .orElseGet(() -> snapshotUtil(snapshotExistente, idUsuario));
    }

    private static String snapshotUtil(String snapshot, UUID idUsuario) {
        if (textoValido(snapshot) && !PLACEHOLDER.matcher(snapshot.trim()).matches()) {
            return snapshot.trim();
        }
        return "USUARIO " + idUsuario;
    }

    private static boolean textoValido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
