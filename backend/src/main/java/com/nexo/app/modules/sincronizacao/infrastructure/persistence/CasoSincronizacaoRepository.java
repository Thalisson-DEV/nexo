package com.nexo.app.modules.sincronizacao.infrastructure.persistence;
import com.nexo.app.modules.sincronizacao.domain.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CasoSincronizacaoRepository extends JpaRepository<CasoSincronizacao,UUID>, JpaSpecificationExecutor<CasoSincronizacao>{
 Optional<CasoSincronizacao> findByOperacaoId(UUID id);
}
