package com.nexo.app.modules.notificacao.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.notificacao.application.dto.PreferenciaNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.PreferenciasNotificacaoRequest;
import com.nexo.app.modules.notificacao.application.usecases.PreferenciaNotificacaoUseCases;
import com.nexo.app.modules.notificacao.doc.PreferenciaNotificacaoControllerDoc;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/notificacoes/preferencias")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PreferenciaNotificacaoController implements PreferenciaNotificacaoControllerDoc {

    private final PreferenciaNotificacaoUseCases useCases;

    @Override
    @GetMapping
    public ResponseEntity<List<PreferenciaNotificacaoResponse>> listar() {
        return ResponseEntity.ok(useCases.listar(usuarioAtual()));
    }

    @Override
    @PutMapping
    public ResponseEntity<List<PreferenciaNotificacaoResponse>> salvar(
            @Valid @RequestBody PreferenciasNotificacaoRequest request) {
        return ResponseEntity.ok(useCases.salvar(usuarioAtual(), request));
    }

    /**
     * O use case recebe o usuário por parâmetro porque a chave do cache depende dele;
     * resolvê-lo aqui é adaptação de contexto HTTP, não regra de negócio.
     */
    private UUID usuarioAtual() {
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        }
        return idUsuario;
    }
}
