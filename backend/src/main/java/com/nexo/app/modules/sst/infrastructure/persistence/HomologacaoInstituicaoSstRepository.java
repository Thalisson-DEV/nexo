package com.nexo.app.modules.sst.infrastructure.persistence;
import com.nexo.app.modules.sst.domain.HomologacaoInstituicaoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.UUID;
public interface HomologacaoInstituicaoSstRepository extends JpaRepository<HomologacaoInstituicaoSst,UUID>{
 List<HomologacaoInstituicaoSst> findAllByIdInstituicaoAndAtivoTrueOrderByDataCriacaoDesc(UUID idInstituicao);
}
