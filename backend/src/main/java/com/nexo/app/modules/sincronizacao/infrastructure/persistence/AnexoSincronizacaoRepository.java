package com.nexo.app.modules.sincronizacao.infrastructure.persistence;
import com.nexo.app.modules.sincronizacao.domain.AnexoSincronizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AnexoSincronizacaoRepository extends JpaRepository<AnexoSincronizacao,UUID>{ java.util.List<AnexoSincronizacao> findByOperacao_Id(UUID idOperacao); }
