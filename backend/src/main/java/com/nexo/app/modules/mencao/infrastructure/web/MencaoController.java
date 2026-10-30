package com.nexo.app.modules.mencao.infrastructure.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.mencao.MencaoApi;
import com.nexo.app.modules.mencao.UsuarioMencionavel;
import com.nexo.app.modules.mencao.doc.MencaoControllerDoc;
import com.nexo.app.modules.mencao.domain.OrigemMencao;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/mencoes")
@RequiredArgsConstructor
public class MencaoController implements MencaoControllerDoc {

    private final MencaoApi mencaoApi;

    /**
     * {@code isAuthenticated()} e não uma permissão dedicada: marcar é parte de
     * escrever, e quem já pode escrever em algum lugar do sistema tem de poder
     * completar um login. A permissão que importa é a de ler o texto, e ela é checada
     * no registro da marcação, onde há contexto para isso.
     */
    @Override
    @GetMapping("/usuarios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UsuarioMencionavel>> sugerir(
            @RequestParam String termo,
            @RequestParam(required = false) OrigemMencao origem,
            @RequestParam(defaultValue = "false") boolean visibilidadeRestrita) {
        return ResponseEntity.ok(mencaoApi.sugerir(termo, origem, visibilidadeRestrita));
    }
}
