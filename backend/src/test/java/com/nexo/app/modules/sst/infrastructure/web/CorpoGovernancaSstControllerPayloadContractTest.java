package com.nexo.app.modules.sst.infrastructure.web;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstUpdateRequest;
import com.nexo.app.modules.sst.application.mappers.BaseAbrangidaCorpoSstMapper;
import com.nexo.app.modules.sst.application.mappers.CorpoGovernancaSstMapper;
import com.nexo.app.modules.sst.application.mappers.MembroCorpoSstMapper;
import com.nexo.app.modules.sst.application.usecases.CorpoGovernancaSstUseCases;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.EscopoPerfilNormativoSst;
import com.nexo.app.modules.sst.domain.PerfilNormativoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CorpoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.MembroCorpoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.PerfilNormativoGovernancaSstRepository;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.Paged;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorpoGovernancaSstControllerPayloadContractTest {

    private static final UUID BASE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PERFIL = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final String ENDPOINT = "/v1/sst/governanca/corpos";

    private CorpoGovernancaSstRepository repository;
    private PerfilNormativoGovernancaSstRepository perfilRepository;
    private BaseOperacionalApi baseApi;
    private CorpoGovernancaSstMapper mapper;
    private MockMvc mockMvc;
    private ValidatorFactory validatorFactory;

    @BeforeEach
    void configurar() {
        repository = mock(CorpoGovernancaSstRepository.class);
        MembroCorpoSstRepository membroRepository = mock(MembroCorpoSstRepository.class);
        perfilRepository = mock(PerfilNormativoGovernancaSstRepository.class);
        baseApi = mock(BaseOperacionalApi.class);
        ColaboradorApi colaboradorApi = mock(ColaboradorApi.class);
        mapper = mock(CorpoGovernancaSstMapper.class);
        MembroCorpoSstMapper membroMapper = mock(MembroCorpoSstMapper.class);
        BaseAbrangidaCorpoSstMapper baseMapper = mock(BaseAbrangidaCorpoSstMapper.class);
        CorpoGovernancaSstUseCases useCases = new CorpoGovernancaSstUseCases(
                repository, membroRepository, perfilRepository, baseApi, colaboradorApi,
                mapper, membroMapper, baseMapper);

        validatorFactory = Validation.buildDefaultValidatorFactory();
        mockMvc = MockMvcBuilders.standaloneSetup(new CorpoGovernancaSstController(useCases))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .build();
    }

    @AfterEach
    void fecharValidador() {
        validatorFactory.close();
    }

    @Test
    void cipaDimensionadaAceitaConfirmacaoFalseEPeriodoAnualExato() throws Exception {
        prepararBase("BA");
        ObjectNode payload = payload("CIPA", "CIPA_DIMENSIONADA",
                "2026-08-01", "2027-07-31", 2, false, null);
        payload.put("uf", "ba");

        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(payload.toString()))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(CorpoGovernancaSst.class);
        verify(repository).save(captor.capture());
        assertFalse(captor.getValue().isHipoteseRepresentanteNomeadoConfirmada());
        assertEquals("BA", captor.getValue().getUf());
        assertEquals(LocalDate.of(2027, 7, 31), captor.getValue().getFimPeriodo());
    }

    @Test
    void representanteNomeadoAceitaConfirmacaoTrueComPerfilNacionalConfirmado() throws Exception {
        prepararBase("BA");
        PerfilNormativoGovernancaSst perfil = perfilNacionalConfirmado();
        when(perfilRepository.findByIdAndAtivoTrue(PERFIL)).thenReturn(Optional.of(perfil));

        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(payload("CIPA", "REPRESENTANTE_NOMEADO",
                                "2026-08-01", "2027-07-31", 2, true, PERFIL).toString()))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(CorpoGovernancaSst.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().isHipoteseRepresentanteNomeadoConfirmada());
        assertEquals(PERFIL, captor.getValue().getIdPerfilNormativo());
    }

    @Test
    void brigadaAceitaRegimeComposicaoNulo() throws Exception {
        prepararBase("BA");

        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(payload("BRIGADA", null,
                                "2026-08-01", "2026-08-24", 3, false, null).toString()))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(CorpoGovernancaSst.class);
        verify(repository).save(captor.capture());
        assertEquals(TipoCorpoGovernancaSst.BRIGADA, captor.getValue().getTipo());
        assertNull(captor.getValue().getRegimeComposicao());
    }

    @Test
    void booleanoAusenteOuNuloRetornaErroDeValidacaoAntesDoCasoDeUso() throws Exception {
        ObjectNode ausente = payload("CIPA", "CIPA_DIMENSIONADA",
                "2026-08-01", "2027-07-31", 2, false, null);
        ausente.remove("hipoteseRepresentanteNomeadoConfirmada");
        ObjectNode nulo = ausente.deepCopy();
        nulo.putNull("hipoteseRepresentanteNomeadoConfirmada");

        executarComErroDeValidacao(ausente);
        executarComErroDeValidacao(nulo);

        verifyNoInteractions(baseApi);
        verify(repository, never()).save(any());
    }

    @Test
    void grauDeRiscoAusenteOuNuloRetornaErroDeValidacao() throws Exception {
        ObjectNode ausente = payload("CIPA", "CIPA_DIMENSIONADA",
                "2026-08-01", "2027-07-31", 2, false, null);
        ausente.remove("grauRisco");
        ObjectNode nulo = ausente.deepCopy();
        nulo.putNull("grauRisco");

        executarComErroDeValidacao(ausente);
        executarComErroDeValidacao(nulo);

        verifyNoInteractions(baseApi);
    }

    @Test
    void cipaRecusaPeriodoParcialMesmoComBooleanoValido() throws Exception {
        prepararBase("BA");

        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(payload("CIPA", "CIPA_DIMENSIONADA",
                                "2026-08-01", "2026-08-24", 2, false, null).toString()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail")
                        .value("O período da CIPA deve representar um mandato anual."));

        verify(repository, never()).save(any());
    }

    @Test
    void ufInvalidaOuDivergenteDaBaseERecusada() throws Exception {
        ObjectNode invalida = payload("CIPA", "CIPA_DIMENSIONADA",
                "2026-08-01", "2027-07-31", 2, false, null);
        invalida.put("uf", "ZZ");
        executarComErroDeValidacao(invalida);

        prepararBase("PE");
        ObjectNode divergente = payload("CIPA", "CIPA_DIMENSIONADA",
                "2026-08-01", "2027-07-31", 2, false, null);
        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(divergente.toString()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail")
                        .value("A UF informada diverge da UF cadastrada para a base operacional."));
    }

    @Test
    void contratosDeCriacaoEAtualizacaoUsamBooleanBoxedECamposObrigatorios() throws Exception {
        assertEquals(Boolean.class, com.nexo.app.modules.sst.application.dto
                .CorpoGovernancaSstRequest.class.getRecordComponents()[10].getType());
        assertEquals(Boolean.class, CorpoGovernancaSstUpdateRequest.class
                .getRecordComponents()[8].getType());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var update = new CorpoGovernancaSstUpdateRequest(
                    0L, "CIPA", "Estabelecimento", "BA",
                    LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                    null, null, null);
            var campos = validator.validate(update).stream()
                    .map(violacao -> violacao.getPropertyPath().toString()).toList();
            assertTrue(campos.contains("grauRisco"));
            assertTrue(campos.contains("hipoteseRepresentanteNomeadoConfirmada"));
        }
        assertNotNull(CorpoGovernancaSstUpdateRequest.class.getRecordComponents()[8]);
    }

    @Test
    void queryDoFrontendMantemSomenteOTipoComoFiltroAtivo() throws Exception {
        CorpoGovernancaSstUseCases useCases = mock(CorpoGovernancaSstUseCases.class);
        when(useCases.listar(any(), any(Pageable.class))).thenReturn(
                new PageResponse<>(List.of(), new Paged(10, 0, 0, 0)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CorpoGovernancaSstController(useCases))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mvc.perform(get(ENDPOINT)
                        .queryParam("tipo", "CIPA")
                        .queryParam("tiposCorpo", "CIPA")
                        .queryParam("tipoCorpo", "CIPA")
                        .queryParam("contexto", "CIPA")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sort", "dataCriacao,desc"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(CorpoGovernancaSstFilter.class);
        verify(useCases).listar(captor.capture(), any(Pageable.class));
        CorpoGovernancaSstFilter filter = captor.getValue();
        assertEquals(List.of(TipoCorpoGovernancaSst.CIPA), filter.tipo());
        assertTrue(filter.status().isEmpty());
        assertTrue(filter.regimeComposicao().isEmpty());
        assertTrue(filter.idBaseOperacional().isEmpty());
    }

    private void prepararBase(String uf) {
        when(baseApi.buscarPorIdsResponse(any())).thenReturn(Map.of(
                BASE, new BaseOperacionalResponse(BASE, "JUAZEIRO", uf,
                        "Endereço cadastrado", null, null, null, null, true)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void executarComErroDeValidacao(ObjectNode payload) throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content(payload.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Campos Inválidos"));
    }

    private static ObjectNode payload(String tipo, String regime, String inicio, String fim,
                                      Integer grau, boolean hipotese, UUID perfil) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("tipo", tipo);
        if (regime == null) json.putNull("regimeComposicao");
        else json.put("regimeComposicao", regime);
        json.put("nome", tipo + " 2026");
        json.put("idBaseOperacional", BASE.toString());
        json.put("unidadeGovernanca", "Unidade Juazeiro");
        json.put("uf", "BA");
        json.put("inicioPeriodo", inicio);
        json.put("fimPeriodo", fim);
        if (grau == null) json.putNull("grauRisco");
        else json.put("grauRisco", grau);
        if (perfil == null) json.putNull("idPerfilNormativo");
        else json.put("idPerfilNormativo", perfil.toString());
        json.put("hipoteseRepresentanteNomeadoConfirmada", hipotese);
        json.putArray("idsBasesAbrangidas");
        return json;
    }

    private static PerfilNormativoGovernancaSst perfilNacionalConfirmado() {
        PerfilNormativoGovernancaSst perfil = new PerfilNormativoGovernancaSst(
                "NR-5", 1, EscopoPerfilNormativoSst.NACIONAL,
                TipoCorpoGovernancaSst.CIPA, "BR", "MTE", "NR-5", "2023",
                LocalDate.of(2023, 3, 20), null,
                "https://www.gov.br/trabalho-e-emprego/nr-5", Map.of());
        ReflectionTestUtils.setField(perfil, "id", PERFIL);
        perfil.confirmar(UUID.randomUUID());
        return perfil;
    }
}
