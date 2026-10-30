package com.nexo.app.modules.notificacao.application.usecases;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.domain.SegmentoComunicado;

import lombok.RequiredArgsConstructor;

/**
 * Expande o público-alvo de um comunicado na lista concreta de usuários.
 *
 * <p>Só é usado nos dois caminhos que realmente precisam da lista: entregar no
 * sininho e montar o relatório de confirmação. A exibição do mural NÃO passa por
 * aqui — lá a pergunta é a inversa e muito mais barata ("este usuário pertence ao
 * público?"), respondida pela própria consulta do repositório.</p>
 *
 * <p>Fala com o IAM apenas pela {@link UsuarioApi}, a API pública do módulo, para
 * não criar dependência sobre o domínio dele.</p>
 */
@Component
@RequiredArgsConstructor
public class ResolvedorAudienciaComunicado {

    private final UsuarioApi usuarioApi;

    public Set<UUID> resolver(Comunicado comunicado) {
        Set<UUID> destinatarios = new LinkedHashSet<>();
        for (SegmentoComunicado segmento : comunicado.segmentosAtivos()) {
            destinatarios.addAll(expandir(segmento));
        }
        return destinatarios;
    }

    private Set<UUID> expandir(SegmentoComunicado segmento) {
        return switch (segmento.getTipoAlvo()) {
            case TODOS -> new LinkedHashSet<>(usuarioApi.buscarIdsAtivos());
            case PERFIL -> new LinkedHashSet<>(usuarioApi.buscarIdsAtivosPorPerfil(segmento.getIdAlvo()));
            case TIPO_USUARIO -> new LinkedHashSet<>(usuarioApi.buscarIdsAtivosPorTipo(segmento.getValorAlvo()));
            case USUARIO -> new LinkedHashSet<>(Set.of(segmento.getIdAlvo()));
        };
    }
}
