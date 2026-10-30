package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpoGovernancaSstTest {

    private static final UUID BASE = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 12, 31);

    @Test
    @DisplayName("CIPA dimensionada ativa sem secretário permanente quando presidente e vice são válidos")
    void deveAtivarCipaDimensionadaSemSecretarioPermanente() {
        CorpoGovernancaSst corpo = cipaDimensionada();
        adicionarPresidenteEVice(corpo);

        corpo.ativar(null, UUID.randomUUID());

        assertEquals(StatusCorpoGovernancaSst.ATIVO, corpo.getStatus());
        assertFalse(corpo.isConformidadeNormativaValidada());
        assertTrue(java.util.Arrays.stream(PapelMembroGovernancaSst.values())
                .noneMatch(papel -> papel.name().contains("SECRET")));
    }

    @Test
    @DisplayName("Presidente dos empregados é recusado no ingresso")
    void deveRecusarPresidenteComRepresentacaoInvalida() {
        CorpoGovernancaSst corpo = cipaDimensionada();

        assertThrows(InvarianteVioladaException.class, () -> corpo.adicionarMembro(
                snapshot(UUID.randomUUID()), PapelMembroGovernancaSst.PRESIDENTE,
                RepresentacaoCipa.EMPREGADOS, TitularidadeCipa.TITULAR,
                OrigemMembroCipa.ELEITO, "ELEICAO-2026", 1,
                null, false, INICIO));
    }

    @Test
    @DisplayName("Representante nomeado exige hipótese e perfil nacional confirmado")
    void deveExigirFonteConfirmadaParaRepresentanteNomeado() {
        PerfilNormativoGovernancaSst perfil = perfilNacionalCipa();
        UUID idPerfil = UUID.randomUUID();
        ReflectionTestUtils.setField(perfil, "id", idPerfil);
        perfil.confirmar(UUID.randomUUID());
        CorpoGovernancaSst corpo = new CorpoGovernancaSst(
                TipoCorpoGovernancaSst.CIPA, RegimeComposicaoSst.REPRESENTANTE_NOMEADO,
                "Representante 2026", BASE, "Base Salvador", "Estabelecimento Salvador",
                "BA", INICIO, FIM, 2, idPerfil, true);
        corpo.adicionarMembro(snapshot(UUID.randomUUID()),
                PapelMembroGovernancaSst.REPRESENTANTE_NOMEADO,
                RepresentacaoCipa.EMPREGADOS, null, OrigemMembroCipa.DESIGNADO,
                null, null, "ATO-NR5-2026", false, INICIO);

        corpo.ativar(perfil, UUID.randomUUID());

        assertEquals(StatusCorpoGovernancaSst.ATIVO, corpo.getStatus());
        assertTrue(corpo.isConformidadeNormativaValidada());
        assertEquals("NR-5", corpo.getPerfilCodigoSnapshot());
    }

    @Test
    @DisplayName("Brigada sem perfil estadual pode registrar fatos sem declarar conformidade")
    void deveAtivarBrigadaFactualSemDeclararConformidade() {
        CorpoGovernancaSst corpo = new CorpoGovernancaSst(
                TipoCorpoGovernancaSst.BRIGADA, null, "Brigada prédio A", BASE,
                "Base Salvador", "Edificação A", "BA", INICIO, FIM, 2,
                null, false);
        corpo.adicionarMembro(snapshot(UUID.randomUUID()), PapelMembroGovernancaSst.BRIGADISTA,
                null, null, null, null, null, null, false, INICIO);

        corpo.ativar(null, UUID.randomUUID());

        assertEquals(StatusCorpoGovernancaSst.ATIVO, corpo.getStatus());
        assertFalse(corpo.isConformidadeNormativaValidada());
        assertEquals("SEM_PERFIL_CONFIRMADO", corpo.getCodigoAvaliacaoNormativa());
    }

    @Test
    @DisplayName("Saída de CIPA ativa abre vacância e substituição fundamentada a resolve")
    void devePreservarSaidaEVincularSubstituicao() {
        CorpoGovernancaSst corpo = cipaDimensionada();
        MembroCorpoSst presidente = adicionarPresidenteEVice(corpo);
        corpo.ativar(null, UUID.randomUUID());
        LocalDate efeito = LocalDate.of(2026, 5, 10);

        corpo.registrarSaida(presidente, efeito, "Término do vínculo", UUID.randomUUID());

        assertFalse(presidente.isParticipacaoAtiva());
        assertTrue(presidente.isVacanciaPendente());
        MembroCorpoSst sucessor = corpo.substituir(
                presidente, snapshot(UUID.randomUUID()), PapelMembroGovernancaSst.PRESIDENTE,
                RepresentacaoCipa.EMPREGADOR, TitularidadeCipa.TITULAR,
                OrigemMembroCipa.DESIGNADO, null, null, "ATO-SUB-2026", false,
                efeito, "Saída já registrada", "Ato de designação substitutiva",
                UUID.randomUUID());

        assertFalse(presidente.isVacanciaPendente());
        assertEquals(sucessor, presidente.getMembroSubstituto());
        assertEquals(presidente, sucessor.getMembroSubstituido());
        assertTrue(sucessor.isParticipacaoAtiva());
    }

    @Test
    @DisplayName("CIPA não encerra antecipadamente por conveniência")
    void deveRestringirEncerramentoAntecipadoDaCipa() {
        CorpoGovernancaSst corpo = cipaDimensionada();
        adicionarPresidenteEVice(corpo);
        corpo.ativar(null, UUID.randomUUID());

        assertThrows(InvarianteVioladaException.class, () -> corpo.encerrar(
                LocalDate.of(2026, 6, 1), MotivoEncerramentoCorpoSst.FIM_DO_PERIODO,
                "Reorganização", null, UUID.randomUUID()));

        corpo.encerrar(LocalDate.of(2026, 6, 1),
                MotivoEncerramentoCorpoSst.ENCERRAMENTO_ESTABELECIMENTO,
                "Encerramento definitivo", "PROCESSO-ADMIN-123", UUID.randomUUID());
        assertEquals(StatusCorpoGovernancaSst.ENCERRADO, corpo.getStatus());
        assertNotNull(corpo.getDataEncerramento());
    }

    private static CorpoGovernancaSst cipaDimensionada() {
        return new CorpoGovernancaSst(
                TipoCorpoGovernancaSst.CIPA, RegimeComposicaoSst.CIPA_DIMENSIONADA,
                "CIPA 2026", BASE, "Base Salvador", "Estabelecimento Salvador",
                "BA", INICIO, FIM, 2, null, false);
    }

    private static MembroCorpoSst adicionarPresidenteEVice(CorpoGovernancaSst corpo) {
        MembroCorpoSst presidente = corpo.adicionarMembro(snapshot(UUID.randomUUID()),
                PapelMembroGovernancaSst.PRESIDENTE, RepresentacaoCipa.EMPREGADOR,
                TitularidadeCipa.TITULAR, OrigemMembroCipa.DESIGNADO,
                null, null, "ATO-PRES-2026", false, INICIO);
        corpo.adicionarMembro(snapshot(UUID.randomUUID()),
                PapelMembroGovernancaSst.VICE_PRESIDENTE, RepresentacaoCipa.EMPREGADOS,
                TitularidadeCipa.TITULAR, OrigemMembroCipa.ELEITO,
                "ELEICAO-2026", 1, null, false, INICIO);
        return presidente;
    }

    private static SnapshotMembroGovernancaSst snapshot(UUID id) {
        return new SnapshotMembroGovernancaSst(id, "Nome do colaborador", UUID.randomUUID(),
                "Eletricista", BASE, "Base Salvador", "Diurno");
    }

    private static PerfilNormativoGovernancaSst perfilNacionalCipa() {
        return new PerfilNormativoGovernancaSst(
                "NR-5", 1, EscopoPerfilNormativoSst.NACIONAL,
                TipoCorpoGovernancaSst.CIPA, "BR", "MTE", "NR-5",
                "2023", LocalDate.of(2023, 1, 1), null,
                "https://www.gov.br/trabalho-e-emprego/nr-5", Map.of());
    }
}
