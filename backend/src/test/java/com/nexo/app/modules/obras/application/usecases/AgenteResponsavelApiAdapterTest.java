package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.AgenteResponsavelApi;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgenteResponsavelApiAdapterTest {

    @Mock
    private AgenteResponsavelRepository repository;

    private AgenteResponsavelApi api;

    @BeforeEach
    void setUp() {
        api = new AgenteResponsavelApiAdapter(repository);
    }

    @Test
    void deveResolverAgenteDaObraEOutroAgenteAtivoPeloMesmoContratoPublico() {
        UUID idAgenteDaObra = UUID.randomUUID();
        UUID idOutroAgente = UUID.randomUUID();
        AgenteResponsavel agenteDaObra = agente(idAgenteDaObra, "AGENTE DA OBRA");
        AgenteResponsavel outroAgente = agente(idOutroAgente, "FISCAL DA RESERVA");
        when(repository.findById(idAgenteDaObra)).thenReturn(Optional.of(agenteDaObra));
        when(repository.findById(idOutroAgente)).thenReturn(Optional.of(outroAgente));

        assertThat(api.buscarAtivoPorId(idAgenteDaObra)).get()
                .extracting(AgenteResponsavelApi.AgenteResponsavelPublico::nome)
                .isEqualTo("AGENTE DA OBRA");
        assertThat(api.buscarAtivoPorId(idOutroAgente)).get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.id()).isEqualTo(idOutroAgente);
                    assertThat(snapshot.nome()).isEqualTo("FISCAL DA RESERVA");
                    assertThat(snapshot.email()).isEqualTo("agente@example.com");
                    assertThat(snapshot.empresaOrgao()).isEqualTo("CONCESSIONARIA");
                });
    }

    @Test
    void deveRetornarVazioParaAgenteAusenteOuInativo() {
        UUID idAusente = UUID.randomUUID();
        UUID idInativo = UUID.randomUUID();
        AgenteResponsavel inativo = agente(idInativo, "AGENTE INATIVO");
        inativo.desativar();
        when(repository.findById(idAusente)).thenReturn(Optional.empty());
        when(repository.findById(idInativo)).thenReturn(Optional.of(inativo));

        assertThat(api.buscarAtivoPorId(idAusente)).isEmpty();
        assertThat(api.buscarAtivoPorId(idInativo)).isEmpty();
        assertThat(api.buscarAtivoPorId(null)).isEmpty();
    }

    private AgenteResponsavel agente(UUID id, String nome) {
        AgenteResponsavel agente = new AgenteResponsavel(
                nome, "agente@example.com", "74999999999", "CONCESSIONARIA");
        ReflectionTestUtils.setField(agente, "id", id);
        return agente;
    }
}
