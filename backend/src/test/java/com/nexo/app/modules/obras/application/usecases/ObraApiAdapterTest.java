package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.application.dto.ProjetoRelacionadoResponse;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OcorrenciaObra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.OcorrenciaObraRepository;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ObraApiAdapterTest {

    @Mock
    private ObraRepository repository;
    @Mock
    private OcorrenciaObraRepository ocorrenciaRepository;
    @Mock
    private RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;
    @Mock
    private ProcessoNegocioApi processoNegocioApi;

    private ObraApi api;

    @BeforeEach
    void setUp() {
        api = new ObraApiAdapter(repository, ocorrenciaRepository, relacionamentoProjetoUseCases, processoNegocioApi);
    }

    @Test
    void deveBuscarObraTecnicaValidaPorIdENumeroComContratoMinimoCompleto() {
        UUID id = UUID.randomUUID();
        UUID idProcesso = UUID.randomUUID();
        UUID idBase = UUID.randomUUID();
        UUID idEquipe = UUID.randomUUID();
        UUID idAgente = UUID.randomUUID();
        UUID idRelacionado = UUID.randomUUID();
        Obra obra = obra(id, idProcesso, idBase, TipoObra.CORRETIVA, idEquipe, idAgente);
        ProjetoRelacionadoResponse relacionado = new ProjetoRelacionadoResponse(
                UUID.randomUUID(), idRelacionado, "7000001", "PROJETO KIT", TipoObra.OBRA,
                TipoVinculoProjeto.KIT, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null,
                null, "PLANEJADA", true
        );
        when(repository.findById(id)).thenReturn(Optional.of(obra));
        when(repository.findByNumeroIgnoreCase("1000001")).thenReturn(Optional.of(obra));
        when(processoNegocioApi.buscarPorIdResponse(idProcesso))
                .thenReturn(Optional.of(new ProcessoNegocioResponse(idProcesso, "OBRAS", true)));
        when(relacionamentoProjetoUseCases.listarSemValidarExistencia(id)).thenReturn(List.of(relacionado));

        ObraApi.ObraPublica porId = api.buscarTecnicaAtivaPorId(id).orElseThrow();
        ObraApi.ObraPublica porNumero = api.buscarPorNumero(" 1000001 ").orElseThrow();

        assertThat(porId.tipoObra()).isEqualTo(ObraApi.TipoObraPublico.CORRETIVA);
        assertThat(porId.ativa()).isTrue();
        assertThat(porId.idProcesso()).isEqualTo(idProcesso);
        assertThat(porId.nomeProcesso()).isEqualTo("OBRAS");
        assertThat(porId.processoTecnico()).isTrue();
        assertThat(porId.idBaseOperacional()).isEqualTo(idBase);
        assertThat(porId.idsEquipes()).containsExactly(idEquipe);
        assertThat(porId.agenteResponsavel().id()).isEqualTo(idAgente);
        assertThat(porId.projetosRelacionados()).singleElement()
                .satisfies(projeto -> {
                    assertThat(projeto.id()).isEqualTo(idRelacionado);
                    assertThat(projeto.tipoVinculo()).isEqualTo(ObraApi.TipoVinculoProjetoPublico.KIT);
                });
        assertThat(porNumero.id()).isEqualTo(id);
    }

    @Test
    void deveRetornarVazioParaObraAusente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(api.buscarPorId(id)).isEmpty();
        assertThat(api.buscarTecnicaAtivaPorId(id)).isEmpty();
        assertThat(api.buscarPorNumero(" ")).isEmpty();
    }

    @Test
    void deveExporDesativadaMasNaoAceitaLaComoTecnicaAtiva() {
        UUID id = UUID.randomUUID();
        UUID idProcesso = UUID.randomUUID();
        Obra obra = obra(id, idProcesso, UUID.randomUUID(), TipoObra.PREVENTIVA,
                UUID.randomUUID(), UUID.randomUUID());
        obra.desativar();
        when(repository.findById(id)).thenReturn(Optional.of(obra));
        when(processoNegocioApi.buscarPorIdResponse(idProcesso))
                .thenReturn(Optional.of(new ProcessoNegocioResponse(idProcesso, "OBRAS", true)));
        when(relacionamentoProjetoUseCases.listarSemValidarExistencia(id)).thenReturn(List.of());

        assertThat(api.buscarPorId(id)).get().extracting(ObraApi.ObraPublica::ativa).isEqualTo(false);
        assertThat(api.buscarTecnicaAtivaPorId(id)).isEmpty();
    }

    @Test
    void deveRejeitarObraDeProcessoNaoTecnicoNaBuscaElegivel() {
        UUID id = UUID.randomUUID();
        UUID idProcesso = UUID.randomUUID();
        Obra obra = obra(id, idProcesso, UUID.randomUUID(), TipoObra.OBRA,
                UUID.randomUUID(), UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.of(obra));
        when(processoNegocioApi.buscarPorIdResponse(idProcesso))
                .thenReturn(Optional.of(new ProcessoNegocioResponse(idProcesso, "COMERCIAL", true)));
        when(relacionamentoProjetoUseCases.listarSemValidarExistencia(id)).thenReturn(List.of());

        assertThat(api.buscarPorId(id)).get()
                .extracting(ObraApi.ObraPublica::processoTecnico).isEqualTo(false);
        assertThat(api.buscarTecnicaAtivaPorId(id)).isEmpty();
    }

    @Test
    void deveVincularEquipeExternaDeFormaIdempotente() {
        UUID id = UUID.randomUUID();
        Obra obra = obra(id, UUID.randomUUID(), UUID.randomUUID(), TipoObra.OBRA, UUID.randomUUID(), UUID.randomUUID());
        UUID equipe = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(obra));

        api.vincularEquipeSeAusente(id, equipe);

        verify(repository).vincularEquipeSeAusente(id, equipe);
    }

    @Test
    void deveResolverOuCriarOcorrenciaComCodigoNormalizado() {
        UUID idObra = UUID.randomUUID();
        UUID idOcorrencia = UUID.randomUUID();
        Obra obra = obra(idObra, UUID.randomUUID(), UUID.randomUUID(), TipoObra.CORRETIVA,
                UUID.randomUUID(), UUID.randomUUID());
        OcorrenciaObra ocorrencia = new OcorrenciaObra(obra, "  os   42 ");
        ReflectionTestUtils.setField(ocorrencia, "id", idOcorrencia);
        when(repository.findById(idObra)).thenReturn(Optional.of(obra));
        when(ocorrenciaRepository.findByObraIdAndCodigoNormalizado(idObra, "OS 42"))
                .thenReturn(Optional.of(ocorrencia));

        ObraApi.OcorrenciaPublica resultado = api.resolverOuCriarOcorrencia(idObra, "  os   42 ");

        assertThat(resultado.id()).isEqualTo(idOcorrencia);
        assertThat(resultado.codigo()).isEqualTo("OS 42");
        verify(ocorrenciaRepository).inserirSeAusente(idObra, "OS 42");
    }

    private Obra obra(UUID id, UUID idProcesso, UUID idBase, TipoObra tipoObra,
                      UUID idEquipe, UUID idAgente) {
        AgenteResponsavel agente = new AgenteResponsavel("AGENTE", "agente@example.com", null, null);
        ReflectionTestUtils.setField(agente, "id", idAgente);
        Obra obra = new Obra(
                "1000001", "PROJETO TECNICO", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
                idProcesso, idBase, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new StatusObra("PLANEJADA", 1, false),
                agente, Set.of(idEquipe), tipoObra
        );
        ReflectionTestUtils.setField(obra, "id", id);
        return obra;
    }
}
