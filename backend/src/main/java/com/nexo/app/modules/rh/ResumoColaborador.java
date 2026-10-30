package com.nexo.app.modules.rh;

import java.util.UUID;

/**
 * O mínimo que outro módulo precisa para apresentar a <b>pessoa</b> por trás de um
 * registro técnico: como ela se chama e onde trabalha.
 *
 * <p>Existe para telas em que o vínculo com o colaborador é contexto, não cadastro —
 * a listagem de usuários é o caso típico: quem administra acessos reconhece "MARIA
 * SOUZA — ELETRICISTA" muito antes de reconhecer o login {@code MSOUZA1}.</p>
 *
 * <p>Deliberadamente sem CPF e sem datas: quem exibe um rótulo de contexto não precisa
 * de dado pessoal sensível, e o que não trafega não vaza. Para o cadastro completo
 * existe {@link ColaboradorResponse}.</p>
 */
public record ResumoColaborador(
        UUID id,
        String nomeCompleto,
        String funcao,
        String baseOperacional,
        boolean ativo
) {
}
