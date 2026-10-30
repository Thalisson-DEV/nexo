package com.nexo.app.modules.notificacao.infrastructure.web;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoFilter;
import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.dto.ResumoNotificacoesResponse;
import com.nexo.app.modules.notificacao.application.usecases.NotificacaoUseCases;
import com.nexo.app.modules.notificacao.doc.NotificacaoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * A caixa é pessoal, então o controle de acesso é apenas estar autenticado — exigir
 * uma permissão específica deixaria perfis inteiros sem sininho. O isolamento entre
 * usuários é feito no use case, que escopa toda consulta pelo autenticado.
 */
@RestController
@RequestMapping("/v1/notificacoes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificacaoController implements NotificacaoControllerDoc {

    private final NotificacaoUseCases useCases;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<NotificacaoResponse>> listar(
            @ParameterObject NotificacaoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/resumo")
    public ResponseEntity<ResumoNotificacoesResponse> resumo() {
        return ResponseEntity.ok(useCases.resumo());
    }

    @Override
    @PatchMapping("/{id}/leitura")
    public ResponseEntity<NotificacaoResponse> marcarLida(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.marcarLida(id));
    }

    @Override
    @DeleteMapping("/{id}/leitura")
    public ResponseEntity<NotificacaoResponse> marcarNaoLida(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.marcarNaoLida(id));
    }

    @Override
    @PatchMapping("/leitura")
    public ResponseEntity<ResumoNotificacoesResponse> marcarTodasLidas() {
        return ResponseEntity.ok(useCases.marcarTodasLidas());
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        useCases.arquivar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/lidas")
    public ResponseEntity<ResumoNotificacoesResponse> arquivarLidas() {
        return ResponseEntity.ok(useCases.arquivarLidas());
    }
}
