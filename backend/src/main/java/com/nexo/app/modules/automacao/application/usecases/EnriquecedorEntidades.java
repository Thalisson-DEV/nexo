package com.nexo.app.modules.automacao.application.usecases;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.iam.ContatoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.ContextoOrganizacional;

/**
 * Resolve as entidades por trás dos identificadores que os eventos carregam: o
 * colaborador de um desconto vira nome, base, função e o usuário dele; o solicitante
 * vira pessoa com e-mail e base. É o que permite a uma condição perguntar "o colaborador
 * é da base de Juazeiro?" sem que o evento precise saber disso.
 */
@Component
public class EnriquecedorEntidades {

    private final UsuarioApi usuarios;
    private final ColaboradorApi colaboradores;

    public EnriquecedorEntidades(UsuarioApi usuarios, ColaboradorApi colaboradores) {
        this.usuarios = usuarios;
        this.colaboradores = colaboradores;
    }

    /** Pessoas (usuário + colaborador) dos usuários informados, na mesma ordem. */
    public List<Map<String, Object>> pessoas(Collection<UUID> idsUsuario) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (idsUsuario == null || idsUsuario.isEmpty()) return lista;
        Map<UUID, ContatoUsuario> contatos = usuarios.buscarContatos(idsUsuario);
        Set<UUID> idsColaborador = new HashSet<>();
        contatos.values().forEach(c -> {
            if (c.idColaborador() != null) idsColaborador.add(c.idColaborador());
        });
        Map<UUID, ContextoOrganizacional> organizacao = idsColaborador.isEmpty() ? Map.of()
                : colaboradores.buscarContextoOrganizacional(idsColaborador);
        for (UUID id : idsUsuario) {
            ContatoUsuario c = contatos.get(id);
            if (c == null) continue;
            lista.add(Pessoa.de(c, c.idColaborador() == null ? null : organizacao.get(c.idColaborador())));
        }
        return lista;
    }

    public Map<String, Object> pessoa(UUID idUsuario) {
        if (idUsuario == null) return null;
        List<Map<String, Object>> lista = pessoas(List.of(idUsuario));
        return lista.isEmpty() ? null : lista.get(0);
    }

    /** O colaborador com o usuário vinculado (quando houver). */
    public Map<String, Object> colaborador(UUID idColaborador) {
        if (idColaborador == null) return null;
        Map<UUID, ContextoOrganizacional> organizacao = colaboradores.buscarContextoOrganizacional(List.of(idColaborador));
        ContextoOrganizacional org = organizacao.get(idColaborador);
        if (org == null) return null;
        UUID idUsuario = colaboradores.buscarPorIdResponse(idColaborador).map(ColaboradorResponse::idUsuario).orElse(null);
        ContatoUsuario contato = idUsuario == null ? null : usuarios.buscarContatos(List.of(idUsuario)).get(idUsuario);
        Map<String, Object> p = new LinkedHashMap<>(Pessoa.de(contato, org));
        p.put("ativo", org.ativo());
        return p;
    }
}
