package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoResponse;
import com.nexo.app.modules.estoque.application.mappers.SolicitacaoMapper;
import com.nexo.app.modules.estoque.application.services.PoliticaAcoesPermitidasSolicitacao;
import com.nexo.app.modules.estoque.domain.AcaoPermitidaSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.EtapaOperacionalSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.application.usecases.MotivoRejeicaoUseCases;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.BaseEntity;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoMaterialDetalheTecnicoTest {

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
    @InjectMocks private SolicitacaoMaterialUseCases useCases;

    @Test
    void detalheTecnicoIncluiContextoItensTotaisEacoesSemAlterarCamposLegados() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnica();
        SolicitacaoResponse legado = new SolicitacaoResponse(
                solicitacao.getId(), null, null, solicitacao.getIdRequisitante(), null, null,
                solicitacao.getStatus(), solicitacao.getDataSolicitacao(), null, null,
                null, null, null, null, List.of(), null);
        when(repository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoSolicitacaoRepository.findAllBySolicitacaoIdOrderByRealizadaEmAscIdAsc(solicitacao.getId()))
                .thenReturn(List.of());
        when(politicaAcoesPermitidas.calcular(solicitacao))
                .thenReturn(Set.of(AcaoPermitidaSolicitacao.REGISTRAR_RESERVA));
        when(mapper.toResponse(solicitacao)).thenReturn(legado);

        SolicitacaoResponse resposta = useCases.buscarPorIdCompleto(solicitacao.getId());

        assertThat(resposta.id()).isEqualTo(legado.id());
        assertThat(resposta.status()).isEqualTo(legado.status());
        assertThat(resposta.tecnico()).isNotNull();
        assertThat(resposta.tecnico().tipoTecnico()).isEqualTo(TipoSolicitacaoTecnica.ADICAO_COMUM);
        assertThat(resposta.tecnico().etapaOperacional())
                .isEqualTo(EtapaOperacionalSolicitacao.AGUARDANDO_PLANEJAMENTO);
        assertThat(resposta.tecnico().atrasada()).isFalse();
        assertThat(resposta.tecnico().encerrada()).isFalse();
        assertThat(resposta.tecnico().encerradaEm()).isNull();
        assertThat(resposta.tecnico().idUsuarioEncerramento()).isNull();
        assertThat(resposta.tecnico().motivoEncerramento()).isNull();
        assertThat(resposta.tecnico().idAlmoxarifadoAtendimento())
                .isEqualTo(solicitacao.getAlmoxarifadoAtendimento().getId());
        assertThat(resposta.tecnico().totais().solicitada()).isEqualByComparingTo("2.000");
        assertThat(resposta.tecnico().totais().pendente()).isEqualByComparingTo("2.000");
        assertThat(resposta.tecnico().acoesPermitidas()).containsExactly(AcaoPermitidaSolicitacao.REGISTRAR_RESERVA);
    }

    private SolicitacaoMaterial solicitacaoTecnica() {
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra tecnica", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        Estoque almoxarifado = entidade(new Estoque("ALMOX", entidade(new TipoEstoque("FISICO"))));
        Estoque obraEstoque = entidade(new Estoque("OBRA", entidade(new TipoEstoque("OBRA"))));
        obraEstoque.vincularObra(obra.id());
        SolicitacaoMaterial solicitacao = entidade(SolicitacaoMaterial.criarTecnica(
                obra, null, TipoSolicitacaoTecnica.ADICAO_COMUM, obraEstoque, almoxarifado,
                UUID.randomUUID(), null, "Observacao", UUID.randomUUID()));
        Material material = entidade(new Material("MAT-001", "Material", "UN", "MATR", null, null,
                false, UUID.randomUUID()));
        solicitacao.adicionarItemTecnico(material, new BigDecimal("2.000"));
        solicitacao.getItens().forEach(SolicitacaoMaterialDetalheTecnicoTest::entidade);
        return solicitacao;
    }

    private static <T extends BaseEntity> T entidade(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
