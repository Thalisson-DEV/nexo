package com.nexo.app.modules.automacao.application.usecases;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.application.motor.Interpretador;
import com.nexo.app.modules.automacao.domain.expressao.Expressoes;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.iam.ContatoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;

/**
 * Transforma "quem recebe", como a pessoa configurou, em usuários e caixas de e-mail.
 *
 * <p>Cada destinatário configurado é {@code {tipo, valor, rotulo}}:</p>
 * <ul>
 *   <li>{@code usuario} — id de usuário;</li>
 *   <li>{@code colaborador} — id de colaborador; alcança o usuário vinculado a ele (é o
 *       caminho natural quando se pensa em "o RH de Juazeiro é a Maria");</li>
 *   <li>{@code email} — endereço livre, de dentro ou de fora do Nexo;</li>
 *   <li>{@code permissao} / {@code perfil} — todos os usuários ativos com a permissão ou
 *       do perfil, recalculado a cada execução;</li>
 *   <li>{@code papel} — pessoa que o próprio evento identifica (ex.: solicitante);</li>
 *   <li>{@code expressao} — conteúdo dinâmico: a saída de "Escolher destinatários", uma
 *       lista de e-mails, um campo com o id de um usuário…</li>
 * </ul>
 */
@Component
public class ResolvedorDestinatarios implements Interpretador.ResolvedorPessoas {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern UUID_TEXTO = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    public static final int MAX_DESTINATARIOS = 500;

    private final UsuarioApi usuarios;
    private final ColaboradorApi colaboradores;
    private final EnriquecedorEntidades entidades;

    public ResolvedorDestinatarios(UsuarioApi usuarios, ColaboradorApi colaboradores, EnriquecedorEntidades entidades) {
        this.usuarios = usuarios;
        this.colaboradores = colaboradores;
        this.entidades = entidades;
    }

    /** O resultado: usuários ativos e e-mails sem usuário (de fora). */
    public record Resolucao(Set<UUID> usuarios, Set<String> emailsExternos, List<String> avisos) {

        public boolean vazia() {
            return usuarios.isEmpty() && emailsExternos.isEmpty();
        }
    }

    @Override
    public Pessoas resolver(Object valor, ContextoAcao contexto) {
        Resolucao r = resolverTudo(valor, contexto);
        return new Pessoas(List.copyOf(r.usuarios()), List.copyOf(emails(r)));
    }

    public Resolucao resolverTudo(Object valor, ContextoAcao contexto) {
        Set<UUID> ids = new LinkedHashSet<>();
        Set<String> emails = new LinkedHashSet<>();
        List<String> avisos = new ArrayList<>();
        adicionar(valor, contexto, ids, emails, avisos, 0);
        Set<UUID> ativos = new LinkedHashSet<>();
        if (!ids.isEmpty()) {
            Map<UUID, ContatoUsuario> contatos = usuarios.buscarContatos(ids);
            for (UUID id : ids) {
                ContatoUsuario c = contatos.get(id);
                if (c != null && c.ativo()) ativos.add(id);
                else avisos.add("Usuário " + id + " inexistente ou inativo foi ignorado");
            }
        }
        if (ativos.size() + emails.size() > MAX_DESTINATARIOS) {
            throw new ErroAcao("Destinatários demais (" + (ativos.size() + emails.size()) + "); o limite é " + MAX_DESTINATARIOS + ".");
        }
        return new Resolucao(ativos, emails, avisos);
    }

    /** E-mails de todos: os dos usuários (quem tem) e os externos. */
    public Set<String> emails(Resolucao resolucao) {
        Set<String> todos = new LinkedHashSet<>();
        if (!resolucao.usuarios().isEmpty()) {
            Map<UUID, String> porUsuario = usuarios.buscarEmailsPorIds(resolucao.usuarios());
            for (UUID id : resolucao.usuarios()) {
                String email = porUsuario.get(id);
                if (email != null && !email.isBlank()) todos.add(email.strip().toLowerCase());
            }
        }
        todos.addAll(resolucao.emailsExternos());
        return todos;
    }

    /** Pessoas descritas para as saídas dos passos e para as telas. */
    public List<Map<String, Object>> descrever(Collection<UUID> ids) {
        List<Map<String, Object>> pessoas = new ArrayList<>();
        if (ids.isEmpty()) return pessoas;
        Map<UUID, ContatoUsuario> contatos = usuarios.buscarContatos(ids);
        for (UUID id : ids) {
            ContatoUsuario c = contatos.get(id);
            if (c == null) continue;
            pessoas.add(Pessoa.de(c, null));
        }
        return pessoas;
    }

