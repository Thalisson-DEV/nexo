package com.nexo.app.modules.sst.infrastructure.persistence;
import com.nexo.app.modules.sst.domain.AtivoInspecionavelSst;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;
public interface AtivoInspecionavelSstRepository extends JpaRepository<AtivoInspecionavelSst,UUID>{
 List<AtivoInspecionavelSst> findAllByIdBaseOperacionalAndAtivoTrueOrderByTipoAscIdentificadorLocalAsc(UUID base);
 Optional<AtivoInspecionavelSst> findByIdAndAtivoTrue(UUID id);
 boolean existsByIdBaseOperacionalAndTipoAndIdentificadorLocalAndAtivoTrue(UUID base,AtivoInspecionavelSst.Tipo tipo,String identificador);
}
