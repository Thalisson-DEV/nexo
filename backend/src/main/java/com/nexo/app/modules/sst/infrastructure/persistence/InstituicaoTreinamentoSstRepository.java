package com.nexo.app.modules.sst.infrastructure.persistence;
import com.nexo.app.modules.sst.domain.InstituicaoTreinamentoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;
public interface InstituicaoTreinamentoSstRepository extends JpaRepository<InstituicaoTreinamentoSst,UUID>{
 Optional<InstituicaoTreinamentoSst> findByCnpjAndAtivoTrue(String cnpj);
 Optional<InstituicaoTreinamentoSst> findFirstByNomeIgnoreCaseAndAtivoTrue(String nome);
 long countByFonteReferencia(String fonteReferencia);
 List<InstituicaoTreinamentoSst> findAllByAtivoTrueOrderByNomeAsc();
}
