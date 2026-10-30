package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfilNormativoGovernancaSstTest {

    @Test
    void perfilEstadualConfirmadoSoSeAplicaNaJurisdicaoEVigencia() {
        PerfilNormativoGovernancaSst perfil = new PerfilNormativoGovernancaSst(
                "BA/CBMBA", 1, EscopoPerfilNormativoSst.ESTADUAL,
                TipoCorpoGovernancaSst.BRIGADA, "BA", "CBMBA", "IT-01",
                "2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "https://www.cbm.ba.gov.br/it-01",
                Map.of(PapelMembroGovernancaSst.BRIGADISTA, 2));
        perfil.confirmar(UUID.randomUUID());

        assertTrue(perfil.aplicaA(TipoCorpoGovernancaSst.BRIGADA, "BA",
                LocalDate.of(2026, 6, 1)));
        assertFalse(perfil.aplicaA(TipoCorpoGovernancaSst.BRIGADA, "SP",
                LocalDate.of(2026, 6, 1)));
        assertFalse(perfil.aplicaA(TipoCorpoGovernancaSst.BRIGADA, "BA",
                LocalDate.of(2027, 1, 1)));
        assertThrows(InvarianteVioladaException.class, () -> perfil.confirmar(UUID.randomUUID()));
    }
}
