package com.nexo.app.modules.rh;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consulta de colaboradores por outros módulos.
 */
public interface ColaboradorApi {
    /**
     * Busca o nome completo de um colaborador vinculado a um usuário.
     */
    Optional<String> buscarNomeCompletoPorUsuario(UUID idUsuario);

    /**
     * Busca os dados do colaborador vinculado ao usuário autenticado.
     */
    Optional<ColaboradorResponse> buscarPorUsuarioResponse(UUID idUsuario);

    /**
     * Verifica se um colaborador existe e está ativo.
     */
    boolean existeColaborador(UUID id);

    /**
     * Busca os dados de um colaborador por ID para exibição em outros módulos.
     */
    Optional<ColaboradorResponse> buscarPorIdResponse(UUID id);

    /**
     * Busca colaboradores por correspondência exata do nome completo.
     * Pode retornar mais de um registro em caso de homônimos.
     */
    List<ColaboradorResponse> buscarPorNomeCompletoResponse(String nomeCompleto);

    /**
     * Lista os colaboradores ativos que exercem uma função — usado por outros
     * módulos para expandir regras definidas no nível da função.
     */
    List<ColaboradorResponse> listarAtivosPorFuncao(UUID idFuncao);

    /**
     * Lista, em uma única consulta ao RH, colaboradores ativos das bases e/ou funções
     * informadas. Quando os dois conjuntos são fornecidos, os critérios são combinados
     * por interseção. O contrato deliberadamente não expõe CPF.
     *
     * <p>Conjuntos nulos ou vazios não significam "toda a empresa": ao menos um filtro
     * deve ser informado para evitar cargas acidentais sem limite.</p>
     */
    List<ColaboradorAtivoResumo> listarAtivosPorEscopo(
            Collection<UUID> idsBaseOperacional,
            Collection<UUID> idsFuncao);

    /**
     * Conta o efetivo ativo, opcionalmente restrito a bases operacionais e/ou centros
     * de custo (coleções nulas ou vazias contam toda a empresa; informadas, restringem
     * a quem está em <em>qualquer</em> das opções). Serve de denominador para
     * indicadores de cobertura em outros módulos.
     */
    long contarAtivos(Collection<UUID> idsBaseOperacional, Collection<UUID> idsCentroCusto);

    /**
     * Resumos dos colaboradores informados, indexados por ID e em uma consulta só.
     *
     * <p>É o que permite a outro módulo trocar uma coluna de identificador técnico por
     * um nome de gente sem cair em N+1: a listagem resolve a página inteira de uma vez.
     * IDs inexistentes simplesmente não aparecem no resultado — um vínculo apontando
     * para um registro que sumiu é dado a exibir vazio, nunca motivo de erro na tela.</p>
     */
    Map<UUID, ResumoColaborador> buscarResumosPorIds(Collection<UUID> ids);

    /**
     * Estrutura organizacional dos colaboradores informados, indexada por ID e em uma
     * consulta só — função, base operacional, centro de custo e processo, com os
     * identificadores ao lado dos nomes.
     *
     * <p>É o que permite a outro módulo <b>quebrar seus próprios números</b> pela
     * organização sem replicar o cadastro: o dashboard de descontos agrupa valores por
     * base e por função a partir daqui, e o recorte por centro de custo em uma tela de
     * gestão é só um filtro sobre este mesmo mapa.</p>
     *
     * <p>Como em {@link #buscarResumosPorIds(Collection)}, IDs inexistentes não
     * aparecem no resultado. Colaboradores <b>inativos</b> aparecem normalmente, com
     * {@code ativo=false}: um desligado continua tendo histórico, e sumir com ele faria
     * a soma por base não bater com o total da tela.</p>
     */
    Map<UUID, ContextoOrganizacional> buscarContextoOrganizacional(Collection<UUID> ids);

    /**
     * IDs dos colaboradores cujo nome contém o trecho informado (ignorando caixa e
     * acento), para que outro módulo consiga <b>filtrar</b> pelo nome da pessoa.
     *
     * <p>Sem isto, filtrar usuários por nome de colaborador seria impossível: o nome
     * está cifrado no banco (AES-256) e não aceita {@code LIKE}. A comparação acontece
     * aqui dentro, contra a coluna normalizada de busca, e o que atravessa a fronteira
     * do módulo são apenas os IDs — o texto sensível nunca sai do RH.</p>
     *
     * <p>Devolve todos os que casam, sem teto: quem chama usa o resultado em um
     * {@code IN}, e truncar em silêncio transformaria "filtrei" em "filtrei quase
     * tudo", que é pior do que uma consulta grande.</p>
     */
    List<UUID> buscarIdsPorTrechoDoNome(String trecho);
}
