package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.PlanejarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ReferenciaEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoTecnicaReplanejamentoTest {
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
    void replanejaUmaSolicitacaoERegistraUmEventoComOsDiffs() {
        Fixture f = fixture();
        preparar(f);
        useCases.replanejar(f.solicitacao().getId(), request(f, TipoSolicitacaoTecnica.ADICAO_COMUM),
                UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(f.solicitacao().getOcorrencia()).isEqualTo("OS 900");
        assertThat(f.solicitacao().getDataPrevistaRetirada()).isEqualTo(LocalDate.of(2026, 9, 1));
        verify(solicitacaoRepository).saveAll(List.of(f.solicitacao()));
        ArgumentCaptor<PayloadEventoSolicitacao> payload = ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(any(), any(TipoEventoSolicitacao.class), payload.capture(), any());
        assertThat(payload.getValue().alteracoes()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void registraQuantidadeEMaterialComoDiffsDeCampoComIdentificacaoLegivel() {
        Fixture f = fixture();
        preparar(f);
        ItemSolicitacao item = f.solicitacao().getItens().getFirst();

        useCases.replanejar(f.solicitacao().getId(), request(
                        f, TipoSolicitacaoTecnica.ADICAO_COMUM, f.outroMaterial().getId(), new BigDecimal("2.500")),
                UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<PayloadEventoSolicitacao> payload = ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(any(), any(TipoEventoSolicitacao.class), payload.capture(), any());
        assertThat(payload.getValue().alteracoes())
                .extracting(AlteracaoEventoSolicitacao::caminho)
                .contains("itens[" + item.getId() + "].material", "itens[" + item.getId() + "].quantidade");
        AlteracaoEventoSolicitacao material = payload.getValue().alteracoes().stream()
                .filter(alteracao -> alteracao.caminho().endsWith(".material"))
                .findFirst().orElseThrow();
        assertThat(material.valorAnterior()).isInstanceOf(ReferenciaEventoSolicitacao.class);
        assertThat(((ReferenciaEventoSolicitacao) material.valorAnterior()).snapshot())
                .isEqualTo("MAT - Material");
        assertThat(((ReferenciaEventoSolicitacao) material.valorNovo()).snapshot())
                .isEqualTo("MAT-2 - Material MAT-2");
    }

    @Test
    void registraMaterialAcrescidoERemovidoComIdentificadoresPersistidos() {
        Fixture f = fixture();
        preparar(f);
        UUID idRemovido = f.solicitacao().getItens().getFirst().getId();
        PlanejarSolicitacaoTecnicaRequest request = new PlanejarSolicitacaoTecnicaRequest(
                f.solicitacao().getVersao(), f.obra().id(), "  os   900 ", f.almoxarifado().getId(),
                null, LocalDate.of(2026, 9, 1), "planejamento", List.of(
                new PlanejarSolicitacaoTecnicaRequest.Item(
                        null, f.outroMaterial().getId(), TipoSolicitacaoTecnica.ADICAO_COMUM,
                        new BigDecimal("2.000"))), "substituicao de material", true, true, true, true);

        useCases.replanejar(f.solicitacao().getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<PayloadEventoSolicitacao> payload = ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(
                any(), any(TipoEventoSolicitacao.class),
                payload.capture(), any());
        assertThat(payload.getValue().alteracoes())
                .filteredOn(alteracao -> alteracao.caminho().matches("^itens\\[[^]]+]$"))
                .hasSize(2)
                .anySatisfy(alteracao -> {
                    assertThat(alteracao.caminho()).isEqualTo("itens[" + idRemovido + "]");
                    assertThat(alteracao.valorAnterior()).isNotNull();
                    assertThat(alteracao.valorNovo()).isNull();
                })
                .anySatisfy(alteracao -> {
                    assertThat(alteracao.caminho()).doesNotContain("null");
                    assertThat(alteracao.valorAnterior()).isNull();
                    assertThat(alteracao.valorNovo()).isNotNull();
                });
    }

    @Test
    void rejeitaTipoDivergenteDaSolicitacaoImutavel() {
        Fixture f = fixture();
        when(solicitacaoRepository.findById(f.solicitacao().getId())).thenReturn(Optional.of(f.solicitacao()));
        assertThatThrownBy(() -> useCases.replanejar(
                f.solicitacao().getId(), request(f, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM),
                UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(PreenchimentoInvalidoException.class);
    }

    @Test
    void replanejamentoPermiteEquipeSemColaboradorSaque() {
        Fixture f = fixture();
        prepararInfra(f);
        UUID idEquipe = UUID.randomUUID();
        when(referenciaValidator.validar(f.obra().id(), "  os   900 ", idEquipe, null, null, false))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(
                        f.obra(), new com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica(
                        UUID.randomUUID(), f.obra().id(), "OS 900"),
                        new com.nexo.app.modules.rh.EquipeApi.EquipePublica(idEquipe, "EQP-01", true, null, null, null, null), null));

        PlanejarSolicitacaoTecnicaRequest request = new PlanejarSolicitacaoTecnicaRequest(
                f.solicitacao().getVersao(), f.obra().id(), "  os   900 ", f.almoxarifado().getId(),
                idEquipe, null, false, LocalDate.of(2026, 9, 1), "planejamento", List.of(
                new PlanejarSolicitacaoTecnicaRequest.Item(
                        f.solicitacao().getItens().getFirst().getId(), f.material().getId(),
                        TipoSolicitacaoTecnica.ADICAO_COMUM, BigDecimal.ONE)),
                "ajuste", true, true, true, true, true, true);

        useCases.replanejar(f.solicitacao().getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(f.solicitacao().getIdEquipeSaque()).isEqualTo(idEquipe);
        assertThat(f.solicitacao().getIdColaboradorSaque()).isNull();
    }

    @Test
    void replanejamentoExigeColaboradorSaqueQuandoSaqueForaEquipeVerdadeiro() {
        Fixture f = fixture();
        when(solicitacaoRepository.findById(f.solicitacao().getId())).thenReturn(Optional.of(f.solicitacao()));
        PlanejarSolicitacaoTecnicaRequest request = new PlanejarSolicitacaoTecnicaRequest(
                f.solicitacao().getVersao(), f.obra().id(), "  os   900 ", f.almoxarifado().getId(),
                null, null, true, LocalDate.of(2026, 9, 1), "planejamento", List.of(
                new PlanejarSolicitacaoTecnicaRequest.Item(
                        f.solicitacao().getItens().getFirst().getId(), f.material().getId(),
                        TipoSolicitacaoTecnica.ADICAO_COMUM, BigDecimal.ONE)),
                "ajuste", true, true, true, true, true, true);

        assertThatThrownBy(() -> useCases.replanejar(
                f.solicitacao().getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsEntry(
                                "idColaboradorSaque", "O colaborador responsavel pelo saque e obrigatorio para saque fora da equipe."));
    }

    @Test
    void replanejamentoAtualizaColaboradorSaqueERegistraDiff() {
        Fixture f = fixture();
        prepararInfra(f);
        UUID idColaborador = UUID.randomUUID();
        com.nexo.app.modules.rh.ColaboradorResponse colab = new com.nexo.app.modules.rh.ColaboradorResponse(
                idColaborador, null, "Lucas", "Pereira", "Lucas Pereira", "11122233344",
                null, null, null, LocalDate.now(), true);
        when(referenciaValidator.validar(f.obra().id(), "  os   900 ", null, null, idColaborador, true))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(
                        f.obra(), new com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica(
                        UUID.randomUUID(), f.obra().id(), "OS 900"), null, null, colab, true));

        PlanejarSolicitacaoTecnicaRequest request = new PlanejarSolicitacaoTecnicaRequest(
                f.solicitacao().getVersao(), f.obra().id(), "  os   900 ", f.almoxarifado().getId(),
                null, idColaborador, true, LocalDate.of(2026, 9, 1), "planejamento", List.of(
                new PlanejarSolicitacaoTecnicaRequest.Item(
                        f.solicitacao().getItens().getFirst().getId(), f.material().getId(),
                        TipoSolicitacaoTecnica.ADICAO_COMUM, BigDecimal.ONE)),
                "ajuste", true, true, true, true, true, true);

        useCases.replanejar(f.solicitacao().getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(f.solicitacao().getIdColaboradorSaque()).isEqualTo(idColaborador);
        assertThat(f.solicitacao().isSaqueForaEquipe()).isTrue();

        ArgumentCaptor<PayloadEventoSolicitacao> payload = ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(any(), any(TipoEventoSolicitacao.class), payload.capture(), any());
        assertThat(payload.getValue().alteracoes())
                .extracting(AlteracaoEventoSolicitacao::caminho)
                .contains("colaboradorSaque", "saqueForaEquipe");
    }

    private void preparar(Fixture f) {
        prepararInfra(f);
        when(referenciaValidator.validar(f.obra().id(), "  os   900 ", null, null, null, false))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(
                        f.obra(), new com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica(
                        UUID.randomUUID(), f.obra().id(), "OS 900"), null, null));
    }

    private void prepararInfra(Fixture f) {
        when(solicitacaoRepository.findById(f.solicitacao().getId())).thenReturn(Optional.of(f.solicitacao()));
        when(estoqueRepository.findById(f.almoxarifado().getId())).thenReturn(Optional.of(f.almoxarifado()));
        when(materialRepository.findAllById(any())).thenReturn(List.of(f.material(), f.outroMaterial()));
        when(estoqueObraProvisioningService.resolverOuProvisionar(f.obra())).thenReturn(f.estoqueObra());
        lenient().when(itemRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ItemSolicitacao> itens = invocation.getArgument(0);
            itens.forEach(item -> {
                if (item.getId() == null) entity(item);
            });
            return itens;
        });
        when(solicitacaoRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<SolicitacaoMaterial> solicitacoes = invocation.getArgument(0);
            solicitacoes.forEach(solicitacao -> solicitacao.getItens().forEach(
                    item -> {
                        if (item.getId() == null) entity(item);
                    }));
            return solicitacoes;
        });
    }

    private static PlanejarSolicitacaoTecnicaRequest request(Fixture f, TipoSolicitacaoTecnica tipo) {
        return request(f, tipo, f.material().getId(), BigDecimal.ONE);
    }

    private static PlanejarSolicitacaoTecnicaRequest request(
            Fixture f,
            TipoSolicitacaoTecnica tipo,
            UUID idMaterial,
            BigDecimal quantidade
    ) {
        ItemSolicitacao item = f.solicitacao().getItens().getFirst();
        return new PlanejarSolicitacaoTecnicaRequest(f.solicitacao().getVersao(), f.obra().id(), "  os   900 ",
                f.almoxarifado().getId(), null, LocalDate.of(2026, 9, 1), "planejamento", List.of(
                new PlanejarSolicitacaoTecnicaRequest.Item(item.getId(), idMaterial, tipo, quantidade)),
                "ajuste", true, true, true, true);
    }

    private static Fixture fixture() {
        ObraPublica obra = new ObraPublica(UUID.randomUUID(), "001", "OBRA", TipoObraPublico.CORRETIVA,
                true, UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        Estoque almox = entity(new Estoque("ALMOX", entity(new TipoEstoque("FISICO"))));
        Estoque estoqueObra = entity(new Estoque("OBRA", entity(new TipoEstoque("OBRA"))));
        estoqueObra.vincularObra(obra.id());
        Material material = entity(new Material("MAT", "Material", "UN", "MATR", null, null, false,
                UUID.randomUUID()));
        Material outroMaterial = entity(new Material("MAT-2", "Material MAT-2", "UN", "MATR", null, null, false,
                UUID.randomUUID()));
        SolicitacaoMaterial solicitacao = entity(SolicitacaoMaterial.criarTecnica(
                obra, new com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica(
                        UUID.randomUUID(), obra.id(), "OC-1042"),
                TipoSolicitacaoTecnica.ADICAO_COMUM, estoqueObra, almox, null, null, null, UUID.randomUUID()));
        solicitacao.adicionarItemTecnico(material, BigDecimal.ONE);
        solicitacao.getItens().forEach(SolicitacaoTecnicaReplanejamentoTest::entity);
        return new Fixture(obra, almox, estoqueObra, material, outroMaterial, solicitacao);
    }

    private static <T extends BaseEntity> T entity(T value) {
        if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        return value;
    }

    private record Fixture(ObraPublica obra, Estoque almoxarifado, Estoque estoqueObra,
                           Material material, Material outroMaterial, SolicitacaoMaterial solicitacao) { }
}
