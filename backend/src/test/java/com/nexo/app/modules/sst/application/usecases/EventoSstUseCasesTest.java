package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.sst.application.dto.EnvolvidoEventoRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstFilter;
import com.nexo.app.modules.sst.application.dto.EventoSstRequest;
import com.nexo.app.modules.sst.application.dto.MotivoEventoRequest;
import com.nexo.app.modules.sst.application.dto.TratativaEventoRequest;
import com.nexo.app.modules.sst.application.mappers.EventoSstMapper;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.shared.domain.events.EventoSstConcluidoEvent;
import com.nexo.app.shared.domain.events.EventoSstRegistradoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orquestração do registro de eventos: o que é copiado dos cadastros, o que é validado
 * contra eles e o que o caso de uso recusa antes de o domínio ver.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventoSstUseCasesTest {

    private static final UUID ID_ATIVIDADE = UUID.randomUUID();
    private static final UUID ID_GESTOR = UUID.randomUUID();
    private static final UUID ID_SUBSTITUTO = UUID.randomUUID();
    private static final UUID ID_BASE = UUID.randomUUID();
    private static final UUID ID_CLASSIFICACAO = UUID.randomUUID();
    private static final UUID ID_LOCAL_LESAO = UUID.randomUUID();
    private static final UUID ID_TECNICO = UUID.randomUUID();
    private static final UUID ID_ACIDENTADO = UUID.randomUUID();

    @Mock
    private EventoSstRepository repository;
    @Mock
    private AtividadeSstRepository atividadeRepository;
    @Mock
    private CatalogoSstRepository catalogoRepository;
    @Mock
    private EventoSstMapper mapper;
    @Mock
    private ColaboradorApi colaboradorApi;
    @Mock
    private BaseOperacionalApi baseOperacionalApi;
    @Mock
    private EquipeApi equipeApi;
    @Mock
    private VeiculoApi veiculoApi;
    @Mock
    private ProcessoNegocioApi processoApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EventoSstUseCases useCases;

    private AtividadeSst atividade;

    @BeforeEach
    void configurarPadroes() {
        atividade = new AtividadeSst("Detonação", null, ID_GESTOR, null);

        when(atividadeRepository.findById(ID_ATIVIDADE)).thenReturn(Optional.of(atividade));
        when(catalogoRepository.existsByIdAndTipoAndAtivoTrue(any(), any())).thenReturn(true);
        when(catalogoRepository.findAllById(anyCollection())).thenReturn(List.of());
        // Na edição, uma entrada que o evento já usava é revalidada por tipo (e não por
        // "está ativa"): é o que permite corrigir um evento antigo cujo catálogo mudou.
        when(catalogoRepository.findById(ID_CLASSIFICACAO)).thenReturn(Optional.of(
                new CatalogoSst(TipoCatalogoSst.CLASSIFICACAO_EVENTO, "Queda de mesmo nível", null, null, 0)));
        when(catalogoRepository.findById(ID_LOCAL_LESAO)).thenReturn(Optional.of(
                new CatalogoSst(TipoCatalogoSst.LOCAL_LESAO, "Mão direita", null, null, 0)));
        when(baseOperacionalApi.buscarPorIdResponse(ID_BASE))
                .thenReturn(Optional.of(new BaseOperacionalResponse(
                        ID_BASE, "Base Central", null, null, null, null, null, null, true)));
        when(colaboradorApi.buscarResumosPorIds(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<?> ids = invocation.getArgument(0);
            return ids.stream().map(UUID.class::cast)
                    .collect(java.util.stream.Collectors.toMap(id -> id, EventoSstUseCasesTest::resumoDe));
        });
        when(repository.proximoNumeroSequencial()).thenReturn(42L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ResumoColaborador resumoDe(UUID id) {
        if (id.equals(ID_TECNICO)) {
            return new ResumoColaborador(id, "CARLA MENDES", "Técnica de Segurança", "Base Central", true);
        }
        if (id.equals(ID_ACIDENTADO)) {
            return new ResumoColaborador(id, "CARLOS NUNES", "Eletricista", "Base Norte", true);
        }
        if (id.equals(ID_SUBSTITUTO)) {
            return new ResumoColaborador(id, "ANA DIAS", "Coordenadora", "Base Central", true);
        }
        return new ResumoColaborador(id, "MARIA SOUZA", "Supervisora", "Base Central", true);
    }

    // ------------------------------------------------------------------ //
    //  Registro
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Deve copiar atividade, gestor, base e pessoas, e gerar o número legível")
    void deveCongelarContextoNoRegistro() {
        useCases.registrar(request().build());

        EventoSst evento = capturarSalvo();
        assertEquals("EVT-" + OffsetDateTime.now().getYear() + "-000042", evento.getNumeroRegistro());
        assertEquals("Detonação", evento.getAtividadeNome());
        assertEquals("MARIA SOUZA", evento.getGestorNome());
        assertEquals("Supervisora", evento.getGestorFuncao());
        assertEquals("Base Central", evento.getBaseNome());
        assertFalse(evento.isGestorSubstituto());
        assertEquals(StatusEventoSst.REGISTRADO, evento.getStatus());
        assertEquals("CARLA MENDES", evento.getTecnicos().getFirst().getNome());
    }

    @Test
    @DisplayName("Deve copiar o retrato do colaborador do RH, ignorando o nome mandado no payload")
    void deveCopiarDadosDoRh() {
        useCases.registrar(request()
                .envolvidos(List.of(new EnvolvidoEventoRequest(TipoEnvolvido.COLABORADOR, PapelEnvolvido.ACIDENTADO,
                        ID_ACIDENTADO, "NOME QUE O CLIENTE INVENTOU", "Função inventada", null)))
                .build());

        EventoSst evento = capturarSalvo();
        assertEquals("CARLOS NUNES", evento.getEnvolvidos().getFirst().getNome());
        assertEquals("Eletricista", evento.getEnvolvidos().getFirst().getFuncao());
        assertEquals("Base Norte", evento.getEnvolvidos().getFirst().getBaseOperacional());
    }

    @Test
    @DisplayName("Deve marcar como substituto o gestor informado diferente do da atividade")
    void deveMarcarGestorSubstituto() {
        useCases.registrar(request().idGestor(ID_SUBSTITUTO).build());

        EventoSst evento = capturarSalvo();
        assertEquals(ID_SUBSTITUTO, evento.getIdGestor());
        assertEquals("ANA DIAS", evento.getGestorNome());
        assertTrue(evento.isGestorSubstituto());
        assertEquals(ID_GESTOR, atividade.getIdGestor(), "o cadastro da atividade não é alterado");
    }

    @Test
    @DisplayName("Não deve marcar substituto quando o gestor informado é o próprio da atividade")
    void naoDeveMarcarSubstitutoQuandoIgual() {
        useCases.registrar(request().idGestor(ID_GESTOR).build());

        assertFalse(capturarSalvo().isGestorSubstituto());
    }

    @Test
    @DisplayName("Não deve marcar substituto quando a atividade não tem gestor titular")
    void naoDeveMarcarSubstitutoSemTitular() {
        when(atividadeRepository.findById(ID_ATIVIDADE))
                .thenReturn(Optional.of(new AtividadeSst("Trajeto", null, null, null)));

        useCases.registrar(request().idGestor(ID_SUBSTITUTO).build());

        EventoSst evento = capturarSalvo();
        assertEquals(ID_SUBSTITUTO, evento.getIdGestor());
        assertFalse(evento.isGestorSubstituto(), "sem titular não há quem substituir");
    }

    @Test
    @DisplayName("Deve exigir gestor quando a atividade não tem — a exigência muda de lugar, não some")
    void deveExigirGestorQuandoAtividadeNaoTem() {
        when(atividadeRepository.findById(ID_ATIVIDADE))
                .thenReturn(Optional.of(new AtividadeSst("Trajeto", null, null, null)));

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(request().build()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar entrada de catálogo de outro vocabulário")
    void deveRecusarCatalogoDeOutroTipo() {
        when(catalogoRepository.existsByIdAndTipoAndAtivoTrue(ID_CLASSIFICACAO, TipoCatalogoSst.CLASSIFICACAO_EVENTO))
                .thenReturn(false);

        InvarianteVioladaException erro = assertThrows(InvarianteVioladaException.class,
                () -> useCases.registrar(request().build()));

        assertTrue(erro.getMessage().contains("classificação do evento"));
    }

    @Test
    @DisplayName("Deve recusar atividade desativada em um evento novo")
    void deveRecusarAtividadeDesativada() {
        AtividadeSst desativada = new AtividadeSst("Poda", null, ID_GESTOR, null);
        desativada.desativar();
        when(atividadeRepository.findById(ID_ATIVIDADE)).thenReturn(Optional.of(desativada));

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(request().build()));
    }

    @Test
    @DisplayName("Deve recusar base operacional inexistente")
    void deveRecusarBaseInexistente() {
        when(baseOperacionalApi.buscarPorIdResponse(ID_BASE)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.registrar(request().build()));
    }

    @Test
    @DisplayName("Deve recusar técnico de segurança inativo no RH")
    void deveRecusarTecnicoInativo() {
        when(colaboradorApi.buscarResumosPorIds(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<?> ids = invocation.getArgument(0);
            return ids.stream().map(UUID.class::cast).collect(java.util.stream.Collectors.toMap(
                    id -> id,
                    id -> id.equals(ID_TECNICO)
                            ? new ResumoColaborador(id, "CARLA MENDES", "Técnica de Segurança", "Base Central", false)
                            : resumoDe(id)));
        });

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(request().build()));
    }

    @Test
    @DisplayName("Deve publicar o evento de registro sem nome de pessoa no contexto")
    void devePublicarEventoDeRegistro() {
        useCases.registrar(request().build());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());

        EventoSstRegistradoEvent publicado = (EventoSstRegistradoEvent) captor.getValue();
        assertEquals("Detonação", publicado.atividade());
        assertEquals("Base Central", publicado.base());
        assertTrue(publicado.comLesao());

        Map<String, Object> contexto = publicado.contexto();
        assertFalse(contexto.containsValue("CARLOS NUNES"), "nome de acidentado não vai para a notificação");
        assertFalse(contexto.containsValue("MARIA SOUZA"), "nome de gestor não vai para a notificação");
    }

    // ------------------------------------------------------------------ //
    //  Ciclo de vida
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Deve concluir com tratativa, gravar por dirty checking e publicar a conclusão")
    void deveConcluir() {
        EventoSst evento = registrado();
        when(repository.findOne(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(Optional.of(evento));

        useCases.concluir(evento.getId(), new TratativaEventoRequest("Área isolada e equipe treinada"));

        assertEquals(StatusEventoSst.CONCLUIDO, evento.getStatus());
        verify(repository).flush();
        // save() em agregado gerenciado cai em merge() e devolveria cópias dos filhos.
        verify(repository, never()).save(any());
        verify(eventPublisher).publishEvent(any(EventoSstConcluidoEvent.class));
    }

    @Test
    @DisplayName("Deve recusar edição de evento concluído")
    void deveRecusarEdicaoDeConcluido() {
        EventoSst evento = registrado();
        evento.concluir("Tratado");
        when(repository.findOne(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(Optional.of(evento));

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.atualizar(evento.getId(), request().build()));
    }

    @Test
    @DisplayName("Deve exigir motivo no cancelamento")
    void deveExigirMotivoNoCancelamento() {
        EventoSst evento = registrado();
        when(repository.findOne(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(Optional.of(evento));

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.cancelar(evento.getId(), new MotivoEventoRequest("   ")));
    }

    @Test
    @DisplayName("Deve recusar operação sobre evento inexistente")
    void deveRecusarEventoInexistente() {
        when(repository.findOne(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.buscarPorId(UUID.randomUUID()));
    }

    // ------------------------------------------------------------------ //
    //  Listagem
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Deve resolver o trecho do nome de colaborador em ids dentro do RH")
    void deveResolverNomeDeColaboradorNoRh() {
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(colaboradorApi.buscarIdsPorTrechoDoNome("carlos")).thenReturn(List.of(ID_ACIDENTADO));

        useCases.listar(filtroPorNome("carlos"), Pageable.unpaged());

        verify(colaboradorApi).buscarIdsPorTrechoDoNome("carlos");
    }

    @Test
    @DisplayName("Não deve consultar o RH quando não há filtro por nome")
    void naoDeveConsultarRhSemFiltroDeNome() {
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        useCases.listar(filtroPorNome(null), Pageable.unpaged());

        verify(colaboradorApi, never()).buscarIdsPorTrechoDoNome(any());
    }

    // ------------------------------------------------------------------ //
    //  Apoio
    // ------------------------------------------------------------------ //

    private EventoSst capturarSalvo() {
        ArgumentCaptor<EventoSst> captor = ArgumentCaptor.forClass(EventoSst.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    /**
     * Agregado já registrado, montado direto pelo domínio — as verificações de ciclo de
     * vida não devem depender de uma chamada anterior ao próprio caso de uso.
     */
    private EventoSst registrado() {
        return EventoSst.registrar("EVT-2026-000042",
                new com.nexo.app.modules.sst.domain.DadosEventoSst(
                        TipoEventoSst.ACIDENTE_TIPICO,
                        OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                        ID_CLASSIFICACAO, null, RegistroEvidencia.SIM, RegistroEvidencia.NAO,
                        "Trecho urbano da rede", null, null, "Colaborador sofreu queda durante a atividade.",
                        ID_LOCAL_LESAO, null, null, 3, false, null, null, null),
                new com.nexo.app.modules.sst.domain.SnapshotAtividade(
                        ID_ATIVIDADE, "Detonação", ID_GESTOR, "MARIA SOUZA", "Supervisora", false),
                new com.nexo.app.modules.sst.domain.SnapshotOperacional(
                        ID_BASE, "Base Central", null, null, null, null),
                List.of(new com.nexo.app.modules.sst.domain.SnapshotTecnico(
                        ID_TECNICO, "CARLA MENDES", "Técnica de Segurança")),
                List.of(com.nexo.app.modules.sst.domain.SnapshotEnvolvido.colaborador(
                        PapelEnvolvido.ACIDENTADO, ID_ACIDENTADO, "CARLOS NUNES", "Eletricista", "Base Norte")));
    }

    private static EventoSstFilter filtroPorNome(String nome) {
        return new EventoSstFilter(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, nome, null, null);
    }

    private RequestBuilder request() {
        return new RequestBuilder();
    }

    /** Só o que varia por teste é escrito; o resto é um acidente típico bem formado. */
    private static final class RequestBuilder {

        private UUID idGestor;
        private List<EnvolvidoEventoRequest> envolvidos = List.of(
                new EnvolvidoEventoRequest(TipoEnvolvido.COLABORADOR, PapelEnvolvido.ACIDENTADO,
                        ID_ACIDENTADO, null, null, null));

        RequestBuilder idGestor(UUID valor) {
            this.idGestor = valor;
            return this;
        }

        RequestBuilder envolvidos(List<EnvolvidoEventoRequest> valor) {
            this.envolvidos = valor;
            return this;
        }

        EventoSstRequest build() {
            return new EventoSstRequest(
                    TipoEventoSst.ACIDENTE_TIPICO,
                    OffsetDateTime.now().minusHours(5),
                    OffsetDateTime.now().minusHours(4),
                    ID_ATIVIDADE, idGestor, ID_BASE, null, null,
                    ID_CLASSIFICACAO, null,
                    RegistroEvidencia.SIM, RegistroEvidencia.NAO,
                    "Trecho urbano da rede", null, null,
                    "Colaborador sofreu queda durante a atividade.",
                    ID_LOCAL_LESAO, null, null, 3,
                    false, null, null, null,
                    List.of(ID_TECNICO),
                    envolvidos);
        }
    }
}
