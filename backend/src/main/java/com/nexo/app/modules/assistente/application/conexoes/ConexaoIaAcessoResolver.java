package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.domain.TipoCredencialIa;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CredencialConexaoIaRepository;
import com.nexo.app.shared.infrastructure.security.AcessoExterno;
import com.nexo.app.shared.infrastructure.security.AcessoExternoResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Valida a credencial de cada chamada da IA externa: token de acesso ou chave vigente, de conexao que
 * continua conectada. Desconectar no Nexo corta o acesso na chamada seguinte.
 */
@Component
public class ConexaoIaAcessoResolver implements AcessoExternoResolver {

    private static final int MAX_IP = 64;

    private final CredencialConexaoIaRepository credenciais;
    private final ConexaoIaRepository conexoes;

    public ConexaoIaAcessoResolver(CredencialConexaoIaRepository credenciais, ConexaoIaRepository conexoes) {
        this.credenciais = credenciais;
        this.conexoes = conexoes;
    }

    @Override
    @Transactional
    public Optional<AcessoExterno> resolver(String credencial, String ip) {
        if (credencial == null || credencial.isBlank()) return Optional.empty();
        OffsetDateTime agora = OffsetDateTime.now();
        return credenciais.findByHash(CredenciaisIa.hash(credencial.strip()))
                .filter(c -> c.getTipo() == TipoCredencialIa.ACESSO || c.getTipo() == TipoCredencialIa.CHAVE)
                .filter(c -> c.isValida(agora))
                .flatMap(c -> conexoes.findByIdAndAtivoTrue(c.getConexaoId()))
                .filter(conexao -> conexao.isUtilizavel(agora))
                .map(conexao -> {
                    String origem = ip == null ? null : ip.length() <= MAX_IP ? ip : ip.substring(0, MAX_IP);
                    conexoes.registrarUso(conexao.getId(), agora, origem, agora.minusMinutes(1));
                    return new AcessoExterno(conexao.getUsuarioId(), conexao.getId(), conexao.isPermiteEscrita());
                });
    }
}
