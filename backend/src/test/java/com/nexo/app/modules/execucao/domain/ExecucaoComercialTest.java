package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.application.dto.OperacaoMaterial;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecucaoComercialTest {

    private static final BigDecimal LAT = new BigDecimal("-12.9714");
    private static final BigDecimal LON = new BigDecimal("-38.5014");

    private ExecucaoComercial novaExecucao() {
        return new ExecucaoComercial(
                "NOTA-123",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                OffsetDateTime.now(),
                LAT,
                LON,
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Lançamento de campo dispensa o município: a localização basta")
    void devePermitirExecucaoSemMunicipio() {
        ExecucaoComercial execucao = new ExecucaoComercial(
                "NOTA-456", UUID.randomUUID(), UUID.randomUUID(), null, OffsetDateTime.now(), LAT, LON, null);

        assertNull(execucao.getIdMunicipio());
        assertEquals(LAT, execucao.getLatitude());
    }

    @Test
    @DisplayName("Não deve permitir adicionar a mesma ação duas vezes na mesma execução")
    void naoDevePermitirAcaoDuplicada() {
        ExecucaoComercial execucao = novaExecucao();
        UUID idAcao = UUID.randomUUID();

        execucao.adicionarAcao(idAcao);

        assertThrows(InvarianteVioladaException.class, () -> execucao.adicionarAcao(idAcao));
    }

    @Test
    @DisplayName("Deve permitir adicionar materiais com quantidade positiva")
    void devePermitirMaterialValido() {
        ExecucaoComercial execucao = novaExecucao();

        execucao.adicionarMaterial(UUID.randomUUID(), new BigDecimal("10.5"), OperacaoMaterial.APLICADO, "SERIE123");

        assertEquals(1, execucao.getMateriais().size());
        assertEquals(new BigDecimal("10.5"), execucao.getMateriais().get(0).getQuantidade());
    }

    @Test
    @DisplayName("Não deve permitir material com quantidade zero ou negativa")
    void naoDevePermitirMaterialInvalido() {
        ExecucaoComercial execucao = novaExecucao();
        UUID idMaterial = UUID.randomUUID();

        assertThrows(InvarianteVioladaException.class,
                () -> execucao.adicionarMaterial(idMaterial, BigDecimal.ZERO, OperacaoMaterial.APLICADO, null));

        assertThrows(InvarianteVioladaException.class,
                () -> execucao.adicionarMaterial(idMaterial, new BigDecimal("-1"), OperacaoMaterial.APLICADO, null));
    }

    @Test
    @DisplayName("Deve concluir execução com o valor faturado, mantendo a data e a localização do lançamento")
    void deveConcluirExecucao() {
        OffsetDateTime lancamento = OffsetDateTime.parse("2026-10-09T10:15:00-03:00");
        ExecucaoComercial execucao = new ExecucaoComercial(
                "NOTA-789", UUID.randomUUID(), UUID.randomUUID(), null, lancamento, LAT, LON, null);
        BigDecimal valorCalculado = new BigDecimal("1500.50");
        execucao.adicionarAcao(UUID.randomUUID());

        assertEquals(StatusExecucao.INICIADO, execucao.getStatusExecucao());
        assertNotNull(execucao.getReferenciaNotaHash());

        execucao.concluir(valorCalculado);

        assertEquals(StatusExecucao.CONCLUIDO, execucao.getStatusExecucao());
        assertEquals(valorCalculado, execucao.getValorTotalFaturado());
        assertEquals(lancamento, execucao.getDataLancamento());
        assertEquals(LAT, execucao.getLatitude());
        assertEquals(LON, execucao.getLongitude());
    }

    @Test
    @DisplayName("A data de lançamento é obrigatória")
    void exigeDataDeLancamento() {
        assertThrows(NullPointerException.class, () -> new ExecucaoComercial(
                "NOTA-000", UUID.randomUUID(), UUID.randomUUID(), null, null, LAT, LON, null));
    }

    @Test
    @DisplayName("Deve concluir execução sem ações, pois as ações são opcionais")
    void deveConcluirSemAcoes() {
        ExecucaoComercial execucao = novaExecucao();

        execucao.concluir(BigDecimal.ZERO);

        assertEquals(StatusExecucao.CONCLUIDO, execucao.getStatusExecucao());
        assertTrue(execucao.getAcoes().isEmpty());
    }

    @Test
    @DisplayName("Não deve permitir concluir uma execução já concluída")
    void naoDeveConcluirDuasVezes() {
        ExecucaoComercial execucao = novaExecucao();
        execucao.concluir(BigDecimal.ZERO);

        assertThrows(InvarianteVioladaException.class, () -> execucao.concluir(BigDecimal.ZERO));
    }

    private ExecucaoComercial lancamento(String referencia, TipoNota tipo) {
        return new ExecucaoComercial(referencia, UUID.randomUUID(), UUID.randomUUID(), null,
                OffsetDateTime.now(), LAT, LON, UUID.randomUUID(), tipo);
    }

    @Test
    @DisplayName("Lançamento sem tipo é nota comum, sem número provisório")
    void lancamentoSemTipoEhComum() {
        ExecucaoComercial execucao = novaExecucao();

        assertEquals(TipoNota.COMUM, execucao.getTipoNota());
        assertNull(execucao.getReferenciaProvisoria());
        assertFalse(execucao.isAguardandoNumeroOficial());
    }

    @Test
    @DisplayName("Nota avulsa guarda o número provisório e espera o oficial")
    void avulsaGuardaNumeroProvisorio() {
        ExecucaoComercial execucao = lancamento("AV-EQ12-261019-01", TipoNota.AVULSA);

        assertEquals("AV-EQ12-261019-01", execucao.getReferenciaNota());
        assertEquals("AV-EQ12-261019-01", execucao.getReferenciaProvisoria());
        assertTrue(execucao.isAguardandoNumeroOficial());
    }

    @Test
    @DisplayName("Número oficial substitui a referência da avulsa e mantém o provisório")
    void numeroOficialSubstituiProvisorio() {
        ExecucaoComercial execucao = lancamento("AV-EQ12-261019-01", TipoNota.AVULSA);
        String hashProvisorio = execucao.getReferenciaNotaHash();
        OffsetDateTime agora = OffsetDateTime.now();
        UUID idUsuario = UUID.randomUUID();

        execucao.informarNumeroOficial(" 123456789 ", idUsuario, agora);

        assertEquals("123456789", execucao.getReferenciaNota());
        assertEquals("AV-EQ12-261019-01", execucao.getReferenciaProvisoria());
        assertNotEquals(hashProvisorio, execucao.getReferenciaNotaHash());
        assertEquals(agora, execucao.getDataNumeroOficial());
        assertEquals(idUsuario, execucao.getIdUsuarioNumeroOficial());
        assertFalse(execucao.isAguardandoNumeroOficial());
    }

    @Test
    @DisplayName("Só a avulsa recebe número oficial, e ele não pode ser vazio nem o provisório")
    void numeroOficialValidado() {
        OffsetDateTime agora = OffsetDateTime.now();
        ExecucaoComercial comum = novaExecucao();
        ExecucaoComercial avulsa = lancamento("AV-1", TipoNota.AVULSA);

        assertThrows(InvarianteVioladaException.class, () -> comum.informarNumeroOficial("999", null, agora));
        assertThrows(InvarianteVioladaException.class, () -> avulsa.informarNumeroOficial("  ", null, agora));
        assertThrows(InvarianteVioladaException.class, () -> avulsa.informarNumeroOficial("av-1", null, agora));
    }

    @Test
    @DisplayName("Lançamento sem número é recusado")
    void lancamentoSemNumeroRecusado() {
        assertThrows(InvarianteVioladaException.class, () -> lancamento(" ", TipoNota.OCORRENCIA));
    }

    @Test
    @DisplayName("Material da Concessionária só entra na ocorrência e fica fora dos materiais da equipe")
    void materialDaConcessionariaSoNaOcorrencia() {
        ExecucaoComercial comum = novaExecucao();
        UUID idMaterial = UUID.randomUUID();
        assertThrows(InvarianteVioladaException.class, () -> comum.adicionarMaterial(
                idMaterial, BigDecimal.ONE, OperacaoMaterial.APLICADO, null, OrigemMaterial.CONCESSIONARIA));

        ExecucaoComercial ocorrencia = lancamento("OC-2026/AB12", TipoNota.OCORRENCIA);
        ocorrencia.adicionarMaterial(idMaterial, BigDecimal.ONE, OperacaoMaterial.APLICADO, null, OrigemMaterial.CONCESSIONARIA);
        ocorrencia.adicionarMaterial(UUID.randomUUID(), BigDecimal.TEN, OperacaoMaterial.RETIRADO, null, OrigemMaterial.CONCESSIONARIA);
        ocorrencia.adicionarMaterial(UUID.randomUUID(), BigDecimal.ONE, OperacaoMaterial.APLICADO, null, OrigemMaterial.EQUIPE);

        assertEquals(3, ocorrencia.getMateriais().size());
        assertEquals(1, ocorrencia.getMateriaisDaEquipe().size());
        assertEquals(OrigemMaterial.EQUIPE, ocorrencia.getMateriaisDaEquipe().get(0).getOrigem());
    }

    @Test
    @DisplayName("Alterar a nota troca só o que mudou e devolve o que entrou e o que saiu")
    void alterarLancamentosDevolveADiferenca() {
        ExecucaoComercial execucao = concluida();
        UUID mantida = UUID.randomUUID();
        UUID removida = UUID.randomUUID();
        UUID nova = UUID.randomUUID();
        UUID cabo = UUID.randomUUID();
        UUID errado = UUID.randomUUID();
        UUID certo = UUID.randomUUID();
        execucao.adicionarAcao(mantida);
        execucao.adicionarAcao(removida);
        execucao.adicionarMaterial(cabo, new BigDecimal("10"), OperacaoMaterial.APLICADO, null);
        execucao.adicionarMaterial(errado, BigDecimal.ONE, OperacaoMaterial.APLICADO, null);
        execucao.concluir(BigDecimal.TEN);

        AlteracaoLancamentos alteracao = execucao.alterarLancamentos(List.of(mantida, nova), List.of(
                new MaterialLancado(cabo, new BigDecimal("7"), OperacaoMaterial.APLICADO, null, null),
                new MaterialLancado(certo, BigDecimal.ONE, OperacaoMaterial.APLICADO, null, null)));

        assertEquals(List.of(nova), alteracao.acoesAdicionadas());
        assertEquals(List.of(removida), alteracao.acoesRemovidas());
        assertEquals(2, alteracao.materiaisRemovidos().size());
        assertEquals(0, new BigDecimal("3").compareTo(alteracao.materiaisRemovidos().stream()
                .filter(m -> m.idMaterial().equals(cabo)).findFirst().orElseThrow().quantidade()));
        assertEquals(List.of(certo), alteracao.materiaisAdicionados().stream().map(MaterialLancado::idMaterial).toList());
        assertEquals(2, execucao.getAcoes().size());
        assertEquals(2, execucao.getMateriais().size());
        assertEquals(0, new BigDecimal("7").compareTo(execucao.getMateriais().stream()
                .filter(m -> m.getIdMaterial().equals(cabo)).findFirst().orElseThrow().getQuantidade()));
    }

    @Test
    @DisplayName("Alteração sem mudança é recusada")
    void alteracaoSemMudancaERecusada() {
        ExecucaoComercial execucao = concluida();
        UUID acao = UUID.randomUUID();
        execucao.adicionarAcao(acao);
        execucao.concluir(BigDecimal.ZERO);

        assertThrows(InvarianteVioladaException.class, () -> execucao.alterarLancamentos(List.of(acao), List.of()));
    }

    @Test
    @DisplayName("Avulsa: trocar o número pelo de verdade é informar o oficial; virar avulsa guarda o provisório")
    void corrigirNumeroDaAvulsa() {
        ExecucaoComercial avulsa = new ExecucaoComercial("AV-1", UUID.randomUUID(), UUID.randomUUID(), null,
                OffsetDateTime.now().minusDays(1), null, null, null, TipoNota.AVULSA);
        avulsa.concluir(BigDecimal.ZERO);
        UUID usuario = UUID.randomUUID();

        assertTrue(avulsa.corrigirDados(new DadosNota("9001", null, null, null, null), usuario, OffsetDateTime.now()));
        assertEquals("9001", avulsa.getReferenciaNota());
        assertFalse(avulsa.isAguardandoNumeroOficial());

        ExecucaoComercial comum = concluida();
        comum.concluir(BigDecimal.ZERO);
        comum.corrigirDados(new DadosNota(null, TipoNota.AVULSA, null, null, null), usuario, OffsetDateTime.now());
        assertTrue(comum.isAguardandoNumeroOficial());
        assertEquals(comum.getReferenciaNota(), comum.getReferenciaProvisoria());
    }

    @Test
    @DisplayName("A data do lançamento não vai para o futuro")
    void dataNoFuturoRecusada() {
        ExecucaoComercial execucao = concluida();
        execucao.concluir(BigDecimal.ZERO);
        OffsetDateTime agora = OffsetDateTime.now();

        assertThrows(InvarianteVioladaException.class, () -> execucao.corrigirDados(
                new DadosNota(null, null, null, null, agora.plusDays(1)), null, agora));
    }

    @Test
    @DisplayName("Trocar a série de linha não colide com a série que está saindo")
    void serieTrocadaDeOperacaoNaoColide() {
        ExecucaoComercial execucao = concluida();
        UUID medidor = UUID.randomUUID();
        execucao.adicionarMaterial(medidor, BigDecimal.ONE, OperacaoMaterial.APLICADO, "SN-1");
        execucao.concluir(BigDecimal.ZERO);

        AlteracaoLancamentos alteracao = execucao.alterarLancamentos(List.of(), List.of(
                new MaterialLancado(medidor, BigDecimal.ONE, OperacaoMaterial.RETIRADO, "SN-1", null)));

        assertEquals(1, alteracao.materiaisRemovidos().size());
        assertEquals(1, alteracao.materiaisAdicionados().size());
        assertEquals(OperacaoMaterial.RETIRADO, execucao.getMateriais().getFirst().getOperacao());
    }

    @Test
    @DisplayName("Registrar a alteração marca a nota como alterada e guarda quem e quando")
    void registrarAlteracaoMarcaANota() {
        ExecucaoComercial execucao = concluida();
        execucao.concluir(BigDecimal.ZERO);
        UUID usuario = UUID.randomUUID();
        OffsetDateTime quando = OffsetDateTime.now();

        execucao.registrarAlteracao(new BigDecimal("42.00"), usuario, quando);

        assertTrue(execucao.isAlterada());
        assertEquals(1, execucao.getTotalAlteracoes());
        assertEquals(usuario, execucao.getIdUsuarioAlteracao());
        assertEquals(quando, execucao.getDataAlteracao());
        assertEquals(StatusExecucao.CONCLUIDO, execucao.getStatusExecucao());
    }

    private ExecucaoComercial concluida() {
        return novaExecucao();
    }
}
