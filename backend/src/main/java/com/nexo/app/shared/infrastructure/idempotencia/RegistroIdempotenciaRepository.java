package com.nexo.app.shared.infrastructure.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RegistroIdempotenciaRepository extends JpaRepository<RegistroIdempotencia, String> {

    /**
     * Tenta reservar a chave para execução, em um único comando atômico.
     *
     * <p>Retorna {@code 1} quando a reserva foi obtida (chave inédita, ou reserva antiga
     * já expirada e retomada) e {@code 0} quando já existe uma reserva viva — que é o
     * sinal de "esta intenção já está sendo, ou já foi, atendida".</p>
     *
     * <p>É SQL nativo por necessidade, não por preferência: o {@code ON CONFLICT} é o
     * que transforma a checagem e a escrita em um só passo. Qualquer variante em duas
     * etapas (ler, depois gravar) deixa aberta justamente a janela de milissegundos que
     * o duplo clique explora.</p>
     *
     * <p>O ramo {@code DO UPDATE ... WHERE data_expiracao < now()} retoma uma reserva
     * vencida em vez de recusá-la. Sem ele, uma chave cuja requisição original morreu
     * junto com o processo (deploy no meio do request, por exemplo) ficaria emperrada
     * em EM_PROCESSAMENTO até o expurgo, bloqueando o reenvio legítimo.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotencia_requisicoes
                (chave, id_usuario, metodo, caminho, estado, data_criacao, data_expiracao)
            VALUES
                (:chave, :idUsuario, :metodo, :caminho, 'EM_PROCESSAMENTO', now(), :dataExpiracao)
            ON CONFLICT (chave) DO UPDATE SET
                id_usuario        = EXCLUDED.id_usuario,
                metodo            = EXCLUDED.metodo,
                caminho           = EXCLUDED.caminho,
                estado            = 'EM_PROCESSAMENTO',
                digest_requisicao = NULL,
                status_http       = NULL,
                content_type      = NULL,
                corpo_resposta    = NULL,
                data_criacao      = now(),
                data_conclusao    = NULL,
                data_expiracao    = EXCLUDED.data_expiracao
            WHERE idempotencia_requisicoes.data_expiracao < now()
            """, nativeQuery = true)
    int reservar(@Param("chave") String chave,
                 @Param("idUsuario") UUID idUsuario,
                 @Param("metodo") String metodo,
                 @Param("caminho") String caminho,
                 @Param("dataExpiracao") OffsetDateTime dataExpiracao);

    @Modifying
    @Query(value = """
            UPDATE idempotencia_requisicoes
               SET estado            = 'CONCLUIDA',
                   status_http       = :statusHttp,
                   content_type      = :contentType,
                   corpo_resposta    = :corpoResposta,
                   digest_requisicao = :digest,
                   data_conclusao    = now()
             WHERE chave = :chave
            """, nativeQuery = true)
    int concluir(@Param("chave") String chave,
                 @Param("statusHttp") int statusHttp,
                 @Param("contentType") String contentType,
                 @Param("corpoResposta") String corpoResposta,
                 @Param("digest") String digest);

    /** Expurgo das reservas vencidas; devolve quantas linhas saíram, para o log. */
    @Modifying
    @Query("DELETE FROM RegistroIdempotencia r WHERE r.dataExpiracao < :limite")
    int expurgarExpirados(@Param("limite") OffsetDateTime limite);
}
