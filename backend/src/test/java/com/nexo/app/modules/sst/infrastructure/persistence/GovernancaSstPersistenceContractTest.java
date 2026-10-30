package com.nexo.app.modules.sst.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancaSstPersistenceContractTest {

    @Test
    void migrationProtegeAtivacaoConcorrenteEParticipacaoDuplicada() throws Exception {
        String sql;
        try (var input = new ClassPathResource(
                "db/migration/V94__SST_Governanca_Corpos_e_Categorias.sql").getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("CREATE UNIQUE INDEX ux_corpo_governanca_unidade_ativa"));
        assertTrue(sql.contains("WHERE status = 'ATIVO' AND ativo"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX ux_membro_corpo_participacao_ativa"));
        assertTrue(sql.contains("WHERE fim_participacao IS NULL AND ativo"));
        assertTrue(sql.contains("'CIPA', 'BRIGADA'"));
        assertTrue(sql.contains("'CIPA_DIMENSIONADA', 'REPRESENTANTE_NOMEADO'"));
    }

    @Test
    void consultaCompartilhadaDeMembrosRespeitaOwnershipEDataEfetiva() throws Exception {
        var metodo = MembroCorpoSstRepository.class.getMethod(
                "buscarAtivosNoCorpoEm", UUID.class, LocalDate.class);
        String jpql = metodo.getAnnotation(Query.class).value();

        assertTrue(jpql.contains("membro.corpo.id = :idCorpo"));
        assertTrue(jpql.contains("membro.inicioParticipacao <= :dataReferencia"));
        assertTrue(jpql.contains("membro.fimParticipacao > :dataReferencia"));
    }
}
