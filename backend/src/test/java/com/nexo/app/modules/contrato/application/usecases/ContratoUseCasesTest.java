package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.contrato.application.mappers.AcaoMapper;
import com.nexo.app.modules.contrato.application.mappers.ContratoMapper;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.modules.contrato.domain.AcaoValor;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoValorRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratoUseCasesTest {

    @Mock
    private ContratoRepository repository;
    @Mock
    private AcaoValorRepository acaoValorRepository;
    @Mock
    private AcaoRepository acaoRepository;
    @Mock
    private ContratoMapper mapper;
    @Mock
    private AcaoMapper acaoMapper;

    @InjectMocks
    private ContratoUseCases useCases;

    private UUID idContrato;
    private Contrato contrato;
    private Acao acao1;
    private Acao acao2;

    @BeforeEach
    void setUp() {
        idContrato = UUID.randomUUID();
        contrato = new Contrato("CTR-001", "Cliente Teste",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("1.2500"));
        ReflectionTestUtils.setField(contrato, "id", idContrato);

        acao1 = new Acao("LIG01", "Ligação Nova");
        ReflectionTestUtils.setField(acao1, "id", UUID.randomUUID());

        acao2 = new Acao("REL01", "Religação");
        ReflectionTestUtils.setField(acao2, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("listarAcoesComValor deve retornar acoes com valor ajustado pelo coeficiente")
    void deveRetornarAcoesComValorAjustado() {
        LocalDate dataRef = LocalDate.of(2026, 10, 1);
        AcaoValor av1 = new AcaoValor(acao1, contrato, new BigDecimal("100.00"), LocalDate.of(2026, 1, 1));
        AcaoValor av2 = new AcaoValor(acao2, contrato, new BigDecimal("50.00"), LocalDate.of(2026, 1, 1));

        when(repository.findById(idContrato)).thenReturn(Optional.of(contrato));
        when(acaoValorRepository.findValoresVigentesPorContrato(idContrato, dataRef))
                .thenReturn(List.of(av1, av2));

        List<ContratoApi.AcaoValorItem> itens = useCases.listarAcoesComValor(idContrato, dataRef);

        assertThat(itens).hasSize(2);
        // 100 * 1.25 = 125.00
        assertThat(itens.get(0).codigo()).isEqualTo("LIG01");
        assertThat(itens.get(0).valor()).isEqualByComparingTo(new BigDecimal("125.00"));
        // 50 * 1.25 = 62.50
        assertThat(itens.get(1).codigo()).isEqualTo("REL01");
        assertThat(itens.get(1).valor()).isEqualByComparingTo(new BigDecimal("62.50"));
    }

    @Test
    @DisplayName("listarAcoesComValor deve considerar apenas o valor mais recente quando houver múltiplas vigências")
    void deveConsiderarValorMaisRecente() {
        LocalDate dataRef = LocalDate.of(2026, 10, 1);
        AcaoValor avRecente = new AcaoValor(acao1, contrato, new BigDecimal("120.00"), LocalDate.of(2026, 6, 1));
        AcaoValor avAntigo = new AcaoValor(acao1, contrato, new BigDecimal("100.00"), LocalDate.of(2026, 1, 1));

        when(repository.findById(idContrato)).thenReturn(Optional.of(contrato));
        when(acaoValorRepository.findValoresVigentesPorContrato(idContrato, dataRef))
                .thenReturn(List.of(avRecente, avAntigo));

        List<ContratoApi.AcaoValorItem> itens = useCases.listarAcoesComValor(idContrato, dataRef);

        assertThat(itens).hasSize(1);
        // 120 * 1.25 = 150.00
        assertThat(itens.get(0).codigo()).isEqualTo("LIG01");
        assertThat(itens.get(0).valor()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("listarAcoesComValor deve retornar lista vazia se contrato estiver fora da vigência")
    void deveRetornarVazioSeContratoForaDaVigencia() {
        LocalDate dataAntes = LocalDate.of(2025, 12, 31);
        when(repository.findById(idContrato)).thenReturn(Optional.of(contrato));

        List<ContratoApi.AcaoValorItem> itens = useCases.listarAcoesComValor(idContrato, dataAntes);

        assertThat(itens).isEmpty();
    }

    @Test
    @DisplayName("listarAcoesComValor deve retornar lista vazia se contrato for nulo ou não encontrado")
    void deveRetornarVazioSeContratoNuloOuInexistente() {
        assertThat(useCases.listarAcoesComValor(null, LocalDate.now())).isEmpty();

        UUID inex = UUID.randomUUID();
        when(repository.findById(inex)).thenReturn(Optional.empty());
        assertThat(useCases.listarAcoesComValor(inex, LocalDate.now())).isEmpty();
    }
}
