package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.ResumoUsuario;
import com.nexo.app.modules.iam.domain.TipoUsuario;
import com.nexo.app.modules.iam.domain.Usuario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    /** Probe limitado: o banco só precisa provar que existe uma linha ativa. */
    Optional<Usuario> findFirstByAtivoTrueOrderByIdAsc();

    /*
     * As três consultas abaixo devolvem só o id porque servem à expansão de público-alvo
     * (mural e motor de regras), onde carregar a entidade inteira de centenas de usuários
     * seria desperdício — o consumidor precisa apenas de para quem entregar.
     */

    @Query("SELECT u.id FROM Usuario u WHERE u.ativo = true AND u.perfil.id = :idPerfil")
    List<UUID> buscarIdsAtivosPorPerfil(@Param("idPerfil") UUID idPerfil);

    @Query("SELECT u.id FROM Usuario u WHERE u.ativo = true AND u.tipo = :tipo")
    List<UUID> buscarIdsAtivosPorTipo(@Param("tipo") TipoUsuario tipo);

    @Query("SELECT u.id FROM Usuario u WHERE u.ativo = true")
    List<UUID> buscarIdsAtivos();

    @Query("""
            SELECT DISTINCT u.id FROM Usuario u
              JOIN u.perfil p
              JOIN p.permissoes perm
             WHERE u.ativo = true AND p.ativo = true AND perm.ativo = true
               AND lower(perm.nome) = lower(:permissao)
            """)
    List<UUID> buscarIdsAtivosComPermissao(@Param("permissao") String permissao);

    /**
     * Resolve vários logins de uma vez. A projeção para {@code ResumoUsuario} evita
     * materializar o perfil (que é EAGER) só para descobrir a quem um {@code @} se
     * refere.
     */
    @Query("""
            SELECT new com.nexo.app.modules.iam.ResumoUsuario(u.id, u.login)
              FROM Usuario u
             WHERE u.ativo = true AND upper(u.login) IN :logins
            """)
    List<ResumoUsuario> buscarAtivosPorLogins(@Param("logins") Collection<String> logins);

    @Query("""
            SELECT new com.nexo.app.modules.iam.ResumoUsuario(u.id, u.login)
              FROM Usuario u
             WHERE u.ativo = true AND upper(u.login) LIKE upper(concat('%', :trecho, '%'))
             ORDER BY u.login ASC
            """)
    List<ResumoUsuario> buscarAtivosPorTrechoDoLogin(@Param("trecho") String trecho, Pageable pageable);

    @Query("""
            SELECT u.id FROM Usuario u
             WHERE lower(u.login) LIKE lower(concat('%', :trecho, '%'))
                OR lower(u.email) LIKE lower(concat('%', :trecho, '%'))
            """)
    List<UUID> buscarIdsPorTrechoDoLogin(@Param("trecho") String trecho);

    Optional<Usuario> findByLoginIgnoreCase(String login);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByLoginIgnoreCase(String login);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByLoginIgnoreCaseAndIdNot(String login, UUID id);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
