package com.nexo.app.modules.mencao;

import java.util.UUID;

import com.nexo.app.modules.mencao.domain.OrigemMencao;

/**
 * Tudo o que o módulo de marcações precisa saber sobre o texto que está recebendo, sem
 * saber nada sobre o módulo que o produziu.
 *
 * @param origem              de onde vem o texto; define também qual permissão delimita
 *                            a visibilidade restrita
 * @param idOrigem            id do texto em si (a interação, o comentário) — é ele que
 *                            distingue duas marcações no mesmo registro
 * @param idRegistro          id do agregado a que o texto pertence (o chamado)
 * @param rotuloRegistro      como o registro é chamado por gente ("chamado CH-2026… –
 *                            Impressora não liga"), para caber no texto do aviso
 * @param link                caminho no frontend que abre o registro; viaja com a
 *                            marcação porque cada origem tem o seu, e a regra de
 *                            notificação usa {@code {{link}}} em vez de um caminho fixo
 * @param idAutor             quem escreveu o texto; nunca é marcado por si mesmo
 * @param visibilidadeRestrita indica que o texto não circula para todos (comentário
 *                            interno), o que restringe quem pode ser marcado nele
 */
public record ContextoMencao(
        OrigemMencao origem,
        UUID idOrigem,
        UUID idRegistro,
        String rotuloRegistro,
        String link,
        UUID idAutor,
        boolean visibilidadeRestrita
) {
}
