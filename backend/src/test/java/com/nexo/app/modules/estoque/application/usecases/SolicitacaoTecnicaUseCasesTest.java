package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoTecnicaUseCasesTest {

    @Mock private ReferenciaTecnicaValidator referenciaValidator;
    @Mock private EstoqueObraProvisioningService estoqueObraProvisioningService;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private ItemSolicitacaoRepository itemRepository;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private PublicadorEventoSolicitacaoTecnica publicadorEventos;
    @Mock private ObservabilidadeSolicitacaoTecnica observabilidade;
    @InjectMocks private SolicitacaoTecnicaUseCases useCases;

    @Test
    void submissaoComQuatroTiposCriaUmaSolicitacaoIndependentePorTipo() {
        Fixture fixture = fixture();
        configurarPersistencia(fixture);
        CriarSolicitacaoTecnicaRequest request = request(fixture, List.of(
                item(fixture.comum(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM),
                item(fixture.concreto(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_CONCRETO),
                item(fixture.comum(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.DEVOLUCAO_COMUM),
                item(fixture.concreto(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO)));

        var resposta = useCases.criar(request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(resposta.solicitacoesCriadas()).hasSize(4);
        assertThat(resposta.solicitacoesCriadas()).extracting(item -> item.codigo())
                .containsExactlyInAnyOrder("221", "921", "222", "922");
        ArgumentCaptor<List<SolicitacaoMaterial>> captor = ArgumentCaptor.forClass(List.class);
        verify(solicitacaoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(filho -> {
            assertThat(filho.getIdObra()).isEqualTo(fixture.obra().id());
            assertThat(filho.getOcorrencia()).isEqualTo("OC-1042");
            assertThat(filho.getItens()).hasSize(1);
        });
    }

    @Test
    void itensDoMesmoTipoCompartilhamUmUnicoFilho() {
        Fixture fixture = fixture();
        Material outroComum = material("MAT-COMUM-2");
        configurarPersistencia(fixture, outroComum);
        CriarSolicitacaoTecnicaRequest request = request(fixture, List.of(
                item(fixture.comum(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM),
                item(outroComum, com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM)));

        var resposta = useCases.criar(request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(resposta.solicitacoesCriadas()).singleElement().satisfies(
                criado -> assertThat(criado.codigo()).isEqualTo("221"));
        ArgumentCaptor<List<SolicitacaoMaterial>> captor = ArgumentCaptor.forClass(List.class);
        verify(solicitacaoRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getItens()).hasSize(2);
    }

    @Test
    void criacaoRegistraSnapshotInicialDeCabecalhoEItens() {
        Fixture fixture = fixture();
        configurarPersistencia(fixture);
        CriarSolicitacaoTecnicaRequest request = request(fixture, List.of(
                item(fixture.comum(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM)));

        useCases.criar(request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<PayloadEventoSolicitacao> payload = ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(any(), any(), payload.capture(), any());
        assertThat(payload.getValue().alteracoes()).isEmpty();
        assertThat(payload.getValue().snapshots())
                .containsKeys("obra", "tipoObra", "ocorrencia", "tipoTecnico", "almoxarifado",
                        "equipe", "dataPrevista", "observacao", "itens");
        assertThat((List<?>) payload.getValue().snapshots().get("itens")).hasSize(1);
    }

    @Test
    void persisteOcorrenciaCanonicaEmCadaSolicitacao() {
        Fixture fixture = fixture();
        CriarSolicitacaoTecnicaRequest request = new CriarSolicitacaoTecnicaRequest(
                fixture.obra().id(), "  os   12345 ", fixture.almoxarifado().getId(),
                null, null, "Teste",
                List.of(item(fixture.comum(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM)));
        configurarPersistenciaComOcorrencia(
                fixture, request.ocorrencia(), "OS 12345");

        useCases.criar(request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<List<SolicitacaoMaterial>> filhos = ArgumentCaptor.forClass(List.class);
        verify(solicitacaoRepository).saveAll(filhos.capture());
        assertThat(filhos.getValue()).allSatisfy(
                filho -> assertThat(filho.getOcorrencia()).isEqualTo("OS 12345"));
    }

    @Test
    void referenciaInvalidaImpedeProvisionamento() {
        CriarSolicitacaoTecnicaRequest request = new CriarSolicitacaoTecnicaRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), null, null, "Teste",
                List.of(new CriarSolicitacaoTecnicaRequest.Item(
                        UUID.randomUUID(), com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM, BigDecimal.ONE)));
        when(referenciaValidator.validar(request.idObra(), null, null, null, null, false))
                .thenThrow(new PreenchimentoInvalidoException(java.util.Map.of("idObra", "Obra invalida")));

        assertThatThrownBy(() -> useCases.criar(request, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(PreenchimentoInvalidoException.class);

        verifyNoInteractions(estoqueObraProvisioningService, solicitacaoRepository);
        verify(estoqueRepository, never()).findById(any());
    }

    private void configurarPersistencia(Fixture fixture, Material... adicionais) {
        configurarPersistenciaComOcorrencia(fixture, "OC-1042", "OC-1042", adicionais);
    }

    private void configurarPersistenciaComOcorrencia(
            Fixture fixture,
            String ocorrenciaInformada,
            String ocorrenciaNormalizada,
            Material... adicionais
    ) {
        when(referenciaValidator.validar(fixture.obra().id(), ocorrenciaInformada, null, null, null, false))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(
                        fixture.obra(), new com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica(
                        UUID.randomUUID(), fixture.obra().id(), ocorrenciaNormalizada), null, null));
        when(estoqueRepository.findById(fixture.almoxarifado().getId()))
                .thenReturn(Optional.of(fixture.almoxarifado()));
        List<Material> materiais = new java.util.ArrayList<>(List.of(fixture.comum(), fixture.concreto()));
        materiais.addAll(List.of(adicionais));
        when(materialRepository.findAllById(any())).thenReturn(materiais);
        when(estoqueObraProvisioningService.resolverOuProvisionar(fixture.obra()))
                .thenReturn(fixture.estoqueObra());
        when(solicitacaoRepository.saveAll(any())).thenAnswer(invocation -> {
            List<SolicitacaoMaterial> filhos = invocation.getArgument(0);
            filhos.forEach(SolicitacaoTecnicaUseCasesTest::comId);
            return filhos;
        });
    }

    private static Fixture fixture() {
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001234", "OBRA CORRETIVA", TipoObraPublico.CORRETIVA,
                true, UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        TipoEstoque fisico = comId(new TipoEstoque("FISICO"));
        Estoque almoxarifado = comId(new Estoque("ALMOXARIFADO CENTRAL", fisico));
        almoxarifado.vincularBaseOperacional(obra.idBaseOperacional());
        TipoEstoque tipoObra = comId(new TipoEstoque("OBRA"));
        Estoque estoqueObra = comId(new Estoque("OBRA 001234", tipoObra));
        estoqueObra.vincularBaseOperacional(obra.idBaseOperacional());
        estoqueObra.vincularObra(obra.id());
        return new Fixture(
                obra, almoxarifado, estoqueObra,
                material("MAT-COMUM"),
                material("MAT-CONCRETO"));
    }

    private static Material material(String codigo) {
        return comId(new Material(
                codigo, "Material " + codigo, "UN", "MATR", null, null, false,
                UUID.randomUUID()));
    }

    private static CriarSolicitacaoTecnicaRequest request(
            Fixture fixture,
            List<CriarSolicitacaoTecnicaRequest.Item> itens
    ) {
        return new CriarSolicitacaoTecnicaRequest(
                fixture.obra().id(), "OC-1042", fixture.almoxarifado().getId(),
                null, null, "Teste", itens);
    }

    private static CriarSolicitacaoTecnicaRequest.Item item(
            Material material,
            com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica tipo
    ) {
        return new CriarSolicitacaoTecnicaRequest.Item(material.getId(), tipo, BigDecimal.ONE);
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        if (entidade.getId() == null) {
            ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        }
        return entidade;
    }

    private record Fixture(
            ObraPublica obra,
            Estoque almoxarifado,
            Estoque estoqueObra,
            Material comum,
            Material concreto
    ) {
    }
}
