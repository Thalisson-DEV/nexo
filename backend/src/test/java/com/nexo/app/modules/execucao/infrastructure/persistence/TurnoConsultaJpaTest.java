package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.Turno;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Banco próprio: as consultas contam todos os turnos e notas, e o contexto compartilhado traz dados de outros testes.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:turno-consulta-jpa;DB_CLOSE_DELAY=-1")
class TurnoConsultaJpaTest {

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private ExecucaoComercialRepository execucaoRepository;

    @Test
    @Transactional
    void consultaTurnosEResumeProducaoPorTurno() {
        UUID idEquipe = UUID.randomUUID();
        UUID outraEquipe = UUID.randomUUID();
        Turno aberto = turnoRepository.saveAndFlush(new Turno(idEquipe, UUID.randomUUID()));
        Turno fechado = new Turno(idEquipe, UUID.randomUUID());
        fechado.fechar();
        fechado = turnoRepository.saveAndFlush(fechado);
        turnoRepository.saveAndFlush(new Turno(outraEquipe, UUID.randomUUID()));

        OffsetDateTime agora = OffsetDateTime.now();
        lancar(aberto, agora.minusMinutes(30), "100.00", true);
        lancar(aberto, agora.minusMinutes(10), "250.50", true);
        lancar(aberto, agora.minusMinutes(5), "999.00", false);

        var daEquipe = turnoRepository.findAll(TurnoSpecification.filtrarPor(
                        new ConsultaTurnos(Set.of(idEquipe), null, null, null, null, null)),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dataInicio")));
        assertThat(daEquipe.getTotalElements()).isEqualTo(2);

        var soAbertos = turnoRepository.findAll(TurnoSpecification.filtrarPor(
                new ConsultaTurnos(Set.of(idEquipe), StatusTurno.ABERTO, agora.minusDays(1), agora.plusDays(1),
                        agora.minusHours(1), agora.plusHours(1))));
        assertThat(soAbertos).extracting(Turno::getId).containsExactly(aberto.getId());

        var nenhumaEquipe = turnoRepository.findAll(TurnoSpecification.filtrarPor(
                new ConsultaTurnos(Set.of(), null, null, null, null, null)));
        assertThat(nenhumaEquipe).isEmpty();

        List<ProducaoDoTurno> producao = execucaoRepository.resumirPorTurnos(
                List.of(aberto.getId(), fechado.getId()), StatusExecucao.CONCLUIDO);
        assertThat(producao).singleElement().satisfies(p -> {
            assertThat(p.idTurno()).isEqualTo(aberto.getId());
            assertThat(p.notas()).isEqualTo(2L);
            assertThat(p.producao()).isEqualByComparingTo("350.50");
            assertThat(p.primeiroLancamento()).isBefore(p.ultimoLancamento());
        });

        var doTurno = execucaoRepository.findByIdTurnoAndAtivoTrueOrderByDataLancamentoAsc(aberto.getId());
        assertThat(doTurno).hasSize(3);
        assertThat(doTurno.getFirst().getReferenciaNota()).isEqualTo("NOTA-" + agora.minusMinutes(30).toEpochSecond());
    }

    private void lancar(Turno turno, OffsetDateTime quando, String valor, boolean concluir) {
        ExecucaoComercial execucao = new ExecucaoComercial("NOTA-" + quando.toEpochSecond(), turno.getIdEquipe(),
                turno.getId(), null, quando, null, null, null);
        if (concluir) {
            execucao.concluir(new BigDecimal(valor));
        }
        execucaoRepository.saveAndFlush(execucao);
    }
}
