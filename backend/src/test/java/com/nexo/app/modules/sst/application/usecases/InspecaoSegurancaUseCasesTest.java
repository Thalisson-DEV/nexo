package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.sst.application.dto.AtualizarInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.RegistrarInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.RegistrarInspecaoRequest.DesvioItem;
import com.nexo.app.modules.sst.application.dto.RegistrarInspecaoRequest.RespostaItem;
import com.nexo.app.modules.sst.domain.*;
import com.nexo.app.modules.sst.application.mappers.TipoInspecaoMapper;
import com.nexo.app.modules.sst.application.services.GravadorEventoInspecaoService;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoInspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspecaoSegurancaUseCasesTest {

    @Mock private InspecaoSegurancaRepository repository;
    @Mock private FormularioInspecaoRepository formularioRepository;
    @Mock private TipoInspecaoRepository tipoInspecaoRepository;
    @Mock private TipoInspecaoMapper tipoInspecaoMapper;
    @Mock private EvidenciaInspecaoRepository evidenciaRepository;
    @Mock private ProcessoNegocioApi processoApi;
    @Mock private VeiculoApi veiculoApi;
    @Mock private EquipeApi equipeApi;
    @Mock private BaseOperacionalApi baseOperacionalApi;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private UsuarioApi usuarioApi;
    @Mock private GravadorEventoInspecaoService gravadorEventoService;
    @Mock private EventoInspecaoSegurancaRepository eventoRepository;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private InspecaoSegurancaUseCases useCases;

    private final UUID idFormulario = UUID.randomUUID();
    private final UUID idTipoInspecao = UUID.randomUUID();
    private final UUID idPergunta = UUID.randomUUID();
    private final UUID idOpcaoSim = UUID.randomUUID();   // geraDesvio = false
    private final UUID idOpcaoNao = UUID.randomUUID();   // geraDesvio = true
    private final UUID idProcesso = UUID.randomUUID();
    private final UUID idBaseOperacional = UUID.randomUUID();
    private final UUID colabA = UUID.randomUUID();
    private final UUID inspetor = UUID.randomUUID();
    private final UUID idEvidencia = UUID.randomUUID();

    private FormularioInspecao formularioComPergunta() {
        FormularioInspecao form = new FormularioInspecao("Inspeção de Campo", "Checklist");
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");
        Pergunta pergunta = grupo.adicionarPergunta("A câmera estava conservada?", TipoResposta.MULTIPLA_ESCOLHA, true);
        OpcaoResposta sim = pergunta.adicionarOpcao("Sim", false, null);
        OpcaoResposta nao = pergunta.adicionarOpcao("Não", true, GravidadeDesvio.CRITICA);
        setId(form, idFormulario);
        setId(grupo, UUID.randomUUID());
        setId(pergunta, idPergunta);
        setId(sim, idOpcaoSim);
        setId(nao, idOpcaoNao);
        return form;
    }

    private void setId(BaseEntity entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    @Test
    void buscarPorIdIgnoraInspecaoDesativada() {
        UUID idInspecao = UUID.randomUUID();
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.buscarPorId(idInspecao));
        verify(repository, never()).findById(idInspecao);
    }

    private RegistrarInspecaoRequest request(List<UUID> colaboradores, List<RespostaItem> respostas, List<DesvioItem> desvios) {
        when(baseOperacionalApi.existeBase(idBaseOperacional)).thenReturn(true);
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.of(evidenciaPendente(inspetor)));
        TipoInspecao tipoCampo = new TipoInspecao("Campo", "Inspeções em campo", 1);
        setId(tipoCampo, idTipoInspecao);
        when(tipoInspecaoRepository.findById(idTipoInspecao)).thenReturn(Optional.of(tipoCampo));
        when(tipoInspecaoRepository.findByNomeNormalizadoAndAtivoTrue("campo")).thenReturn(Optional.of(tipoCampo));
        return new RegistrarInspecaoRequest(
                idFormulario, idTipoInspecao, idProcesso, null, null, idBaseOperacional,
                OffsetDateTime.now(), "obs", null, null,
                colaboradores, respostas, desvios, List.of(idEvidencia));
    }

    /** Foto já enviada pelo inspetor e ainda não usada em nenhuma inspeção. */
    private EvidenciaInspecao evidenciaPendente(UUID idUsuarioEnvio) {
        EvidenciaInspecao evidencia = new EvidenciaInspecao(
                idUsuarioEnvio, "foto.jpg", "image/jpeg", 3, "sst/inspecoes/evidencias/" + idEvidencia + ".jpg");
        setId(evidencia, idEvidencia);
        return evidencia;
    }

    @Test
    @DisplayName("registrar deve falhar quando o formulário não existe")
    void registrarFormularioInexistente() {
        when(formularioRepository.findById(any())).thenReturn(Optional.empty());

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(), List.of());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve falhar quando um colaborador inspecionado não existe")
    void registrarColaboradorInexistente() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(false);

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(), List.of());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve falhar quando uma pergunta obrigatória não é respondida")
    void registrarPerguntaObrigatoriaNaoRespondida() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(), List.of());

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve rejeitar desvio em opção que não está configurada para gerar desvio")
    void registrarDesvioEmOpcaoSemGeraDesvio() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        RespostaItem resposta = new RespostaItem(idPergunta, List.of(idOpcaoSim), null, null, null);
        DesvioItem desvio = new DesvioItem(idPergunta, idOpcaoSim, "x", "y", null, List.of(colabA));

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(resposta), List.of(desvio));

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve rejeitar desvio atribuído a colaborador fora dos inspecionados")
    void registrarDesvioColaboradorForaDosInspecionados() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        UUID colabForaDaLista = UUID.randomUUID();
        RespostaItem resposta = new RespostaItem(idPergunta, List.of(idOpcaoNao), null, null, null);
        DesvioItem desvio = new DesvioItem(idPergunta, idOpcaoNao, "x", "y", null, List.of(colabForaDaLista));

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(resposta), List.of(desvio));

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve persistir a inspeção concluída com respostas e desvios válidos")
    void registrarComSucesso() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RespostaItem resposta = new RespostaItem(idPergunta, List.of(idOpcaoNao), null, null, null);
        DesvioItem desvio = new DesvioItem(idPergunta, idOpcaoNao, "Câmera danificada", "Substituir", null, List.of(colabA));

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(resposta), List.of(desvio));

        useCases.registrar(req, inspetor);

        ArgumentCaptor<InspecaoSeguranca> captor = ArgumentCaptor.forClass(InspecaoSeguranca.class);
        verify(repository).save(captor.capture());

        InspecaoSeguranca salva = captor.getValue();
        assertEquals(StatusInspecao.CONCLUIDA, salva.getStatus());
        assertEquals(inspetor, salva.getIdInspetor());
        assertEquals(1, salva.getColaboradoresInspecionados().size());
        assertEquals(1, salva.getRespostas().size());
        assertEquals(1, salva.getDesvios().size());
        // gravidade derivada da opção que gerou o desvio (pré-definida no formulário), não do request
        assertEquals(GravidadeDesvio.CRITICA, salva.getDesvios().get(0).getGravidade());
        assertEquals(1, salva.getDesvios().get(0).getColaboradores().size());
        assertEquals(1, salva.getEvidencias().size());
    }

    @Test
    @DisplayName("registrar deve falhar quando a evidência informada não existe")
    void registrarEvidenciaInexistente() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(), List.of());
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve recusar evidência enviada por outro usuário")
    void registrarEvidenciaDeOutroUsuario() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(), List.of());
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.of(evidenciaPendente(UUID.randomUUID())));

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve recusar evidência já utilizada em outra inspeção")
    void registrarEvidenciaJaUtilizada() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        EvidenciaInspecao jaUsada = evidenciaPendente(inspetor);
        InspecaoSeguranca outra = new InspecaoSeguranca(
                idFormulario, idTipoInspecao, idProcesso, inspetor, null, null, idBaseOperacional, OffsetDateTime.now(), null);
        outra.vincularEvidencia(jaUsada);

        RegistrarInspecaoRequest req = request(List.of(colabA), List.of(), List.of());
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.of(jaUsada));

        assertThrows(InvarianteVioladaException.class, () -> useCases.registrar(req, inspetor));
    }

    @Test
    @DisplayName("registrar deve ignorar IDs de evidência repetidos no mesmo lançamento")
    void registrarEvidenciaRepetidaNoMesmoRequest() {
        when(formularioRepository.findById(any())).thenReturn(Optional.of(formularioComPergunta()));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(baseOperacionalApi.existeBase(idBaseOperacional)).thenReturn(true);
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.of(evidenciaPendente(inspetor)));
        TipoInspecao tipoCampo = new TipoInspecao("Campo", "Inspeções em campo", 1);
        setId(tipoCampo, idTipoInspecao);
        when(tipoInspecaoRepository.findById(idTipoInspecao)).thenReturn(Optional.of(tipoCampo));

        RespostaItem resposta = new RespostaItem(idPergunta, List.of(idOpcaoSim), null, null, null);
        RegistrarInspecaoRequest req = new RegistrarInspecaoRequest(
                idFormulario, idTipoInspecao, idProcesso, null, null, idBaseOperacional,
                OffsetDateTime.now(), "obs", null, null,
                List.of(colabA), List.of(resposta), List.of(), List.of(idEvidencia, idEvidencia));

        useCases.registrar(req, inspetor);

        ArgumentCaptor<InspecaoSeguranca> captor = ArgumentCaptor.forClass(InspecaoSeguranca.class);
        verify(repository).save(captor.capture());
        assertEquals(1, captor.getValue().getEvidencias().size());
    }

    @Test
    @DisplayName("atualizarCabecalho deve permitir atualização quando usuário for o criador da inspeção")
    void atualizarCabecalhoComSucesso() {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                idFormulario, idTipoInspecao, idProcesso, inspetor, null, null, idBaseOperacional, OffsetDateTime.now(), "obs original");
        inspecao.adicionarColaboradorInspecionado(colabA);
        UUID idInspecao = UUID.randomUUID();
        setId(inspecao, idInspecao);

        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TipoInspecao tipoNovo = new TipoInspecao("Novo Tipo", "desc", 2);
        UUID idTipoNovo = UUID.randomUUID();
        setId(tipoNovo, idTipoNovo);
        when(tipoInspecaoRepository.findById(idTipoNovo)).thenReturn(Optional.of(tipoNovo));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(baseOperacionalApi.existeBase(idBaseOperacional)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);
        UUID colabB = UUID.randomUUID();
        when(colaboradorApi.existeColaborador(colabB)).thenReturn(true);

        com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest req =
                new com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest(
                        idTipoNovo, idProcesso, idBaseOperacional, null, null,
                        OffsetDateTime.now(), "nova obs", null, null,
                        List.of(colabA, colabB), "Justificativa da alteração");

        var response = useCases.atualizarCabecalho(idInspecao, req, inspetor);

        assertNotNull(response);
        assertEquals("nova obs", inspecao.getObservacoes());
        assertEquals(2, inspecao.getColaboradoresInspecionados().size());
        verify(gravadorEventoService).registrarEdicaoCabecalho(eq(inspecao), any(), eq(inspetor), eq("Justificativa da alteração"));
    }

    @Test
    @DisplayName("atualizarCabecalho deve lançar AccessDeniedException quando usuário não for o criador")
    void atualizarCabecalhoNegadoParaNaoCriador() {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                idFormulario, idTipoInspecao, idProcesso, inspetor, null, null, idBaseOperacional, OffsetDateTime.now(), null);
        UUID idInspecao = UUID.randomUUID();
        setId(inspecao, idInspecao);
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));

        UUID outroUsuario = UUID.randomUUID();
        com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest req =
                new com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest(
                        idTipoInspecao, idProcesso, idBaseOperacional, null, null,
                        OffsetDateTime.now(), "obs", null, null,
                        List.of(colabA), "justificativa");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> useCases.atualizarCabecalho(idInspecao, req, outroUsuario));
    }

    @Test
    @DisplayName("atualizarCabecalho deve barrar remoção de colaborador que possui desvio associado")
    void atualizarCabecalhoImpedeRemoverColaboradorComDesvio() {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                idFormulario, idTipoInspecao, idProcesso, inspetor, null, null, idBaseOperacional, OffsetDateTime.now(), null);
        inspecao.adicionarColaboradorInspecionado(colabA);
        UUID colabComDesvio = UUID.randomUUID();
        inspecao.adicionarColaboradorInspecionado(colabComDesvio);
        inspecao.registrarDesvio(idPergunta, idOpcaoNao, GravidadeDesvio.ALTA, "desvio", "corrigir", null, List.of(colabComDesvio));

        UUID idInspecao = UUID.randomUUID();
        setId(inspecao, idInspecao);
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));

        TipoInspecao tipo = new TipoInspecao("Campo", "desc", 1);
        setId(tipo, idTipoInspecao);
        when(tipoInspecaoRepository.findById(idTipoInspecao)).thenReturn(Optional.of(tipo));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(baseOperacionalApi.existeBase(idBaseOperacional)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        // Tentando salvar apenas com colabA (removendo colabComDesvio)
        com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest req =
                new com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest(
                        idTipoInspecao, idProcesso, idBaseOperacional, null, null,
                        OffsetDateTime.now(), null, null, null,
                        List.of(colabA), "tentativa de remocao");

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.atualizarCabecalho(idInspecao, req, inspetor));
    }

    @Test
    @DisplayName("deve registrar inspeção com desvio tratado in-loco (RESOLVIDO) e definir data de resolução")
    void deveRegistrarInspecaoComDesvioTratadoInLoco() {
        FormularioInspecao form = formularioComPergunta();
        when(formularioRepository.findById(idFormulario)).thenReturn(Optional.of(form));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        when(repository.save(any(InspecaoSeguranca.class))).thenAnswer(invocation -> {
            InspecaoSeguranca entity = invocation.getArgument(0);
            setId(entity, UUID.randomUUID());
            return entity;
        });

        List<RespostaItem> respostas = List.of(new RespostaItem(idPergunta, List.of(idOpcaoNao), null, null, null));
        List<DesvioItem> desvios = List.of(new DesvioItem(
                idPergunta, idOpcaoNao, "Não conservada", "Substituído no ato da inspeção", null, List.of(colabA), StatusDesvio.RESOLVIDO));

        RegistrarInspecaoRequest req = request(List.of(colabA), respostas, desvios);

        var response = useCases.registrar(req, inspetor);

        ArgumentCaptor<InspecaoSeguranca> captor = ArgumentCaptor.forClass(InspecaoSeguranca.class);
        verify(repository).save(captor.capture());

        InspecaoSeguranca salva = captor.getValue();
        assertEquals(1, salva.getDesvios().size());
        DesvioSeguranca desvioSalvo = salva.getDesvios().get(0);
        assertEquals(StatusDesvio.RESOLVIDO, desvioSalvo.getStatus());
        assertNotNull(desvioSalvo.getDataResolucao());
        assertEquals("Substituído no ato da inspeção", desvioSalvo.getAcaoCorretiva());
        assertEquals(salva.getDataRealizacao().toLocalDate(), desvioSalvo.getPrazo());
    }

    @Test
    @DisplayName("atualizarInspecao deve permitir alteração completa de respostas, desvios e evidências")
    void deveAtualizarInspecaoCompletaComSucesso() {
        FormularioInspecao form = formularioComPergunta();
        when(formularioRepository.findById(idFormulario)).thenReturn(Optional.of(form));
        when(processoApi.existeProcesso(idProcesso)).thenReturn(true);
        when(baseOperacionalApi.existeBase(idBaseOperacional)).thenReturn(true);
        when(colaboradorApi.existeColaborador(colabA)).thenReturn(true);

        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                idFormulario, idTipoInspecao, idProcesso, inspetor, null, null, idBaseOperacional, OffsetDateTime.now(), "original");
        inspecao.adicionarColaboradorInspecionado(colabA);
        EvidenciaInspecao evid1 = evidenciaPendente(inspetor);
        inspecao.vincularEvidencia(evid1);
        UUID idInspecao = UUID.randomUUID();
        setId(inspecao, idInspecao);

        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));
        when(repository.saveAndFlush(any(InspecaoSeguranca.class))).thenAnswer(inv -> inv.getArgument(0));

        TipoInspecao tipo = new TipoInspecao("Campo", "desc", 1);
        setId(tipo, idTipoInspecao);
        when(tipoInspecaoRepository.findById(idTipoInspecao)).thenReturn(Optional.of(tipo));

        UUID idEvid2 = UUID.randomUUID();
        EvidenciaInspecao evid2 = new EvidenciaInspecao(
                inspetor, "nova_foto.jpg", "image/jpeg", 5, "sst/inspecoes/evidencias/" + idEvid2 + ".jpg");
        setId(evid2, idEvid2);
        when(evidenciaRepository.findById(idEvid2)).thenReturn(Optional.of(evid2));

        List<RespostaItem> novasRespostas = List.of(new RespostaItem(idPergunta, List.of(idOpcaoNao), null, null, null));
        List<DesvioItem> novosDesvios = List.of(new DesvioItem(
                idPergunta, idOpcaoNao, "Problema detectado", "Corrigido", null, List.of(colabA), StatusDesvio.RESOLVIDO));

        AtualizarInspecaoRequest req = new AtualizarInspecaoRequest(
                idFormulario, idTipoInspecao, idProcesso, idBaseOperacional, null, null,
                OffsetDateTime.now(), "obs atualizada", null, null,
                List.of(colabA), novasRespostas, novosDesvios, List.of(idEvid2), "Correção de dados preenchidos");

        var response = useCases.atualizarInspecao(idInspecao, req, inspetor);

        assertNotNull(response);
        assertEquals("obs atualizada", inspecao.getObservacoes());
        assertEquals(1, inspecao.getRespostas().size());
        assertEquals(1, inspecao.getDesvios().size());
        assertEquals(StatusDesvio.RESOLVIDO, inspecao.getDesvios().get(0).getStatus());
        assertEquals(1, inspecao.getEvidencias().size());
        assertEquals(idEvid2, inspecao.getEvidencias().get(0).getId());
        verify(gravadorEventoService).registrarEdicaoCompleta(eq(inspecao), any(), eq(inspetor), eq("Correção de dados preenchidos"));
    }

    @Test
    @DisplayName("atualizarInspecao deve barrar usuário que não é o criador da inspeção")
    void deveNegarAtualizacaoInspecaoParaNaoCriador() {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                idFormulario, idTipoInspecao, idProcesso, inspetor, null, null, idBaseOperacional, OffsetDateTime.now(), "original");
        UUID idInspecao = UUID.randomUUID();
        setId(inspecao, idInspecao);
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));

        UUID outro = UUID.randomUUID();
        AtualizarInspecaoRequest req = new AtualizarInspecaoRequest(
                idFormulario, idTipoInspecao, idProcesso, idBaseOperacional, null, null,
                OffsetDateTime.now(), "obs", null, null,
                List.of(colabA), List.of(), List.of(), List.of(idEvidencia), "tentativa");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> useCases.atualizarInspecao(idInspecao, req, outro));
    }
}
