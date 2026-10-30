package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.AprovarSolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.application.mappers.SolicitacaoMapper;
import com.nexo.app.modules.estoque.application.services.AlteracoesSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.services.ConsultaOperacoesSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.PoliticaAcoesPermitidasSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoMaterialComercialValidationTest {

    @Mock private SolicitacaoMaterialRepository repository;
    @Mock private SaldoRepository saldoRepository;
    @Mock private SaldoSerieRepository serieRepository;
    @Mock private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private SolicitacaoMapper mapper;
    @Mock private MotivoRejeicaoUseCases motivoRejeicaoUseCases;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OperacaoSolicitacaoMaterialRepository operacaoSolicitacaoRepository;
    @Mock private PoliticaAcoesPermitidasSolicitacao politicaAcoesPermitidas;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private AlteracoesSolicitacaoComercialService alteracoesComerciais;
    @Mock private ConsultaOperacoesSolicitacaoComercialService operacoesComerciais;
    @Mock private EquipeApi equipeApi;
    @Mock private ColaboradorApi colaboradorApi;
    @InjectMocks private SolicitacaoMaterialUseCases useCases;

    @Test
    void rejeitaOrigemQueNaoSejaFisicaEAtiva() {
        Estoque origemCampo = estoque("ORIGEM CAMPO", "CAMPO", null);
        UUID idEquipe = UUID.randomUUID();
        when(estoqueRepository.findById(origemCampo.getId())).thenReturn(Optional.of(origemCampo));

        assertThatThrownBy(() -> useCases.criarDireta(
                request(origemCampo.getId(), null, idEquipe), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("FISICO");
    }

    @Test
    void rejeitaEquipeSemEstoqueDeCampo() {
        Estoque origem = estoque("ALMOX", "FISICO", null);
        UUID idEquipe = UUID.randomUUID();
        when(estoqueRepository.findById(origem.getId())).thenReturn(Optional.of(origem));
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.criarDireta(
                request(origem.getId(), null, idEquipe), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("nao possui estoque de campo");
    }

    @Test
    void rejeitaDestinoLegadoDiferenteDoEstoqueDaEquipe() {
        Estoque origem = estoque("ALMOX", "FISICO", null);
        UUID idEquipe = UUID.randomUUID();
        Estoque destino = estoque("EQ-01", "CAMPO", idEquipe);
        when(estoqueRepository.findById(origem.getId())).thenReturn(Optional.of(origem));
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.of(destino));

        assertThatThrownBy(() -> useCases.criarDireta(
                request(origem.getId(), UUID.randomUUID(), idEquipe), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("nao corresponde");
    }

    @Test
    void aceitaDestinoOmitidoOuLegadoCoerente() {
        Estoque origem = estoque("ALMOX", "FISICO", null);
        UUID idEquipe = UUID.randomUUID();
        Estoque destino = estoque("EQ-01", "CAMPO", idEquipe);
        Material material = new Material(
                "MAT-001", "Material", "UN", "MATR", null, null, false, UUID.randomUUID());
        ReflectionTestUtils.setField(material, "id", UUID.randomUUID());
        when(estoqueRepository.findById(origem.getId())).thenReturn(Optional.of(origem));
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.of(destino));
        when(materialRepository.findById(material.getId())).thenReturn(Optional.of(material));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, UUID.randomUUID(), UUID.randomUUID(), null, null)));
        when(repository.save(any(SolicitacaoMaterial.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoRequest requestLegadoCoerente = new SolicitacaoRequest(
                origem.getId(),
                destino.getId(),
                LocalDate.now().plusDays(2),
                idEquipe,
                null,
                false,
                List.of(new SolicitacaoRequest.ItemRequest(material.getId(), BigDecimal.ONE)));
        SolicitacaoRequest requestAtual = new SolicitacaoRequest(
                origem.getId(),
                null,
                LocalDate.now().plusDays(2),
                idEquipe,
                null,
                false,
                List.of(new SolicitacaoRequest.ItemRequest(material.getId(), BigDecimal.ONE)));
        useCases.criarDireta(requestLegadoCoerente, UUID.randomUUID(), UUID.randomUUID());
        useCases.criarDireta(requestAtual, UUID.randomUUID(), UUID.randomUUID());

        verify(repository, times(2)).save(any(SolicitacaoMaterial.class));
    }

    @Test
    void criacaoComercialDefineTipoFixoComo221() {
        UUID idEquipe = UUID.randomUUID();
        Estoque origem = estoque("ALMOX_01", "FISICO", null);
        Estoque destino = estoque("CAMPO_01", "CAMPO", idEquipe);
        Material material = new Material(
                "MAT-01", "Material Teste", "UN", "MATR", null, null, false, UUID.randomUUID());
        ReflectionTestUtils.setField(material, "id", UUID.randomUUID());
        when(estoqueRepository.findById(origem.getId())).thenReturn(Optional.of(origem));
        when(estoqueRepository.findByIdEquipe(idEquipe)).thenReturn(Optional.of(destino));
        when(materialRepository.findById(material.getId())).thenReturn(Optional.of(material));
        when(equipeApi.buscarAtivaPorId(idEquipe)).thenReturn(Optional.of(new EquipeApi.EquipePublica(
                idEquipe, "EQ-01", true, UUID.randomUUID(), UUID.randomUUID(), null, null)));

        org.mockito.ArgumentCaptor<SolicitacaoMaterial> captor = org.mockito.ArgumentCaptor.forClass(SolicitacaoMaterial.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoRequest request = new SolicitacaoRequest(
                origem.getId(),
                destino.getId(),
                LocalDate.now().plusDays(2),
                idEquipe,
                null,
                false,
                List.of(new SolicitacaoRequest.ItemRequest(material.getId(), BigDecimal.ONE)));

        useCases.criar(request, UUID.randomUUID());

        SolicitacaoMaterial criada = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(criada.getContexto())
                .isEqualTo(com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL);
        org.assertj.core.api.Assertions.assertThat(criada.getTipoTecnico())
                .isEqualTo(com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM);
        org.assertj.core.api.Assertions.assertThat(criada.getTipoTecnico().getCodigo())
                .isEqualTo("221");
    }

    @Test
    void aprovacaoTrocaOAlmoxarifadoDeOrigemEADataPrevistaDoSaque() {
        UUID idEquipe = UUID.randomUUID();
        Estoque origem = estoque("ALMOX_01", "FISICO", null);
        Estoque outroAlmox = estoque("ALMOX_02", "FISICO", null);
        Estoque destino = estoque("CAMPO_01", "CAMPO", idEquipe);
        Material material = new Material(
                "MAT-01", "Material Teste", "UN", "MATR", null, null, false, UUID.randomUUID());
        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(
                origem, destino, UUID.randomUUID(), LocalDate.now().plusDays(2), idEquipe, null, false);
        ReflectionTestUtils.setField(solicitacao, "id", UUID.randomUUID());
        solicitacao.adicionarItem(material, BigDecimal.ONE);
        LocalDate novaData = LocalDate.now().plusDays(5);
        when(repository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(estoqueRepository.findById(outroAlmox.getId())).thenReturn(Optional.of(outroAlmox));

        useCases.aprovar(solicitacao.getId(), UUID.randomUUID(),
                new AprovarSolicitacaoRequest(null, null, null, outroAlmox.getId(), novaData));

        org.assertj.core.api.Assertions.assertThat(solicitacao.getEstoqueOrigem()).isSameAs(outroAlmox);
        org.assertj.core.api.Assertions.assertThat(solicitacao.getDataPrevistaRetirada()).isEqualTo(novaData);
        org.assertj.core.api.Assertions.assertThat(solicitacao.getStatus())
                .isEqualTo(SolicitacaoMaterial.StatusSolicitacao.APROVADO);
    }

    @Test
    void aprovacaoRecusaOrigemQueNaoSejaAlmoxarifadoFisico() {
        UUID idEquipe = UUID.randomUUID();
        Estoque origem = estoque("ALMOX_01", "FISICO", null);
        Estoque outroCampo = estoque("CAMPO_02", "CAMPO", UUID.randomUUID());
        Estoque destino = estoque("CAMPO_01", "CAMPO", idEquipe);
        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(
                origem, destino, UUID.randomUUID(), LocalDate.now().plusDays(2), idEquipe, null, false);
        ReflectionTestUtils.setField(solicitacao, "id", UUID.randomUUID());
        when(repository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(estoqueRepository.findById(outroCampo.getId())).thenReturn(Optional.of(outroCampo));

        assertThatThrownBy(() -> useCases.aprovar(solicitacao.getId(), UUID.randomUUID(),
                new AprovarSolicitacaoRequest(null, null, null, outroCampo.getId(), null)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("FISICO");
    }

    private static SolicitacaoRequest request(UUID origem, UUID destino, UUID equipe) {
        return new SolicitacaoRequest(
                origem, destino, LocalDate.now().plusDays(2), equipe, null, false, List.of());
    }

    private static Estoque estoque(String nome, String tipo, UUID idEquipe) {
        Estoque estoque = new Estoque(nome, new TipoEstoque(tipo));
        ReflectionTestUtils.setField(estoque, "id", UUID.randomUUID());
        if (idEquipe != null) estoque.vincularEquipe(idEquipe);
        return estoque;
    }
}
