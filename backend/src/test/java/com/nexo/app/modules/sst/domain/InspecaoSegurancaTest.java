package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de invariantes do agregado de execução {@link InspecaoSeguranca}.
 * Cobre a regra-chave: os colaboradores de um desvio devem ser um subconjunto
 * dos colaboradores marcados como inspecionados.
 */
class InspecaoSegurancaTest {

    private final UUID idFormulario = UUID.randomUUID();
    private final UUID idTipoInspecao = UUID.randomUUID();
    private final UUID idProcesso = UUID.randomUUID();
    private final UUID idInspetor = UUID.randomUUID();

    private InspecaoSeguranca novaInspecao() {
        return new InspecaoSeguranca(
                idFormulario,
                idTipoInspecao,
                idProcesso,
                idInspetor,
                null,                 // veículo opcional
                null,                 // equipe opcional
                UUID.randomUUID(),
                OffsetDateTime.now(),
                "Inspeção de rotina");
    }

    private EvidenciaInspecao novaEvidencia() {
        return new EvidenciaInspecao(idInspetor, "foto.jpg", "image/jpeg", 3,
                "sst/inspecoes/evidencias/" + UUID.randomUUID() + ".jpg");
    }

    @Test
    @DisplayName("Deve nascer EM_ANDAMENTO com o inspetor (usuário logado) vinculado")
    void deveNascerEmAndamento() {
        InspecaoSeguranca inspecao = novaInspecao();

        assertEquals(StatusInspecao.EM_ANDAMENTO, inspecao.getStatus());
        assertEquals(idInspetor, inspecao.getIdInspetor());
        assertEquals(idFormulario, inspecao.getIdFormulario());
    }

    @Test
    @DisplayName("Não deve permitir o mesmo colaborador inspecionado duas vezes")
    void naoDevePermitirColaboradorInspecionadoDuplicado() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID idColaborador = UUID.randomUUID();

        inspecao.adicionarColaboradorInspecionado(idColaborador);

