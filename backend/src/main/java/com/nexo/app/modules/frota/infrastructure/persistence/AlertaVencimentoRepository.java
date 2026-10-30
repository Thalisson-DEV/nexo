package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.AlertaVencimento;
import com.nexo.app.modules.frota.domain.TipoAlvoAlerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface AlertaVencimentoRepository extends JpaRepository<AlertaVencimento, UUID> {

    /** Já houve aviso nesta faixa ou numa mais urgente (menor) para este vencimento? */
    boolean existsByTipoAlvoAndIdAlvoAndDataVencimentoAndDiasAntecedenciaLessThanEqual(
            TipoAlvoAlerta tipoAlvo, UUID idAlvo, LocalDate dataVencimento, Integer diasAntecedencia);
}
