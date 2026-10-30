/**
 * Marcações de pessoas em textos ({@code @LOGIN}).
 *
 * <p>Módulo próprio, e não parte de chamados nem de notificações, por duas razões.
 * Marcar alguém é um fato de domínio — "fulano chamou beltrano para este assunto" —
 * que existe independentemente de virar aviso; e é um fato que não pertence a nenhum
 * módulo de negócio em particular, já que a mesma mecânica serve a chamados hoje e a
 * desvios ou solicitações amanhã.</p>
 *
 * <p>A dependência aponta para fora: quem tem texto com marcação chama a
 * {@code MencaoApi}. Este módulo não conhece chamado algum — recebe um
 * {@code ContextoMencao} genérico e publica um evento. Quem transforma isso em
 * notificação é o módulo de notificação, pelas regras configuradas, sem que exista
 * ligação direta entre os três.</p>
 */
@ApplicationModule(displayName = "mencao")
package com.nexo.app.modules.mencao;

import org.springframework.modulith.ApplicationModule;
