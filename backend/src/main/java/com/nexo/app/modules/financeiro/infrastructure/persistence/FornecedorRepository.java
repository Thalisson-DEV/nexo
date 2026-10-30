package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.Fornecedor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, UUID>, JpaSpecificationExecutor<Fornecedor> {

    /** O documento é guardado apenas com dígitos, então a comparação é exata. */
    Optional<Fornecedor> findByDocumento(String documento);

    Optional<Fornecedor> findByNomeFantasiaIgnoreCase(String nomeFantasia);

    @Query("""
            select fornecedor.id from Fornecedor fornecedor
            where cast(function('immutable_unaccent', lower(fornecedor.nomeFantasia)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(fornecedor.razaoSocial)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(fornecedor.observacoes)) as string) like concat('%', :trecho, '%')
               or fornecedor.documento like concat('%', :digitos, '%')
            """)
    java.util.List<UUID> buscarIdsPorTrecho(@Param("trecho") String trecho,
                                            @Param("digitos") String digitos);

    @Query("""
            select f from Fornecedor f
            where f.dadosBancarios is not null
              and f.dadosBancariosBusca is null
            """)
    java.util.List<Fornecedor> buscarPendentesReindexacao(Pageable pageable);
}
