package com.nexo.app.modules.estoque.application.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoSummaryResponse;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.MotivoRejeicao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SolicitacaoMapperContratoFrontendTest {

    @Test
    void projetaContextoExplicitoNosRootsDeListagemEDetalhe() {
        TipoEstoque tipo = comId(new TipoEstoque("FISICO"));
        Estoque origem = comId(new Estoque("ORIGEM", tipo));
        Estoque destino = comId(new Estoque("DESTINO", tipo));
        SolicitacaoMaterial solicitacao = comId(new SolicitacaoMaterial(origem, destino, UUID.randomUUID()));
        SolicitacaoMapper mapper = Mappers.getMapper(SolicitacaoMapper.class);
        ReflectionTestUtils.setField(mapper, "estoqueMapper", mock(EstoqueMapper.class));
        ReflectionTestUtils.setField(mapper, "materialMapper", mock(MaterialMapper.class));
        ReflectionTestUtils.setField(mapper, "motivoRejeicaoMapper", Mappers.getMapper(MotivoRejeicaoMapper.class));

        SolicitacaoResponse detalhe = mapper.toResponse(solicitacao);
        SolicitacaoSummaryResponse resumo = mapper.toSummaryResponse(solicitacao);

        assertThat(detalhe.contexto()).isEqualTo(ContextoSolicitacaoMaterial.COMERCIAL);
        assertThat(resumo.getContexto()).isEqualTo(ContextoSolicitacaoMaterial.COMERCIAL);
    }

    @Test
    void detalheRejeitadoSerializaSnapshotDoMotivoSemEntidadeJpa() throws Exception {
        TipoEstoque tipo = comId(new TipoEstoque("FISICO"));
        SolicitacaoMaterial solicitacao = comId(new SolicitacaoMaterial(
                comId(new Estoque("ORIGEM", tipo)), comId(new Estoque("DESTINO", tipo)), UUID.randomUUID()));
        MotivoRejeicao motivo = comId(new MotivoRejeicao("Sem estoque", "Material indisponivel"));
        solicitacao.rejeitar(motivo, "Sem saldo no almoxarifado");
        SolicitacaoMapper mapper = Mappers.getMapper(SolicitacaoMapper.class);
        ReflectionTestUtils.setField(mapper, "estoqueMapper", mock(EstoqueMapper.class));
        ReflectionTestUtils.setField(mapper, "materialMapper", mock(MaterialMapper.class));
        ReflectionTestUtils.setField(mapper, "motivoRejeicaoMapper", Mappers.getMapper(MotivoRejeicaoMapper.class));

        SolicitacaoResponse resposta = mapper.toResponse(solicitacao);
        motivo.atualizar("Alterado", "Descricao alterada");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(resposta);

        assertThat(resposta.motivoRejeicao().id()).isEqualTo(motivo.getId());
        assertThat(resposta.motivoRejeicao().nome()).isEqualTo("Sem estoque");
        assertThat(json).contains("\"motivoRejeicao\"", "\"nome\":\"Sem estoque\"");
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
