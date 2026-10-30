package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.FrotaOdometroApi;
import com.nexo.app.modules.frota.FrotaCondutorApi;
import com.nexo.app.modules.frota.application.dto.AcoplarImplementoRequest;
import com.nexo.app.modules.frota.application.dto.CondutorRequest;
import com.nexo.app.modules.frota.application.dto.VincularCondutorRequest;
import com.nexo.app.modules.frota.domain.CategoriaCnh;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Funcao;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import java.time.LocalDate;
import com.nexo.app.modules.frota.application.dto.FichaVeiculoFilter;
import com.nexo.app.modules.frota.application.dto.ImplementoRequest;
import com.nexo.app.modules.frota.application.dto.TipoImplementoRequest;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaRequest;
import com.nexo.app.modules.frota.domain.Combustivel;
import com.nexo.app.modules.frota.domain.RegimeVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.CentroFaturamento;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caminho real com JPA: cadastro pelo RH gera a ficha pelo evento, a listagem compõe KM
 * e implementos com as consultas JPQL de verdade, e acoplar em outro veículo encerra o
 * acoplamento anterior.
 */
@SpringBootTest
@Transactional
class FrotaPersistenciaTest {

    @Autowired
    private VeiculoFrotaUseCases veiculos;
    @Autowired
    private ImplementoUseCases implementos;
    @Autowired
    private TipoImplementoUseCases tiposImplemento;
    @Autowired
    private FrotaOdometroApi odometro;
    @Autowired
    private VeiculoApi veiculoApi;
    @Autowired
    private FichaVeiculoRepository fichaRepository;
    @Autowired
    private BaseOperacionalRepository baseRepository;
    @Autowired
    private CentroFaturamentoRepository centroRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CondutorUseCases condutores;
    @Autowired
    private VinculoCondutorUseCases vinculos;
    @Autowired
    private FrotaCondutorApi condutorApi;
    @Autowired
    private ColaboradorRepository colaboradorRepository;
    @Autowired
    private ProcessoNegocioRepository processoRepository;
    @Autowired
    private FuncaoRepository funcaoRepository;

