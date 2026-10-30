package com.nexo.app.modules.sst.infrastructure.persistence;
import com.nexo.app.modules.sst.domain.ProcessoTreinamentoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;
public interface ProcessoTreinamentoSstRepository extends JpaRepository<ProcessoTreinamentoSst,UUID>{
 Optional<ProcessoTreinamentoSst> findFirstByCodigoAndAtivoTrueOrderByVersaoReferenciaDesc(String codigo);
 List<ProcessoTreinamentoSst> findAllByAtivoTrueOrderByNomeAsc();
}
