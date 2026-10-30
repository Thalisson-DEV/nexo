package com.nexo.app.modules.iam.doc;

import com.nexo.app.modules.iam.application.dto.AlterarSenhaRequest;
import com.nexo.app.modules.iam.application.dto.LoginRequest;
import com.nexo.app.modules.iam.application.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Autenticação", description = "Login, renovação e encerramento da sessão do aparelho")
public interface AuthControllerDoc {

    @Operation(summary = "Entrar",
            description = "Abre uma sessão para o aparelho. Devolve o token de acesso no corpo e o "
                    + "refresh token no cookie HttpOnly `ces_sessao`.")
    @ApiResponse(responseCode = "200", description = "Sessão aberta")
    @ApiResponse(responseCode = "403", description = "Credenciais inválidas")
    ResponseEntity<TokenResponse> login(LoginRequest request, HttpServletRequest http);

    @Operation(summary = "Renovar o token de acesso",
            description = "Gira o refresh token do cookie e emite um novo token de acesso. Não exige "
                    + "o token de acesso, que pode já ter vencido.")
    @ApiResponse(responseCode = "200", description = "Token renovado")
    @ApiResponse(responseCode = "401", description = "Sessão expirada, revogada ou desconhecida (code SESSAO_INVALIDA)")
    ResponseEntity<TokenResponse> renovar(@Parameter(hidden = true) String refreshToken, HttpServletRequest http);

    @Operation(summary = "Sair", description = "Encerra a sessão do aparelho e apaga o cookie.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada")
    ResponseEntity<Void> logout(@Parameter(hidden = true) String refreshToken, HttpServletRequest http);

    @Operation(summary = "Alterar a própria senha",
            description = "Encerra as sessões dos outros aparelhos do usuário; a do aparelho atual continua.")
    ResponseEntity<Void> alterarSenha(AlterarSenhaRequest request);
}
