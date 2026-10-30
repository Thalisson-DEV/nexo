package com.nexo.app.shared.infrastructure.persistence;

import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.domain.DescontoItem;
import com.nexo.app.modules.desconto.domain.MotivoDesconto;
import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.modules.desconto.domain.TipoEquipamento;
import com.nexo.app.shared.domain.AuditIgnore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntitySnapshotFactoryTest {

    private static final class RegistroSensivel {
        private final UUID id = UUID.randomUUID();

        @AuditIgnore
        private final String conteudo = "conteúdo que não pode ir para logs_sistema";

        private final String status = "REGISTRADO";
    }

    private final UUID colaboradorId = UUID.randomUUID();
    private final UUID solicitanteId = UUID.randomUUID();
    private final UUID motivoId = UUID.randomUUID();
    private final UUID descontoId = UUID.randomUUID();

    private Desconto buildDescontoComItens() {
        var motivo = new MotivoDesconto("Mau Uso", 5);
        ReflectionTestUtils.setField(motivo, "id", motivoId);

        var equipamento = new TipoEquipamento("Ferramental");
        ReflectionTestUtils.setField(equipamento, "id", UUID.randomUUID());

        var desconto = new Desconto(colaboradorId, motivo, "Obs", 3, OffsetDateTime.now(), solicitanteId);
        ReflectionTestUtils.setField(desconto, "id", descontoId);
        desconto.adicionarItem("Alicate", equipamento, new BigDecimal("150.00"));
        desconto.adicionarItem("Chave de fenda", equipamento, new BigDecimal("50.00"));
        return desconto;
    }

    @Test
    @DisplayName("Não estoura recursão infinita no lado pai de uma relação bidirecional")
    void deveGerarSnapshotDoPaiSemRecursao() {
        Map<String, Object> snapshot = EntitySnapshotFactory.criar(buildDescontoComItens());

        assertNotNull(snapshot);
        assertEquals(descontoId.toString(), snapshot.get("id"));
        assertEquals(colaboradorId.toString(), snapshot.get("colaboradorId"));
        assertEquals(new BigDecimal("200.00"), snapshot.get("valorSugerido"));
        assertEquals(StatusDesconto.SOLICITADO.name(), snapshot.get("status"));
    }

    @Test
    @DisplayName("Não estoura recursão infinita no lado filho — cenário exato do StackOverflow em produção")
    void deveGerarSnapshotDoFilhoSemRecursao() {
        DescontoItem item = buildDescontoComItens().getItens().get(0);

        Map<String, Object> snapshot = EntitySnapshotFactory.criar(item);

        assertNotNull(snapshot);
        assertEquals("Alicate", snapshot.get("descricaoMaterial"));
        assertEquals(new BigDecimal("150.00"), snapshot.get("valor"));
    }

    @Test
    @DisplayName("Relações *ToOne viram apenas o id, sem serializar a entidade associada")
    void deveReduzirAssociacaoSimplesAoIdentificador() {
        Map<String, Object> snapshot = EntitySnapshotFactory.criar(buildDescontoComItens());

        assertEquals(motivoId.toString(), snapshot.get("motivoId"));
        assertFalse(snapshot.containsKey("motivo"), "A entidade associada não deve ser percorrida.");
    }

    @Test
    @DisplayName("Coleções *ToMany ficam fora do snapshot — cada filho gera o próprio log")
    void deveOmitirColecoesDeEntidades() {
        Map<String, Object> snapshot = EntitySnapshotFactory.criar(buildDescontoComItens());

        assertFalse(snapshot.containsKey("itens"));
        assertFalse(snapshot.containsKey("anexos"));
    }

    @Test
    @DisplayName("O campo transiente de snapshot não é auditado")
    void deveIgnorarCampoTransiente() {
        Map<String, Object> snapshot = EntitySnapshotFactory.criar(buildDescontoComItens());

        assertFalse(snapshot.containsKey("snapshotAnterior"));
    }

    @Test
    @DisplayName("Datas e enums são normalizados para texto aceito pelo JSONB")
    void deveNormalizarTiposParaJsonb() {
        Map<String, Object> snapshot = EntitySnapshotFactory.criar(buildDescontoComItens());

        assertInstanceOf(String.class, snapshot.get("dataOcorrencia"));
        assertInstanceOf(String.class, snapshot.get("status"));
        assertInstanceOf(Boolean.class, snapshot.get("ativo"));
        assertNull(snapshot.get("dataValidacao"));
    }

    @Test
    @DisplayName("@AuditIgnore omite o conteúdo sensível e preserva a auditoria estrutural")
    void deveOmitirCampoMarcadoComAuditIgnore() {
        Map<String, Object> snapshot = EntitySnapshotFactory.criar(new RegistroSensivel());

        assertFalse(snapshot.containsKey("conteudo"));
        assertEquals("REGISTRADO", snapshot.get("status"));
        assertNotNull(snapshot.get("id"));
    }
}
