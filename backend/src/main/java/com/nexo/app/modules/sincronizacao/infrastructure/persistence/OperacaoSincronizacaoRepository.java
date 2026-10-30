package com.nexo.app.modules.sincronizacao.infrastructure.persistence;
import com.nexo.app.modules.sincronizacao.domain.OperacaoSincronizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OperacaoSincronizacaoRepository extends JpaRepository<OperacaoSincronizacao,UUID>{ Optional<OperacaoSincronizacao> findByIdOperacaoLocalAndIdUsuarioOrigem(UUID id,UUID usuario); Optional<OperacaoSincronizacao> findByIdOperacaoLocal(UUID id); }
