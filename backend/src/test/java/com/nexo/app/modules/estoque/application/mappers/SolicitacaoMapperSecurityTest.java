package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoResponse;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SolicitacaoMapperSecurityTest {

    @Test
    void preservaCamposLegadosMasNuncaMapeiaValoresDasAssinaturas() {
        TipoEstoque tipo = comId(new TipoEstoque("FISICO"));
        Estoque origem = comId(new Estoque("ORIGEM", tipo));
        Estoque destino = comId(new Estoque("DESTINO", tipo));
        Material material = new Material(
                "MAT-SEC", "Material", "UN", "MATR", null, null,
                false, UUID.randomUUID());
        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial(origem, destino, UUID.randomUUID());
        solicitacao.adicionarItem(material, BigDecimal.ONE);
        ReflectionTestUtils.setField(solicitacao.getItens().getFirst(), "id", UUID.randomUUID());
        solicitacao.aprovar(UUID.randomUUID());
        solicitacao.registrarEntrega(
                UUID.randomUUID(), "assinatura-almox-secreta", "assinatura-equipe-secreta",
                Map.of(solicitacao.getItens().getFirst().getId(),
                        new SolicitacaoMaterial.ItemEntregaInfo(BigDecimal.ONE, null, null)),
                List.of());

        SolicitacaoMapper mapper = Mappers.getMapper(SolicitacaoMapper.class);
        ReflectionTestUtils.setField(mapper, "estoqueMapper", mock(EstoqueMapper.class));
        ReflectionTestUtils.setField(mapper, "materialMapper", mock(MaterialMapper.class));
        ReflectionTestUtils.setField(mapper, "motivoRejeicaoMapper", Mappers.getMapper(MotivoRejeicaoMapper.class));

        SolicitacaoResponse response = mapper.toResponse(solicitacao);

        assertThat(response.assinaturaAlmox()).isNull();
        assertThat(response.assinaturaEquipe()).isNull();
        assertThat(Arrays.stream(SolicitacaoResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .contains("assinaturaAlmox", "assinaturaEquipe");
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