        assertThrows(InvarianteVioladaException.class,
                () -> inspecao.adicionarColaboradorInspecionado(idColaborador));
        assertEquals(1, inspecao.getColaboradoresInspecionados().size());
    }

    @Test
    @DisplayName("Não deve permitir responder a mesma pergunta duas vezes")
    void naoDevePermitirRespostaDuplicada() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID idPergunta = UUID.randomUUID();

        inspecao.responder(idPergunta, List.of(UUID.randomUUID()), null, null, null);

        assertThrows(InvarianteVioladaException.class,
                () -> inspecao.responder(idPergunta, List.of(UUID.randomUUID()), null, null, null));
    }

    @Test
    @DisplayName("Deve registrar resposta de escolha guardando as opções selecionadas")
    void deveRegistrarRespostaDeEscolha() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID idPergunta = UUID.randomUUID();
        UUID idOpcao = UUID.randomUUID();

        RespostaInspecao resposta = inspecao.responder(idPergunta, List.of(idOpcao), null, null, null);

        assertEquals(1, inspecao.getRespostas().size());
        assertEquals(idPergunta, resposta.getIdPergunta());
        assertEquals(1, resposta.getOpcoesSelecionadas().size());
        assertEquals(idOpcao, resposta.getOpcoesSelecionadas().get(0).getIdOpcaoResposta());
    }

    @Test
    @DisplayName("Deve registrar um desvio atribuído a colaboradores que estão sendo inspecionados")
    void deveRegistrarDesvioValido() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID colabA = UUID.randomUUID();
        UUID colabB = UUID.randomUUID();
        inspecao.adicionarColaboradorInspecionado(colabA);
        inspecao.adicionarColaboradorInspecionado(colabB);

        UUID idPergunta = UUID.randomUUID();
        UUID idOpcao = UUID.randomUUID();

        DesvioSeguranca desvio = inspecao.registrarDesvio(
                idPergunta, idOpcao, GravidadeDesvio.ALTA,
                "Câmera danificada", "Substituir o equipamento",
                LocalDate.now().plusDays(7), List.of(colabA));

        assertEquals(1, inspecao.getDesvios().size());
        assertEquals(GravidadeDesvio.ALTA, desvio.getGravidade());
        assertEquals(StatusDesvio.ABERTO, desvio.getStatus());
        assertEquals(1, desvio.getColaboradores().size());
        assertEquals(colabA, desvio.getColaboradores().get(0).getIdColaborador());
    }

    @Test
    @DisplayName("Não deve atribuir desvio a colaborador que não está entre os inspecionados")
    void naoDeveAtribuirDesvioForaDosInspecionados() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID colabInspecionado = UUID.randomUUID();
        UUID colabForaDaLista = UUID.randomUUID();
        inspecao.adicionarColaboradorInspecionado(colabInspecionado);

        assertThrows(InvarianteVioladaException.class, () -> inspecao.registrarDesvio(
                UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.MEDIA,
                "Desvio", "Ação", null, List.of(colabForaDaLista)));
    }

    @Test
    @DisplayName("Não deve registrar desvio sem nenhum colaborador responsável")
    void naoDeveRegistrarDesvioSemColaborador() {
        InspecaoSeguranca inspecao = novaInspecao();
        inspecao.adicionarColaboradorInspecionado(UUID.randomUUID());

        assertThrows(InvarianteVioladaException.class, () -> inspecao.registrarDesvio(
                UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.BAIXA,
                "Desvio", "Ação", null, List.of()));
    }

    @Test
    @DisplayName("Deve concluir uma inspeção com colaboradores inspecionados e evidência fotográfica")
    void deveConcluirInspecao() {
        InspecaoSeguranca inspecao = novaInspecao();
        inspecao.adicionarColaboradorInspecionado(UUID.randomUUID());
        inspecao.vincularEvidencia(novaEvidencia());

        inspecao.concluir();

        assertEquals(StatusInspecao.CONCLUIDA, inspecao.getStatus());
    }

    @Test
    @DisplayName("Não deve concluir uma inspeção sem nenhum colaborador inspecionado")
    void naoDeveConcluirSemColaboradores() {
        InspecaoSeguranca inspecao = novaInspecao();
        inspecao.vincularEvidencia(novaEvidencia());

        assertThrows(InvarianteVioladaException.class, inspecao::concluir);
    }

    @Test
    @DisplayName("Não deve concluir uma inspeção sem evidência fotográfica")
    void naoDeveConcluirSemEvidencia() {
        InspecaoSeguranca inspecao = novaInspecao();
        inspecao.adicionarColaboradorInspecionado(UUID.randomUUID());

        assertThrows(InvarianteVioladaException.class, inspecao::concluir);
    }

    @Test
    @DisplayName("Não deve concluir uma inspeção já concluída")
    void naoDeveConcluirDuasVezes() {
        InspecaoSeguranca inspecao = novaInspecao();
        inspecao.adicionarColaboradorInspecionado(UUID.randomUUID());
        inspecao.vincularEvidencia(novaEvidencia());
        inspecao.concluir();

        assertThrows(InvarianteVioladaException.class, inspecao::concluir);
    }

    @Test
    @DisplayName("Não deve reaproveitar a mesma evidência em duas inspeções")
    void naoDeveReaproveitarEvidencia() {
        InspecaoSeguranca primeira = novaInspecao();
        InspecaoSeguranca segunda = novaInspecao();
        EvidenciaInspecao evidencia = novaEvidencia();
        primeira.vincularEvidencia(evidencia);

        assertThrows(InvarianteVioladaException.class, () -> segunda.vincularEvidencia(evidencia));
        assertEquals(0, segunda.getEvidencias().size());
    }

    @Test
    @DisplayName("Não deve remover a única evidência de uma inspeção")
    void naoDeveRemoverUltimaEvidencia() {
        InspecaoSeguranca inspecao = novaInspecao();
        EvidenciaInspecao evidencia = novaEvidencia();
        inspecao.vincularEvidencia(evidencia);

        assertThrows(InvarianteVioladaException.class, () -> inspecao.removerEvidencia(evidencia));
        assertEquals(1, inspecao.getEvidencias().size());
    }

    @Test
    @DisplayName("Deve remover uma evidência quando há outra que comprove a inspeção")
    void deveRemoverEvidenciaComOutraDisponivel() {
        InspecaoSeguranca inspecao = novaInspecao();
        EvidenciaInspecao primeira = novaEvidencia();
        inspecao.vincularEvidencia(primeira);
        inspecao.vincularEvidencia(novaEvidencia());

        inspecao.removerEvidencia(primeira);

        assertEquals(1, inspecao.getEvidencias().size());
    }

    @Test
    @DisplayName("Não deve aceitar documento como evidência da inspeção")
    void naoDeveAceitarDocumentoComoEvidencia() {
        assertThrows(InvarianteVioladaException.class, () -> new EvidenciaInspecao(
                idInspetor, "laudo.pdf", "application/pdf", 10, "sst/inspecoes/evidencias/x.pdf"));
    }

    @Test
    @DisplayName("Deve preencher automaticamente a data de resolução")
    void devePreencherDataResolucao() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID colaborador = UUID.randomUUID();
        inspecao.adicionarColaboradorInspecionado(colaborador);
        DesvioSeguranca desvio = inspecao.registrarDesvio(
                UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.MEDIA,
                "Desvio", "Ação", LocalDate.now(), List.of(colaborador));

        desvio.atualizarTratamento(StatusDesvio.RESOLVIDO, null, null);

        assertNotNull(desvio.getDataResolucao());
        assertEquals(StatusDesvio.RESOLVIDO, desvio.getStatus());
    }

    @Test
    @DisplayName("Deve limpar a data de resolução ao voltar para pendente")
    void deveLimparDataResolucaoAoVoltarParaPendente() {
        InspecaoSeguranca inspecao = novaInspecao();
        UUID colaborador = UUID.randomUUID();
        inspecao.adicionarColaboradorInspecionado(colaborador);
        DesvioSeguranca desvio = inspecao.registrarDesvio(
                UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.MEDIA,
                "Desvio", "Ação", LocalDate.now(), List.of(colaborador));
        desvio.atualizarTratamento(StatusDesvio.RESOLVIDO, null, null);

        desvio.atualizarTratamento(StatusDesvio.EM_TRATAMENTO, null, null);

        assertNull(desvio.getDataResolucao());
    }
}
