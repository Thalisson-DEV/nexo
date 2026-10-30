package com.nexo.app.modules.iam;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de IAM para consumo por outros módulos.
 */
public interface UsuarioApi {
    /**
     * Busca o nome (login) do usuário pelo ID.
     */
    Optional<String> buscarNomePorId(UUID id);

    List<UUID> buscarIdsPorTrechoDoLogin(String trecho);

    /**
     * Verifica se o usuário é do tipo CAMPO.
     */
    boolean isUsuarioCampo(UUID id);

    /**
     * Atributos do usuário que decidem a que públicos ele pertence.
     *
     * <p>Uma chamada só, para que a montagem do mural — que roda a cada abertura da
     * aplicação — não precise de uma consulta por dimensão de segmentação.</p>
     */
    Optional<ContextoAudienciaUsuario> buscarContextoAudiencia(UUID id);

    /**
     * IDs dos usuários ativos de um perfil de acesso.
     */
    List<UUID> buscarIdsAtivosPorPerfil(UUID idPerfil);

    /**
     * IDs dos usuários ativos de um tipo ({@code ADMINISTRATIVO} ou {@code CAMPO}).
     * Tipo desconhecido devolve lista vazia, e não erro — quem chama está expandindo
     * uma configuração que pode ter ficado obsoleta.
     */
    List<UUID> buscarIdsAtivosPorTipo(String tipoUsuario);

    /**
     * IDs de todos os usuários ativos.
     */
    List<UUID> buscarIdsAtivos();

    /**
     * IDs dos usuários ativos cujo perfil concede a permissão informada.
     *
     * <p>É o que permite a uma regra de notificação endereçar "quem aprova desconto"
     * em vez de uma lista de nomes: quando alguém ganha ou perde a atribuição, os
     * destinatários acompanham sozinhos.</p>
     */
    List<UUID> buscarIdsAtivosComPermissao(String permissao);

    /**
     * Logins dos IDs informados, em uma consulta só. Evita o N+1 de relatórios que
     * precisam nomear cada usuário de uma lista.
     */
    Map<UUID, String> buscarLoginsPorIds(Collection<UUID> ids);

    /**
     * E-mails dos IDs informados, em uma consulta só.
     *
     * <p>Usuário sem e-mail cadastrado simplesmente não aparece no resultado — é o
     * caso comum dos usuários de campo, e não é erro: significa que aquela pessoa é
     * alcançável pelo sininho, não pela caixa de correio. Quem chama trata a ausência
     * como "não notificável por este canal", nunca como falha.</p>
     */
    Map<UUID, String> buscarEmailsPorIds(Collection<UUID> ids);

    /**
     * Caminho inverso do anterior: dos logins para os IDs, em uma consulta só.
     *
     * <p>Existe para as marcações ({@code @LOGIN} escrito no meio de um texto). A
     * comparação ignora caixa e o mapa devolvido é indexado pelo login <b>em
     * maiúsculas</b>, que é como o cadastro o normaliza — quem chama compara contra o
     * que digitou sem se preocupar com isso.</p>
     *
     * <p>Logins inexistentes ou de usuários inativos simplesmente não aparecem no
     * resultado: um {@code @} digitado errado é erro de escrita, não falha de
     * operação, e não pode derrubar a mensagem inteira.</p>
     */
    Map<String, UUID> buscarIdsAtivosPorLogins(Collection<String> logins);

    /**
     * Usuários ativos cujo login contém o trecho informado, para o autocomplete de
     * marcação.
     *
     * <p>O limite é obrigatório porque a consulta serve a uma lista que aparece
     * enquanto se digita: sem teto, os primeiros caracteres varreriam o cadastro
     * inteiro a cada tecla.</p>
     */
    List<ResumoUsuario> buscarAtivosPorTrechoDoLogin(String trecho, int limite);

    /**
     * Contatos dos usuários informados (ativos ou não), em uma consulta só. É o que o
     * motor de automações usa para transformar "quem recebe" em caixa de e-mail e em
     * colaborador — e, pelo colaborador, em base e função.
     */
    Map<UUID, ContatoUsuario> buscarContatos(Collection<UUID> ids);

    /** Usuário ativo dono deste e-mail, quando houver. */
    Optional<UUID> buscarIdAtivoPorEmail(String email);

    /**
     * Permissões efetivas do usuário ativo (as do perfil). Vazio se o usuário não existe
     * ou está inativo. Serve às execuções feitas em nome de alguém fora de uma
     * requisição — a automação enxerga exatamente o que o seu dono enxergaria.
     */
    java.util.Set<String> buscarPermissoes(UUID id);

    /** Perfis de acesso ativos (id e nome), para seletores de destinatário. */
    List<com.nexo.app.shared.application.dto.LookupOptionDto> listarPerfisAtivos();

    /** Nomes das permissões cadastradas, em ordem alfabética. */
    List<String> listarPermissoes();
}
