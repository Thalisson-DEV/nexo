package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.DecisaoPorEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisaoPorEmailRepository extends JpaRepository<DecisaoPorEmail, UUID> {

    Optional<DecisaoPorEmail> findByHashToken(String hashToken);
}
