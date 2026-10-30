package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.obras.AgenteResponsavelApi;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenciaTecnicaValidatorTest {

    @Mock
    private ObraApi obraApi;
    @Mock
    private EquipeApi equipeApi;
    @Mock
    private AgenteResponsavelApi agenteResponsavelApi;
    @Mock
    private ColaboradorApi colaboradorApi;
    @InjectMocks
    private ReferenciaTecnicaValidator validator;

    @Test
    void aceitaObraTecnicaCorretivaEquipeVinculadaEOutroAgenteAtivo() {
        UUID idEquipe = UUID.randomUUID();
        UUID idAgente = UUID.randomUUID();
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of(idEquipe));
        EquipeApi.EquipePublica equipe = new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, obra.idBaseOperacional(), obra.idProcesso(), null, null);
        AgenteResponsavelApi.AgenteResponsavelPublico agente =
                new AgenteResponsavelApi.AgenteResponsavelPublico(
                        idAgente, "Agente Externo", "agente@example.com", null, "CONCESSIONARIA");
        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(equipe));
        when(agenteResponsavelApi.buscarAtivoPorId(idAgente)).thenReturn(Optional.of(agente));
        OcorrenciaPublica ocorrencia = new OcorrenciaPublica(UUID.randomUUID(), obra.id(), "OC-1042");
        when(obraApi.resolverOuCriarOcorrencia(obra.id(), "OC-1042")).thenReturn(ocorrencia);

        var resultado = validator.validar(obra.id(), "OC-1042", idEquipe, idAgente);

        assertThat(resultado.obra()).isSameAs(obra);
        assertThat(resultado.ocorrencia()).isSameAs(ocorrencia);
        assertThat(resultado.equipe()).isSameAs(equipe);
        assertThat(resultado.agenteResponsavel()).isSameAs(agente);
    }

    @Test
    void resolveCadastroComOcorrenciaNormalizada() {
        ObraPublica obra = obra(TipoObraPublico.PREVENTIVA, true, true, Set.of());
        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        OcorrenciaPublica ocorrencia = new OcorrenciaPublica(UUID.randomUUID(), obra.id(), "OS 12345");
        when(obraApi.resolverOuCriarOcorrencia(obra.id(), "OS 12345")).thenReturn(ocorrencia);

        var resultado = validator.validar(obra.id(), "  ｏｓ   12345 ", null, null);

        assertThat(resultado.ocorrencia()).isSameAs(ocorrencia);
        verifyNoInteractions(equipeApi, agenteResponsavelApi);
    }

    @Test
    void rejeitaControleELimiteComoErroDoCampoOcorrencia() {
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of());
        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));

        assertThatThrownBy(() -> validator.validar(obra.id(), "OC-10\n42", null, null))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsKey("ocorrencia"));
        assertThatThrownBy(() -> validator.validar(obra.id(), "x".repeat(101), null, null))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsKey("ocorrencia"));
    }

    @Test
    void rejeitaOcorrenciaAusenteParaCorretiva() {
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of());
        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));

        assertThatThrownBy(() -> validator.validar(obra.id(), " ", null, null))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsKey("ocorrencia"));
    }

    @Test
    void rejeitaOcorrenciaInformadaParaObraComum() {
        ObraPublica obra = obra(TipoObraPublico.OBRA, true, true, Set.of());
        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));

        assertThatThrownBy(() -> validator.validar(obra.id(), "OS-10", null, null))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsEntry(
                                "ocorrencia", "A ocorrencia nao se aplica a obra do tipo OBRA."));
    }

    @Test
    void acumulaMensagensPorCampoParaObraEquipeEAgenteInvalidos() {
        UUID idEquipe = UUID.randomUUID();
        UUID idAgente = UUID.randomUUID();
        ObraPublica obra = obra(TipoObraPublico.PREVENTIVA, false, true, Set.of());
        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.empty());
        when(agenteResponsavelApi.buscarAtivoPorId(idAgente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validar(obra.id(), null, idEquipe, idAgente))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsOnlyKeys(
                                "idObra", "ocorrencia", "idEquipe", "idAgenteResponsavel"));
    }

    @Test
    void obraAusenteNaoConsultaEquipeNemAgenteQuandoNaoInformados() {
        UUID idObra = UUID.randomUUID();
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validar(idObra, null, null, null))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsKey("idObra"));
        verifyNoInteractions(equipeApi, agenteResponsavelApi);
    }

    @Test
    void aceitaColaboradorMembroAtivoDaEquipe() {
        UUID idEquipe = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of(idEquipe));
        EquipeApi.EquipePublica equipe = new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, obra.idBaseOperacional(), obra.idProcesso(), null, null);
        ColaboradorResponse colaborador = new ColaboradorResponse(
                idColaborador, null, "Carlos", "Silva", "Carlos Silva",
                "12345678901", null, null, null, LocalDate.now(), true);

        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        when(obraApi.resolverOuCriarOcorrencia(obra.id(), "OC-1042"))
                .thenReturn(new OcorrenciaPublica(UUID.randomUUID(), obra.id(), "OC-1042"));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(equipe));
        when(colaboradorApi.buscarPorIdResponse(idColaborador)).thenReturn(Optional.of(colaborador));
        when(equipeApi.isMembroAtivo(idEquipe, idColaborador)).thenReturn(true);

        var resultado = validator.validar(obra.id(), "OC-1042", idEquipe, null, idColaborador, false);

        assertThat(resultado.colaboradorSaque()).isEqualTo(colaborador);
        assertThat(resultado.saqueForaEquipe()).isFalse();
    }

    @Test
    void aceitaColaboradorForaDaEquipeQuandoSaqueForaEquipeVerdadeiro() {
        UUID idEquipe = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of(idEquipe));
        EquipeApi.EquipePublica equipe = new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, obra.idBaseOperacional(), obra.idProcesso(), null, null);
        ColaboradorResponse colaborador = new ColaboradorResponse(
                idColaborador, null, "Maria", "Souza", "Maria Souza",
                "98765432100", null, null, null, LocalDate.now(), true);

        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        when(obraApi.resolverOuCriarOcorrencia(obra.id(), "OC-1042"))
                .thenReturn(new OcorrenciaPublica(UUID.randomUUID(), obra.id(), "OC-1042"));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(equipe));
        when(colaboradorApi.buscarPorIdResponse(idColaborador)).thenReturn(Optional.of(colaborador));

        var resultado = validator.validar(obra.id(), "OC-1042", idEquipe, null, idColaborador, true);

        assertThat(resultado.colaboradorSaque()).isEqualTo(colaborador);
        assertThat(resultado.saqueForaEquipe()).isTrue();
    }

    @Test
    void rejeitaColaboradorNaoMembroQuandoSaqueForaEquipeFalso() {
        UUID idEquipe = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of(idEquipe));
        EquipeApi.EquipePublica equipe = new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, obra.idBaseOperacional(), obra.idProcesso(), null, null);
        ColaboradorResponse colaborador = new ColaboradorResponse(
                idColaborador, null, "Maria", "Souza", "Maria Souza",
                "98765432100", null, null, null, LocalDate.now(), true);

        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(equipe));
        when(colaboradorApi.buscarPorIdResponse(idColaborador)).thenReturn(Optional.of(colaborador));
        when(equipeApi.isMembroAtivo(idEquipe, idColaborador)).thenReturn(false);

        assertThatThrownBy(() -> validator.validar(obra.id(), "OC-1042", idEquipe, null, idColaborador, false))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsEntry(
                                "idColaboradorSaque", "O colaborador informado nao e um membro ativo da equipe informada."));
    }

    @Test
    void rejeitaColaboradorInexistenteOuInativo() {
        UUID idEquipe = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        ObraPublica obra = obra(TipoObraPublico.CORRETIVA, true, true, Set.of(idEquipe));
        EquipeApi.EquipePublica equipe = new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, obra.idBaseOperacional(), obra.idProcesso(), null, null);

        when(obraApi.buscarPorId(obra.id())).thenReturn(Optional.of(obra));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(equipe));
        when(colaboradorApi.buscarPorIdResponse(idColaborador)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validar(obra.id(), "OC-1042", idEquipe, null, idColaborador, false))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsEntry(
                                "idColaboradorSaque", "O colaborador responsavel pelo saque deve existir e estar ativo."));
    }

    private static ObraPublica obra(
            TipoObraPublico tipo,
            boolean ativa,
            boolean processoTecnico,
            Set<UUID> equipes
    ) {
        return new ObraPublica(
                UUID.randomUUID(), "001234", "OBRA TESTE", tipo, ativa,
                UUID.randomUUID(), "OBRAS", processoTecnico, UUID.randomUUID(),
                equipes, null, List.of());
    }
}