    private UUID idBase;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("""
                INSERT INTO frota_parametros (id, faixas_alerta_dias, limite_km_dia, versao, data_criacao, ativo)
                VALUES ('00000000-0000-0000-0000-00000000f001', '30,15,7,0', 1500, 0, CURRENT_TIMESTAMP, TRUE)
                """).executeUpdate();
        var centro = centroRepository.save(new CentroFaturamento("REGIONAL TESTE"));
        BaseOperacional base = baseRepository.save(new BaseOperacional("BASE FROTA", "BA", "Rua A, 1", centro));
        idBase = base.getId();
        this.base = base;
    }

    private BaseOperacional base;

    private UUID colaboradorComCnh(String nome, String cpf, String cnh) {
        var processo = processoRepository.save(new ProcessoNegocio("PROCESSO " + nome));
        var funcao = funcaoRepository.save(new Funcao("MOTORISTA " + nome, null));
        var colaborador = colaboradorRepository.save(new Colaborador(nome, "TESTE", nome + " TESTE", cpf,
                processo, base, funcao, LocalDate.of(2020, 1, 1)));
        condutores.registrarCnh(colaborador.getId(),
                new CondutorRequest(cnh, CategoriaCnh.D, LocalDate.now().plusYears(2), null, null));
        return colaborador.getId();
    }

    private VeiculoFrotaRequest request(String placa) {
        return new VeiculoFrotaRequest(placa, "CONSTELLATION", "VOLKSWAGEN", idBase, null, 2024, 2025,
                null, null, null, Combustivel.DIESEL_S10, new BigDecimal("150"), new BigDecimal("4.5"),
                SituacaoOperacional.ATIVO, RegimeVeiculo.LOCADO, "LOCADORA X", new BigDecimal("8000"),
                null, null, null, null, null);
    }

    @Test
    void veiculoCadastradoNoRhGanhaFichaPeloEvento() {
        var veiculo = veiculoApi.cadastrar(new VeiculoApi.DadosCadastroVeiculo("RHA1B23", "STRADA", "FIAT", idBase));

        var ficha = fichaRepository.findByIdVeiculo(veiculo.id()).orElseThrow();
        assertThat(ficha.getPlaca()).isEqualTo("RHA1B23");
        assertThat(ficha.getIdBaseOperacional()).isEqualTo(idBase);
    }

    @Test
    void cadastroPelaFrotaGravaRhEFichaEListagemCompoeKmEImplementos() {
        var criado = veiculos.criar(request("FRT1A23"));
        odometro.registrarLeitura(criado.idVeiculo(), 12_000, OffsetDateTime.now().minusDays(2),
                FrotaOdometroApi.OrigemLeitura.IMPORTACAO, UUID.randomUUID(), null);
        odometro.registrarLeitura(criado.idVeiculo(), 12_400, OffsetDateTime.now().minusDays(1),
                FrotaOdometroApi.OrigemLeitura.CHECKLIST, UUID.randomUUID(), null);
        var tipo = tiposImplemento.criar(new TipoImplementoRequest("CESTO AÉREO", null));
        var cesto = implementos.criar(new ImplementoRequest("CESTO-01", tipo.id(), null, null, null, null,
                idBase, null, null));
        implementos.acoplar(cesto.id(), new AcoplarImplementoRequest(criado.idVeiculo(),
                OffsetDateTime.now().minusHours(5), null));
        entityManager.flush();

        var pagina = veiculos.listar(new FichaVeiculoFilter("FRT1", List.of(idBase), null, null, null, null),
                PageRequest.of(0, 10, Sort.by("placa")));

        assertThat(pagina.getContent()).hasSize(1);
        var linha = pagina.getContent().get(0);
        assertThat(linha.modelo()).isEqualTo("CONSTELLATION");
        assertThat(linha.baseOperacional()).isEqualTo("BASE FROTA");
        assertThat(linha.kmAtual()).isEqualTo(12_400L);
        assertThat(linha.implementosAcoplados()).containsExactly("CESTO-01");
        assertThat(linha.regime()).isEqualTo(RegimeVeiculo.LOCADO);
    }

    @Test
    void acoplarEmOutroVeiculoEncerraOAcoplamentoAnterior() {
        var primeiro = veiculos.criar(request("FRT2A23"));
        var segundo = veiculos.criar(request("FRT3A23"));
        var tipo = tiposImplemento.criar(new TipoImplementoRequest("GUINDAUTO", null));
        var guindauto = implementos.criar(new ImplementoRequest("GUIN-01", tipo.id(), null, null, null, null,
                idBase, null, null));
        OffsetDateTime troca = OffsetDateTime.now().minusHours(1);

        implementos.acoplar(guindauto.id(), new AcoplarImplementoRequest(primeiro.idVeiculo(), troca.minusDays(1), null));
        implementos.acoplar(guindauto.id(), new AcoplarImplementoRequest(segundo.idVeiculo(), troca, null));
        entityManager.flush();

        var historico = implementos.historicoDoImplemento(guindauto.id());
        assertThat(historico).hasSize(2);
        assertThat(historico.get(0).placa()).isEqualTo("FRT3A23");
        assertThat(historico.get(0).fim()).isNull();
        assertThat(historico.get(1).placa()).isEqualTo("FRT2A23");
        assertThat(historico.get(1).fim()).isEqualTo(troca);
    }

    @Test
    void trocaDeCondutorEncerraOAnteriorEConsultaPorInstanteRespondeQuemDirigia() {
        var veiculo = veiculos.criar(request("FRT4A23"));
        UUID ana = colaboradorComCnh("ANA", "52998224725", "12345678901");
        UUID beto = colaboradorComCnh("BETO", "11144477735", "10987654321");
        OffsetDateTime inicioAna = OffsetDateTime.now().minusDays(3);
        OffsetDateTime inicioBeto = OffsetDateTime.now().minusDays(1);

        vinculos.vincular(veiculo.idVeiculo(), new VincularCondutorRequest(ana, inicioAna, null));
        vinculos.vincular(veiculo.idVeiculo(), new VincularCondutorRequest(beto, inicioBeto, null));
        entityManager.flush();

        assertThat(condutorApi.condutorEm(veiculo.idVeiculo(), inicioAna.plusHours(1))).contains(ana);
        assertThat(condutorApi.condutorEm(veiculo.idVeiculo(), OffsetDateTime.now())).contains(beto);
        assertThat(condutorApi.condutorEm(veiculo.idVeiculo(), inicioAna.minusDays(1))).isEmpty();
        var detalhe = veiculos.detalhar(veiculo.idVeiculo());
        assertThat(detalhe.condutorAtual().idColaborador()).isEqualTo(beto);
    }
}
