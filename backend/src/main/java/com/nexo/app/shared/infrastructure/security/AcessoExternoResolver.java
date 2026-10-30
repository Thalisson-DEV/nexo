package com.nexo.app.shared.infrastructure.security;

import java.util.Optional;

/**
 * Resolve uma credencial de acesso externo (token OAuth ou chave de acesso) para a conexão que ela
 * representa. Vazio quando a credencial não existe, venceu ou a conexão foi desconectada.
 */
public interface AcessoExternoResolver {

    Optional<AcessoExterno> resolver(String credencial, String ip);
}
