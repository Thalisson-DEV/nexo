package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.EtapaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Banco próprio: o teste conta a fila inteira, e o contexto compartilhado traz dados de outros testes.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:solicitacao-etapa-jpa;DB_CLOSE_DELAY=-1")
class SolicitacaoSpecificationEtapaJpaTest {


    @Autowired
    private SolicitacaoMaterialRepository solicitacaoRepository;

    @Autowired
    private ReservaSolicitacaoTecnicaRepository reservaRepository;

    @Autowired
    private TipoEstoqueRepository tipoEstoqueRepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Test
    @Transactional
    void aplicaEtapaAntesDaPaginacaoECalculaTotalSomenteDaFila() {
        Fixture fixture = fixture();
        SolicitacaoMaterial semData = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                null, null, StatusSeparacao.AGUARDANDO_AGENDAMENTO, false, false);
        SolicitacaoMaterial semDataLegado = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                null, null, null, false, false);
        SolicitacaoMaterial semEquipe = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                LocalDate.now(), null, StatusSeparacao.PRONTO_PARA_SEPARACAO, false, false);
        SolicitacaoMaterial semReserva = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                LocalDate.now(), UUID.randomUUID(), StatusSeparacao.PRONTO_PARA_SEPARACAO, false, false, false);
        SolicitacaoMaterial solicitacaoEmSeparacao = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                LocalDate.now(), UUID.randomUUID(), StatusSeparacao.EM_SEPARACAO, false, false);
        SolicitacaoMaterial prontaParaSeparar = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                LocalDate.now(), UUID.randomUUID(), StatusSeparacao.PRONTO_PARA_SEPARACAO, false, false);
        SolicitacaoMaterial pendenteEntrega = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                LocalDate.now(), UUID.randomUUID(), StatusSeparacao.SEPARADO, false, false);
        SolicitacaoMaterial entregaConcluida = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                LocalDate.now(), UUID.randomUUID(), StatusSeparacao.SEPARADO, false, false);
        entregaConcluida.getItens().getFirst().registrarEntregaTecnica(new BigDecimal("2.000"));
        solicitacaoRepository.saveAndFlush(entregaConcluida);
        SolicitacaoMaterial encerrada = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                null, null, StatusSeparacao.AGUARDANDO_AGENDAMENTO, false, true);
        SolicitacaoMaterial devolucaoPlanejada = criar(fixture, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM,
                LocalDate.now(), UUID.randomUUID(), null, false, false);
        SolicitacaoMaterial devolucaoSemPlanejamento = criar(fixture, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM,
                null, null, null, false, false, false);
        SolicitacaoMaterial devolucaoConcluida = criar(fixture, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM,
                null, null, null, false, false, false);
        devolucaoConcluida.getItens().getFirst().registrarDevolucaoTecnica(new BigDecimal("2.000"));
        devolucaoConcluida.confirmarDevolucaoTecnica();
        solicitacaoRepository.saveAndFlush(devolucaoConcluida);

        Page<SolicitacaoMaterial> aguardando = consultar(EtapaSolicitacaoTecnica.AGUARDANDO_PLANEJAMENTO);
        Page<SolicitacaoMaterial> emSeparacao = consultar(EtapaSolicitacaoTecnica.EM_SEPARACAO);
        Page<SolicitacaoMaterial> pendente = consultar(EtapaSolicitacaoTecnica.PENDENTE_ENTREGA);
        Page<SolicitacaoMaterial> aguardandoPaginada = consultar(
                EtapaSolicitacaoTecnica.AGUARDANDO_PLANEJAMENTO, 1);

        assertThat(aguardando.getTotalElements()).isEqualTo(2);
        assertThat(aguardandoPaginada.getTotalElements()).isEqualTo(2);
        assertThat(aguardandoPaginada.getTotalPages()).isEqualTo(2);
        assertThat(aguardandoPaginada.getContent()).hasSize(1);
        assertThat(aguardando.getContent()).extracting(SolicitacaoMaterial::getId)
                .containsExactlyInAnyOrder(semData.getId(), semDataLegado.getId())
                .doesNotContain(semEquipe.getId(), semReserva.getId(), prontaParaSeparar.getId(),
                        solicitacaoEmSeparacao.getId(), encerrada.getId(), devolucaoPlanejada.getId(), devolucaoSemPlanejamento.getId());
        assertThat(emSeparacao.getTotalElements()).isEqualTo(4);
        assertThat(emSeparacao.getContent()).extracting(SolicitacaoMaterial::getId)
                .containsExactlyInAnyOrder(semEquipe.getId(), semReserva.getId(),
                        solicitacaoEmSeparacao.getId(), prontaParaSeparar.getId())
                .doesNotContain(semData.getId(), semDataLegado.getId(),
                        encerrada.getId(), devolucaoPlanejada.getId());
        assertThat(pendente.getTotalElements()).isEqualTo(3);
        assertThat(pendente.getContent()).extracting(SolicitacaoMaterial::getId)
                .containsExactlyInAnyOrder(pendenteEntrega.getId(), devolucaoPlanejada.getId(), devolucaoSemPlanejamento.getId())
                .doesNotContain(entregaConcluida.getId(), devolucaoConcluida.getId());
    }

    @Test
    @Transactional
    void filtraPeloSaquePrevistoComLimitesInclusivos() {
        Fixture fixture = fixture();
        LocalDate hoje = LocalDate.now();
        SolicitacaoMaterial antes = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                hoje.minusDays(1), UUID.randomUUID(), StatusSeparacao.PRONTO_PARA_SEPARACAO, false, false);
        SolicitacaoMaterial noInicio = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                hoje, UUID.randomUUID(), StatusSeparacao.PRONTO_PARA_SEPARACAO, false, false);
        SolicitacaoMaterial noFim = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                hoje.plusDays(6), UUID.randomUUID(), StatusSeparacao.PRONTO_PARA_SEPARACAO, false, false);
        SolicitacaoMaterial semData = criar(fixture, TipoSolicitacaoTecnica.ADICAO_COMUM,
                null, null, StatusSeparacao.AGUARDANDO_AGENDAMENTO, false, false);

        SolicitacaoFilter filtro = new SolicitacaoFilter(
                null, null, null, null, null, null, null, ContextoSolicitacaoMaterial.TECNICO,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, hoje, hoje.plusDays(6));
        Page<SolicitacaoMaterial> pagina = solicitacaoRepository.findAll(
                SolicitacaoSpecification.filterBy(filtro), PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(SolicitacaoMaterial::getId)
                .containsExactlyInAnyOrder(noInicio.getId(), noFim.getId())
                .doesNotContain(antes.getId(), semData.getId());
    }

    private Page<SolicitacaoMaterial> consultar(EtapaSolicitacaoTecnica etapa) {
        return consultar(etapa, 20);
    }

    private Page<SolicitacaoMaterial> consultar(EtapaSolicitacaoTecnica etapa, int tamanho) {
        return solicitacaoRepository.findAll(
                SolicitacaoSpecification.filterBy(filtro(etapa)), PageRequest.of(0, tamanho));
    }

    private SolicitacaoFilter filtro(EtapaSolicitacaoTecnica etapa) {
        return new SolicitacaoFilter(
                null, null, null, null, null, null, null, ContextoSolicitacaoMaterial.TECNICO,
                null, null, null, null, null, null, null, null, null, etapa,
                null, null, null, null, null);
    }

    private SolicitacaoMaterial criar(
            Fixture fixture,
            TipoSolicitacaoTecnica tipo,
            LocalDate data,
            UUID idEquipe,
            StatusSeparacao statusSeparacao,
            boolean saqueForaEquipe,
            boolean encerrada
    ) {
        return criar(fixture, tipo, data, idEquipe, statusSeparacao, saqueForaEquipe, encerrada, true);
    }

    private SolicitacaoMaterial criar(
            Fixture fixture,
            TipoSolicitacaoTecnica tipo,
            LocalDate data,
            UUID idEquipe,
            StatusSeparacao statusSeparacao,
            boolean saqueForaEquipe,
            boolean encerrada,
            boolean registrarReserva
    ) {
        SolicitacaoMaterial solicitacao = SolicitacaoMaterial.criarTecnica(
                fixture.obra(), null, tipo, fixture.estoqueObra(), fixture.almoxarifado(),
                idEquipe, null, saqueForaEquipe, data, null, UUID.randomUUID());
        solicitacao.adicionarItemTecnico(fixture.material(), new BigDecimal("2.000"));
        ReflectionTestUtils.setField(solicitacao, "statusSeparacao", statusSeparacao);
        if (encerrada) {
            ReflectionTestUtils.setField(solicitacao, "encerradaEm", java.time.OffsetDateTime.now());
        }
        SolicitacaoMaterial salvo = solicitacaoRepository.saveAndFlush(solicitacao);
        if (registrarReserva && data != null && (idEquipe != null || saqueForaEquipe)) {
            ReservaSolicitacaoTecnica reserva = new ReservaSolicitacaoTecnica(
                    salvo, BigInteger.valueOf(123456), LocalDate.now(), null, null, null, UUID.randomUUID());
            reservaRepository.saveAndFlush(reserva);
        }
        return salvo;
    }

    private Fixture fixture() {
        TipoEstoque tipoObra = tipoEstoqueRepository.saveAndFlush(new TipoEstoque("OBRA"));
        TipoEstoque tipoAlmoxarifado = tipoEstoqueRepository.saveAndFlush(new TipoEstoque("FISICO"));
        Estoque estoqueObra = new Estoque("OBRA TESTE", tipoObra);
        estoqueObra.vincularObra(UUID.randomUUID());
        estoqueObra = estoqueRepository.saveAndFlush(estoqueObra);
        Estoque almoxarifado = estoqueRepository.saveAndFlush(new Estoque("ALMOX TESTE", tipoAlmoxarifado));
        Material material = materialRepository.saveAndFlush(new Material(
                "MAT-" + UUID.randomUUID().toString().substring(0, 8), "Material teste", "UN", "MATR",
                null, null, false, UUID.randomUUID()));
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "Processo", true, UUID.randomUUID(), Set.of(), null, List.of());
        return new Fixture(obra, estoqueObra, almoxarifado, material);
    }

    private record Fixture(ObraPublica obra, Estoque estoqueObra, Estoque almoxarifado, Material material) {
    }
}
