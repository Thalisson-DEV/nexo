package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.PrioridadeObra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessarImportacaoCarteiraServiceTest {

    @Mock
    private ImportacaoObraRepository importacaoRepository;
    @Mock
    private ObraRepository obraRepository;
    @Mock
    private StatusObraRepository statusRepository;
    @Mock
    private ProcessoNegocioApi processoNegocioApi;
    @Mock
    private BaseOperacionalApi baseOperacionalApi;
    @Mock
    private MunicipioApi municipioApi;
    @Mock
    private CentroFaturamentoApi centroFaturamentoApi;
    @Mock
    private SupervisorApi supervisorApi;
    @Mock
    private CoordenadorApi coordenadorApi;
    @Mock
    private PlatformTransactionManager transacoes;

    private ProcessarImportacaoCarteiraService service;
    private ImportacaoObra importacao;
    private final UUID contrato = UUID.randomUUID();
    private final Map<String, Obra> obras = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new ProcessarImportacaoCarteiraService(importacaoRepository, obraRepository, statusRepository,
                processoNegocioApi, baseOperacionalApi, municipioApi, centroFaturamentoApi, supervisorApi,
                coordenadorApi, new NormalizadorObraService(), transacoes);
        importacao = new ImportacaoObra(ImportacaoObra.Tipo.CARTEIRA, contrato, "carteira.csv");
        when(importacaoRepository.findById(any())).thenReturn(Optional.of(importacao));
        lenient().when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(obras.get(inv.getArgument(0, String.class))));
        lenient().when(obraRepository.save(any(Obra.class))).thenAnswer(inv -> {
            Obra obra = inv.getArgument(0);
            obras.put(obra.getNumero(), obra);
            return obra;
        });
        lenient().when(statusRepository.findByNomeIgnoreCase("PLANEJADA"))
                .thenReturn(Optional.of(new StatusObra("PLANEJADA", 1, false)));
        UUID qualquer = UUID.randomUUID();
        lenient().when(processoNegocioApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(baseOperacionalApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(municipioApi.buscarIdPorNome(anyString()))
                .thenAnswer(inv -> "Inexistente".equals(inv.getArgument(0)) ? Optional.empty() : Optional.of(qualquer));
        lenient().when(centroFaturamentoApi.buscarIdPorNome(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(supervisorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(qualquer));
        lenient().when(coordenadorApi.buscarIdPorNomeColaborador(anyString())).thenReturn(Optional.of(qualquer));
    }

    @Test
    void reimportarAtualizaSoOsDadosDeCarteiraPresentesNaLinha() {
        Obra existente = novaObra("1000");
        existente.informarCarteira(null, null, PrioridadeObra.BAIXA, "LOTE-A", "Liberada",
                null, new BigDecimal("10.00"), new BigDecimal("20.00"));
        obras.put("1000", existente);

        String csv = "Número;Tensão;Prioridade;Lote;Valor Serviço Previsto;Latitude;Longitude\n"
                + "1000;15 kV;Alta;;35.000,50;-9,41;-40,50\n";
        service.processar(UUID.randomUUID(), linhas(csv), "ana");

        assertThat(importacao.getStatus()).isEqualTo(ImportacaoObra.Status.CONCLUIDA);
        assertThat(importacao.getAtualizados()).isEqualTo(1);
        assertThat(importacao.getInseridos()).isZero();
        assertThat(existente.getIdContrato()).isEqualTo(contrato);
        assertThat(existente.getPrioridade()).isEqualTo(PrioridadeObra.ALTA);
        assertThat(existente.getLotePrevisto()).isEqualTo("LOTE-A");
        assertThat(existente.getSituacaoCarteira()).isEqualTo("Liberada");
        assertThat(existente.getValorMaterialPrevisto()).isEqualByComparingTo("10.00");
        assertThat(existente.getValorServicoPrevisto()).isEqualByComparingTo("35000.50");
        assertThat(existente.getClasseTensao()).isEqualTo(ClasseTensao.MT_15);
        assertThat(existente.getClasseTensaoInformadaPor()).isEqualTo("ana");
        assertThat(existente.getLatitude()).isEqualTo(-9.41);
    }

    @Test
    void criaObraNovaEReportaLinhasInvalidasSemInterromperAsDemais() {
        String csv = "NUMERO;TITULO;PROCESSO_NEGOCIO;BASE;MUNICIPIO;CENTRO_CUSTO;SUPERVISOR;COORDENADOR;DATA_INICIO;PRAZO;PRIORIDADE\n"
                + "2000;Extensão de rede;Expansão;Base 1;Cidade;CC;Sup;Coord;01/11/2026;30/06/2027;Média\n"
                + ";Sem número;Expansão;Base 1;Cidade;CC;Sup;Coord;01/11/2026;30/06/2027;\n"
                + "2001;Município errado;Expansão;Base 1;Inexistente;CC;Sup;Coord;01/11/2026;30/06/2027;\n"
                + "2002;Prioridade errada;Expansão;Base 1;Cidade;CC;Sup;Coord;01/11/2026;30/06/2027;Urgentíssima\n"
                + "2003;;Expansão;Base 1;Cidade;CC;Sup;Coord;01/11/2026;30/06/2027;\n";
        service.processar(UUID.randomUUID(), linhas(csv), "ana");

        assertThat(importacao.getStatus()).isEqualTo(ImportacaoObra.Status.CONCLUIDA);
        assertThat(importacao.getTotalLinhas()).isEqualTo(5);
        assertThat(importacao.getInseridos()).isEqualTo(1);
        assertThat(importacao.getErros()).isEqualTo(4);
        assertThat(importacao.getDetalheErros()).extracting(ImportacaoObra.ErroLinha::motivo)
                .anySatisfy(m -> assertThat(m).contains("Número da obra"))
                .anySatisfy(m -> assertThat(m).contains("Município não encontrado"))
                .anySatisfy(m -> assertThat(m).contains("Prioridade inválida"))
                .anySatisfy(m -> assertThat(m).contains("sem título"));
        Obra criada = obras.get("2000");
        assertThat(criada.getTitulo()).isEqualTo("EXTENSÃO DE REDE");
        assertThat(criada.getIdContrato()).isEqualTo(contrato);
        assertThat(criada.getPrioridade()).isEqualTo(PrioridadeObra.NORMAL);
        assertThat(criada.getStatusObra().getNome()).isEqualTo("PLANEJADA");
    }


    static Obra novaObra(String numero) {
        return new Obra(numero, "OBRA " + numero, null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new StatusObra("PLANEJADA", 1, false), null, null);
    }

    private static java.util.List<LeitorPlanilhaImportacao.Linha> linhas(String csv) {
        return new LeitorPlanilhaImportacao().ler(csv.getBytes(StandardCharsets.UTF_8), "planilha.csv");
    }
}
