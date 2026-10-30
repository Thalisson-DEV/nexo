package com.nexo.app.modules.iam.infrastructure.web;

import com.nexo.app.modules.iam.application.dto.AlterarSenhaRequest;
import com.nexo.app.modules.iam.application.dto.LoginRequest;
import com.nexo.app.modules.iam.application.dto.TokenResponse;
import com.nexo.app.modules.iam.application.usecases.AlterarSenhaUseCase;
import com.nexo.app.modules.iam.application.usecases.AutenticarUseCase;
import com.nexo.app.modules.iam.application.usecases.AutenticarUseCase.Credenciais;
import com.nexo.app.modules.iam.application.usecases.SessaoAutenticacaoUseCases;
import com.nexo.app.modules.iam.doc.AuthControllerDoc;
import com.nexo.app.shared.domain.exception.SessaoInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDoc {

    /**
     * Cookie do refresh token. HttpOnly para o JavaScript nunca o enxergar (XSS não o
     * rouba), SameSite=Strict e restrito ao caminho /v1/auth: só viaja para renovar e sair.
     */
    static final String COOKIE_SESSAO = "ces_sessao";

    private final AutenticarUseCase autenticarUseCase;
    private final SessaoAutenticacaoUseCases sessoes;
    private final AlterarSenhaUseCase alterarSenhaUseCase;

    @Value("${ces.security.sessao.cookie-seguro:true}")
    private boolean cookieSeguro;

    @Override
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request, HttpServletRequest http) {
        return comCookie(autenticarUseCase.login(request, http.getHeader(HttpHeaders.USER_AGENT)), http);
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> renovar(
            @CookieValue(name = COOKIE_SESSAO, required = false) String refreshToken,
            HttpServletRequest http) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SessaoInvalidaException("Nenhuma sessão neste aparelho. Entre novamente.");
        }
        return comCookie(autenticarUseCase.renovar(refreshToken), http);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = COOKIE_SESSAO, required = false) String refreshToken,
            HttpServletRequest http) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessoes.encerrar(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, http).toString())
                .build();
    }

    @Override
    @PostMapping("/alterar-senha")
    public ResponseEntity<Void> alterarSenha(@RequestBody @Valid AlterarSenhaRequest request) {
        alterarSenhaUseCase.executar(request);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> comCookie(Credenciais credenciais, HttpServletRequest http) {
        var validade = Duration.between(OffsetDateTime.now(), credenciais.sessaoExpiraEm());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(credenciais.refreshToken(), validade, http).toString())
                .body(new TokenResponse(credenciais.tokenAcesso(), credenciais.sessaoExpiraEm()));
    }

    private ResponseCookie cookie(String valor, Duration validade, HttpServletRequest http) {
        return ResponseCookie.from(COOKIE_SESSAO, valor)
                .httpOnly(true)
                .secure(cookieSeguro)
                .sameSite("Strict")
                .path(http.getContextPath() + "/v1/auth")
                .maxAge(validade)
                .build();
    }
}