    private void adicionar(Object valor, ContextoAcao contexto, Set<UUID> ids, Set<String> emails, List<String> avisos, int profundidade) {
        if (valor == null || profundidade > 6) return;
        if (valor instanceof Collection<?> lista) {
            for (Object item : lista) adicionar(item, contexto, ids, emails, avisos, profundidade + 1);
            return;
        }
        if (valor instanceof Map<?, ?> bruto) {
            Map<String, Object> mapa = Valores.mapa(bruto);
            if (mapa.containsKey("tipo")) {
                configurado(mapa, contexto, ids, emails, avisos, profundidade);
                return;
            }
            // Pessoa descrita por um passo anterior ({usuario_id, email, ...}).
            Object usuario = mapa.containsKey("usuario_id") ? mapa.get("usuario_id") : mapa.get("id_usuario");
            if (usuario != null && UUID_TEXTO.matcher(Valores.texto(usuario)).matches()) {
                ids.add(UUID.fromString(Valores.texto(usuario)));
                return;
            }
            if (mapa.get("email") != null) {
                adicionar(mapa.get("email"), contexto, ids, emails, avisos, profundidade + 1);
                return;
            }
            if (mapa.get("usuarios") != null || mapa.get("emails") != null) {
                adicionar(mapa.get("usuarios"), contexto, ids, emails, avisos, profundidade + 1);
                adicionar(mapa.get("emails"), contexto, ids, emails, avisos, profundidade + 1);
            }
            return;
        }
        for (String parte : Valores.texto(valor).split("[,;\\s]+")) {
            String t = parte.strip().replaceAll("^<|>$", "");
            if (t.isEmpty()) continue;
            if (UUID_TEXTO.matcher(t).matches()) {
                ids.add(UUID.fromString(t));
            } else if (EMAIL.matcher(t.toLowerCase()).matches()) {
                emails.add(t.toLowerCase());
            } else {
                throw new ErroAcao("Destinatário inválido: " + t);
            }
        }
    }

    private void configurado(Map<String, Object> mapa, ContextoAcao contexto, Set<UUID> ids, Set<String> emails,
                             List<String> avisos, int profundidade) {
        String tipo = Valores.texto(mapa.get("tipo"));
        Object valor = mapa.get("valor");
        String texto = Valores.texto(valor).strip();
        switch (tipo) {
            case "usuario" -> {
                if (UUID_TEXTO.matcher(texto).matches()) ids.add(UUID.fromString(texto));
            }
            case "colaborador" -> {
                if (!UUID_TEXTO.matcher(texto).matches()) return;
                ColaboradorResponse colaborador = colaboradores.buscarPorIdResponse(UUID.fromString(texto)).orElse(null);
                if (colaborador == null || colaborador.idUsuario() == null) {
                    avisos.add("O colaborador " + rotulo(mapa, texto) + " não tem usuário no Nexo");
                } else {
                    ids.add(colaborador.idUsuario());
                }
            }
            case "email" -> {
                for (String parte : texto.split("[,;\\s]+")) {
                    if (parte.isBlank()) continue;
                    if (!EMAIL.matcher(parte.toLowerCase()).matches()) throw new ErroAcao("E-mail inválido: " + parte);
                    String email = parte.toLowerCase();
                    // E-mail de um usuário ativo vira o usuário: assim o aviso chega também ao sininho.
                    usuarios.buscarIdAtivoPorEmail(email).ifPresentOrElse(ids::add, () -> emails.add(email));
                }
            }
            case "permissao" -> ids.addAll(daBase(usuarios.buscarIdsAtivosComPermissao(texto), mapa, rotulo(mapa, texto), avisos));
            case "perfil" -> {
                if (UUID_TEXTO.matcher(texto).matches()) {
                    ids.addAll(daBase(usuarios.buscarIdsAtivosPorPerfil(UUID.fromString(texto)), mapa, rotulo(mapa, texto), avisos));
                }
            }
            case "papel" -> {
                Object papeis = Expressoes.obter(Expressoes.obter(Expressoes.obter(contexto.escopo(), "trigger"), "outputs"), "papeis");
                Object ocupantes = Expressoes.obter(papeis, texto);
                if (ocupantes == null || Valores.vazio(ocupantes)) {
                    avisos.add("O evento não trouxe ninguém no papel '" + texto + "'");
                } else {
                    adicionar(ocupantes, contexto, ids, emails, avisos, profundidade + 1);
                }
            }
            case "expressao" -> adicionar(valor, contexto, ids, emails, avisos, profundidade + 1);
            default -> throw new ErroAcao("Tipo de destinatário desconhecido: " + tipo);
        }
    }

    /**
     * "Só da base": com {@code base} preenchida (ex.: a base do colaborador do evento), fica só
     * quem é dessa base. Sem base resolvida, ou sem ninguém dessa base, o aviso não se perde:
     * vai para todos os encontrados e a execução registra o motivo.
     */
    private Collection<UUID> daBase(Collection<UUID> encontrados, Map<String, Object> mapa, String rotulo, List<String> avisos) {
        if (!mapa.containsKey("base")) return encontrados;
        String base = Valores.comparavel(mapa.get("base"));
        if (base.isEmpty()) {
            avisos.add("A base para filtrar '" + rotulo + "' veio vazia; foram incluídas todas as bases");
            return encontrados;
        }
        List<UUID> daBase = new ArrayList<>();
        for (Map<String, Object> pessoa : entidades.pessoas(encontrados)) {
            if (base.equals(Valores.comparavel(pessoa.get("base"))) && pessoa.get("usuario_id") != null) {
                daBase.add(UUID.fromString(Valores.texto(pessoa.get("usuario_id"))));
            }
        }
        if (daBase.isEmpty() && !encontrados.isEmpty()) {
            avisos.add("Ninguém em '" + rotulo + "' é da base " + Valores.texto(mapa.get("base")) + "; enviado a todos");
            return encontrados;
        }
        return daBase;
    }

    private static String rotulo(Map<String, Object> mapa, String padrao) {
        String r = Valores.texto(mapa.get("rotulo"));
        return r.isBlank() ? padrao : r;
    }
}
