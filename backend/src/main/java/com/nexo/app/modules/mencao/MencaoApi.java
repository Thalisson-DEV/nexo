package com.nexo.app.modules.mencao;

import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.mencao.domain.OrigemMencao;

/**
 * API pública do módulo de marcações, para quem tem texto onde se pode escrever
 * {@code @LOGIN}.
 *
 * <p>Quem chama não decide nada sobre notificação: entrega o texto e o contexto, e o
 * módulo cuida de reconhecer as marcações, verificar quem pode ser marcado ali,
 * registrar o fato e anunciá-lo. Se ninguém for marcado, nada acontece — o custo de
 * chamar em toda mensagem é uma expressão regular sobre o texto.</p>
 */
public interface MencaoApi {

    /**
     * Reconhece as marcações escritas no texto, registra-as e anuncia o fato.
     *
     * <p>Um {@code @} que não corresponde a nenhum usuário ativo é ignorado em
     * silêncio: erro de digitação não pode impedir a mensagem de ser enviada. Já
     * marcar alguém que não pode ler o texto <b>é</b> recusado, com o login na
     * mensagem de erro — nesse caso quem escreve precisa saber, porque a alternativa
     * seria a pessoa receber um aviso sobre algo que não consegue abrir.</p>
     *
     * @return ids de quem foi marcado, na ordem em que aparecem no texto
     */
    List<UUID> registrar(ContextoMencao contexto, String texto);

    /**
     * Usuários que podem ser marcados, filtrados pelo trecho já digitado.
     *
     * <p>Recebe origem e visibilidade em vez do nome de uma permissão de propósito: a
     * regra de quem pode ser marcado é do módulo, e deixar o cliente informar a
     * permissão transformaria este endpoint em uma consulta livre de "quem tem tal
     * atribuição".</p>
     */
    List<UsuarioMencionavel> sugerir(String termo, OrigemMencao origem, boolean visibilidadeRestrita);
}
