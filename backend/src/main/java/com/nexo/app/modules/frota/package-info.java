/**
 * Gestão de frota: ficha do veículo, implementos, condutores, documentos e odômetro.
 *
 * <p>O veículo nasce e mora no RH ({@code rh.Veiculo}); este módulo guarda o que só
 * a frota precisa e conversa com o RH pela API pública e por eventos. Decisões em
 * {@code docs/adr/0001-modulo-gestao-frota.md}.</p>
 */
@ApplicationModule(
        displayName = "frota"
)
package com.nexo.app.modules.frota;

import org.springframework.modulith.ApplicationModule;
