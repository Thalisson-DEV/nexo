package com.nexo.app.modules.sst.infrastructure.persistence;
import com.nexo.app.modules.sst.domain.ReferenciaMatrizTreinamentoSst;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface ReferenciaMatrizTreinamentoSstRepository extends JpaRepository<ReferenciaMatrizTreinamentoSst,UUID>{
 boolean existsBySha256Fonte(String hash); long countBySha256FonteAndTipo(String hash,ReferenciaMatrizTreinamentoSst.Tipo tipo);
}
