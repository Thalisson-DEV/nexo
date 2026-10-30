package com.nexo.app.modules.chamado.application.usecases;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoRequest;
import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import com.nexo.app.modules.chamado.domain.TopicoChamado;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.EquipeAtendimentoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.TopicoChamadoRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Percorre o caminho real de gravação de uma interação, com JPA de verdade.
 *
 * <p>Existe por causa de um bug encontrado em uso: chamar {@code save()} em um chamado
 * <b>já gerenciado</b> cai em {@code merge()}, e o merge de uma coleção com filhos
 * transientes persiste <em>cópias</em> — as cópias recebem id, e a instância que o
 * domínio devolveu continua sem nenhum. O sintoma era a marcação recusar a mensagem
 * com "Não foi possível identificar o texto da marcação"; o id nulo também vazava para
 * a resposta da API.</p>
 *
 * <p>Testes de caso de uso com repositório mockado <b>não alcançam</b> esta classe de
 * erro, porque o mock devolve a mesma instância e nunca há merge. Por isso este sobe o
 * contexto e passa pelo caso de uso, em vez de atalhar pelo repositório: é o caminho de
 * produção que precisa ficar guardado.</p>
 */
@SpringBootTest
@Transactional
class ChamadoInteracaoPersistenciaTest {

    @Autowired
    private ChamadoUseCases useCases;
    @Autowired
    private ChamadoRepository chamadoRepository;
    @Autowired
    private TopicoChamadoRepository topicoRepository;
    @Autowired
    private EquipeAtendimentoRepository equipeRepository;

    /** O caso relatado: o solicitante responde marcando alguém. */
    @Test
    void respostaDoSolicitanteComMarcacaoRecebeIdEnaoEhRecusada() {
        UUID solicitanteId = UUID.randomUUID();
        Chamado chamado = chamadoPersistido(solicitanteId, null);

        var interacao = useCases.responderMeuChamado(chamado.getId(),
                new InteracaoChamadoRequest("@USR678909 oiiii", false), solicitanteId);

        assertThat(interacao.id()).isNotNull();
        assertThat(interacao.mensagem()).isEqualTo("@USR678909 oiiii");
    }

    @Test
    void interacaoDoAtendenteComMarcacaoRecebeId() {
        UUID atendenteId = UUID.randomUUID();
        Chamado chamado = chamadoPersistido(UUID.randomUUID(), atendenteId);

        var interacao = useCases.adicionarInteracaoAtendimento(chamado.getId(),
                new InteracaoChamadoRequest("@USR678909 assume aí", false), atendenteId);

        assertThat(interacao.id()).isNotNull();
    }

    /**
     * Comentário interno é o caminho que valida visibilidade antes de gravar — sem o id
     * do texto ele nem chegava a essa validação.
     */
    @Test
    void comentarioInternoComMarcacaoRecebeId() {
        UUID atendenteId = UUID.randomUUID();
        Chamado chamado = chamadoPersistido(UUID.randomUUID(), atendenteId);

        var interacao = useCases.adicionarInteracaoAtendimento(chamado.getId(),
                new InteracaoChamadoRequest("@USR678909 confere o log", true), atendenteId);

        assertThat(interacao.id()).isNotNull();
        assertThat(interacao.interno()).isTrue();
    }

    private Chamado chamadoPersistido(UUID solicitanteId, UUID membroDaEquipe) {
        Set<UUID> membros = membroDaEquipe == null ? Set.of() : Set.of(membroDaEquipe);
        EquipeAtendimento equipe = equipeRepository.save(new EquipeAtendimento(
                "CENTRAL-" + sufixo(), "Central de atendimento",
                "Equipe para teste de persistência.", membros));
        TopicoChamado topico = topicoRepository.save(new TopicoChamado(
                "ACESSO-" + sufixo(), "Acesso e senha", "Login, senha e autenticação.",
                PrioridadeChamado.NORMAL, equipe, 2, 8, true, 72, 10));

        return chamadoRepository.saveAndFlush(new Chamado(topico, "Falha no acesso",
                "O sistema informa credenciais inválidas.", PrioridadeChamado.ALTA, solicitanteId));
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
