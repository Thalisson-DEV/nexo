package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ItemPendenciaRequest;
import com.nexo.app.modules.estoque.application.dto.RegistrarPendenciaRequest;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendenciaSolicitacaoUseCasesTest {

    @Mock
    private SolicitacaoMaterialRepository solicitacoes;

    @Mock
    private MotivoPendenciaUseCases motivos;

    @InjectMocks
    private PendenciaSolicitacaoUseCases useCases;

    private MotivoPendencia motivoA;
    private MotivoPendencia motivoB;

    @BeforeEach
    void setUp() {
        motivoA = new MotivoPendencia("SEM SALDO", "Material sem estoque");
        ReflectionTestUtils.setField(motivoA, "id", UUID.randomUUID());

        motivoB = new MotivoPendencia("DEFEITO", "Material com avaria");
        ReflectionTestUtils.setField(motivoB, "id", UUID.randomUUID());
    }

    @Test
    void quandoTodasAsPendenciasDosMateriaisForemResolvidasAPendenciaDaSolicitacaoEResolvidaJunto() {
        SolicitacaoMaterial solicitacao = criarSolicitacaoComercialComDoisItens();
        UUID idSolicitacao = solicitacao.getId();
        ItemSolicitacao item1 = solicitacao.getItens().get(0);
        ItemSolicitacao item2 = solicitacao.getItens().get(1);

        when(solicitacoes.findById(idSolicitacao)).thenReturn(Optional.of(solicitacao));
        when(motivos.buscar(motivoA.getId())).thenReturn(motivoA);
        when(motivos.buscar(motivoB.getId())).thenReturn(motivoB);

        // 1. Registra pendência nos dois itens
        useCases.registrar(idSolicitacao, new RegistrarPendenciaRequest(
                null,
                null,
                null,
                null,
                List.of(
                        new ItemPendenciaRequest(item1.getId(), motivoA.getId(), "Justificativa item 1"),
                        new ItemPendenciaRequest(item2.getId(), motivoB.getId(), "Justificativa item 2")
                )
        ));

        assertThat(solicitacao.possuiPendencia()).isTrue();
        assertThat(item1.possuiPendencia()).isTrue();
        assertThat(item2.possuiPendencia()).isTrue();

        // 2. Remove pendência do item 1 -> ainda resta o item 2, solicitação continua com pendência do item 2
        useCases.remover(idSolicitacao, item1.getId());

        assertThat(item1.possuiPendencia()).isFalse();
        assertThat(item2.possuiPendencia()).isTrue();
        assertThat(solicitacao.possuiPendencia()).isTrue();
        assertThat(solicitacao.getMotivoPendencia()).isEqualTo(motivoB);
        assertThat(solicitacao.getJustificativaPendencia()).isEqualTo("Justificativa item 2");

        // 3. Remove pendência do item 2 -> todas as pendências foram resolvidas!
        // A pendência da movimentação/solicitação deve ser resolvida junto!
        useCases.remover(idSolicitacao, item2.getId());

        assertThat(item1.possuiPendencia()).isFalse();
        assertThat(item2.possuiPendencia()).isFalse();
        assertThat(solicitacao.possuiPendencia()).isFalse();
        assertThat(solicitacao.getMotivoPendencia()).isNull();
        assertThat(solicitacao.getJustificativaPendencia()).isNull();

        verify(solicitacoes, org.mockito.Mockito.atLeastOnce()).save(solicitacao);
    }

    @Test
    void registroGeralPropagaPendenciaParaMateriaisERemocaoGeralResolveTudo() {
        SolicitacaoMaterial solicitacao = criarSolicitacaoComercialComDoisItens();
        UUID idSolicitacao = solicitacao.getId();
        ItemSolicitacao item1 = solicitacao.getItens().get(0);
        ItemSolicitacao item2 = solicitacao.getItens().get(1);

        when(solicitacoes.findById(idSolicitacao)).thenReturn(Optional.of(solicitacao));
        when(motivos.buscar(motivoA.getId())).thenReturn(motivoA);

        // Registra pendência geral
        useCases.registrar(idSolicitacao, new RegistrarPendenciaRequest(
                motivoA.getId(),
                "Aguardando autorização geral",
                null,
                null,
                null
        ));

        assertThat(solicitacao.possuiPendencia()).isTrue();
        assertThat(item1.possuiPendencia()).isTrue();
        assertThat(item2.possuiPendencia()).isTrue();

        // Remove pendência geral (idItem == null)
        useCases.remover(idSolicitacao, null);

        assertThat(solicitacao.possuiPendencia()).isFalse();
        assertThat(item1.possuiPendencia()).isFalse();
        assertThat(item2.possuiPendencia()).isFalse();
    }

    private SolicitacaoMaterial criarSolicitacaoComercialComDoisItens() {
        TipoEstoque tipo = new TipoEstoque("FISICO");
        Estoque origem = comId(new Estoque("ALMOXARIFADO CENTRAL", tipo));
        Estoque destino = comId(new Estoque("ESTOQUE EQUIPE", new TipoEstoque("CAMPO")));
        destino.vincularEquipe(UUID.randomUUID());

        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(
                origem, destino, UUID.randomUUID(), LocalDate.now().plusDays(2));
        comId(solicitacao);

        Material mat1 = new Material("MAT-1", "Material 1", "UN", "MATR", null, null, false, UUID.randomUUID());
        Material mat2 = new Material("MAT-2", "Material 2", "UN", "MATR", null, null, false, UUID.randomUUID());

        solicitacao.adicionarItem(mat1, BigDecimal.TEN);
        solicitacao.adicionarItem(mat2, BigDecimal.valueOf(5));

        solicitacao.aprovar(UUID.randomUUID());

        comId(solicitacao.getItens().get(0));
        comId(solicitacao.getItens().get(1));

        return solicitacao;
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
