package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.automacao.domain.ArquivoAutomacao;

public interface ArquivoAutomacaoRepository extends JpaRepository<ArquivoAutomacao, UUID> {

    @Query("SELECT a.id AS id, a.nome AS nome, a.tipoConteudo AS tipoConteudo, a.tamanhoBytes AS tamanhoBytes FROM ArquivoAutomacao a WHERE a.idExecucao = :execucao ORDER BY a.criadoEm")
    List<ResumoArquivo> listarDaExecucao(@Param("execucao") UUID idExecucao);

    /** Arquivos de execuções antigas: o e-mail já levou o anexo, o banco não precisa guardá-lo para sempre. */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM automacao_arquivos WHERE criado_em < :limite", nativeQuery = true)
    int expurgar(@Param("limite") OffsetDateTime limite);

    interface ResumoArquivo {
        UUID getId();

        String getNome();

        String getTipoConteudo();

        long getTamanhoBytes();
    }
}
