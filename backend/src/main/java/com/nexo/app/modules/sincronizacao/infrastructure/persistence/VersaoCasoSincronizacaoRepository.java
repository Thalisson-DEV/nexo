package com.nexo.app.modules.sincronizacao.infrastructure.persistence;
import com.nexo.app.modules.sincronizacao.domain.VersaoCasoSincronizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VersaoCasoSincronizacaoRepository extends JpaRepository<VersaoCasoSincronizacao,UUID>{ List<VersaoCasoSincronizacao> findByCaso_IdOrderByNumeroAsc(UUID caso); Optional<VersaoCasoSincronizacao> findTopByCaso_IdOrderByNumeroDesc(UUID caso); }
