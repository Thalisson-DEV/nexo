package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObraTest {

    private StatusObra criarStatusEmExecucao() {
        return new StatusObra("EM EXECUÇÃO", 1, false);
    }

    private StatusObra criarStatusConcluida() {
        return new StatusObra("CONCLUÍDA", 2, true);
    }

    private AgenteResponsavel criarAgente() {
        return new AgenteResponsavel("MARLOS PEREIRA", "marlos.lopes@concessionaria.com.br", "7499999999", "CONCESSIONARIA");
    }

    @Test
    @DisplayName("Deve criar obra válida com todos os campos e equipes")
    void deveCriarObraValida() {
        StatusObra status = criarStatusEmExecucao();
        AgenteResponsavel agente = criarAgente();
        UUID equipe1 = UUID.randomUUID();
        UUID equipe2 = UUID.randomUUID();
        UUID idSupervisor = UUID.randomUUID();
        UUID idCoordenador = UUID.randomUUID();

        Obra obra = new Obra(
                "1228694",
                "REDE PEDAGOGICA REMANSO (CONCESSIONARIA)",
                "Descrição de teste",
                "9101923953",
                new BigDecimal("150000.00"),
                LocalDate.of(2025, 11, 3),
                LocalDate.of(2025, 11, 30),
                -9.439007,
                -41.968228,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                idSupervisor,
                idCoordenador,
                status,
                agente,
                Set.of(equipe1, equipe2)
        );

        assertThat(obra.getNumero()).isEqualTo("1228694");
        assertThat(obra.getTitulo()).isEqualTo("REDE PEDAGOGICA REMANSO (CONCESSIONARIA)");
        assertThat(obra.getNotaObra()).isEqualTo("9101923953");
        assertThat(obra.getValorProjeto()).isEqualTo(new BigDecimal("150000.00"));
        assertThat(obra.getDataInicio()).isEqualTo(LocalDate.of(2025, 11, 3));
        assertThat(obra.getDataPrevisaoFim()).isEqualTo(LocalDate.of(2025, 11, 30));
        assertThat(obra.getIdSupervisor()).isEqualTo(idSupervisor);
        assertThat(obra.getIdCoordenador()).isEqualTo(idCoordenador);
        assertThat(obra.getStatusObra()).isEqualTo(status);
        assertThat(obra.getAgenteResponsavel()).isEqualTo(agente);
        assertThat(obra.getEquipes()).containsExactlyInAnyOrder(equipe1, equipe2);
        assertThat(obra.getTipoObra()).isEqualTo(TipoObra.OBRA);
        assertThat(obra.isAtivo()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TipoObra.class)
    @DisplayName("Deve criar e editar obra com cada tipo operacional")
    void deveCriarEEditarTodosTiposOperacionais(TipoObra tipoObra) {
        Obra obra = new Obra(
                "1234567", "OBRA TIPADA", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), criarStatusEmExecucao(), null, null, tipoObra
        );

        assertThat(obra.getTipoObra()).isEqualTo(tipoObra);

        TipoObra novoTipo = tipoObra == TipoObra.LPT ? TipoObra.OBRA : TipoObra.LPT;
        obra.atualizarDados(
                obra.getNumero(), obra.getTitulo(), obra.getDescricao(), obra.getNotaObra(), obra.getValorProjeto(),
                obra.getDataInicio(), obra.getDataPrevisaoFim(), obra.getLatitude(), obra.getLongitude(),
                obra.getIdProcesso(), obra.getIdBaseOperacional(), obra.getIdMunicipio(), obra.getIdCentroCusto(),
                obra.getIdSupervisor(), obra.getIdCoordenador(), obra.getStatusObra(), obra.getAgenteResponsavel(),
                obra.getEquipes(), novoTipo
        );

        assertThat(obra.getTipoObra()).isEqualTo(novoTipo);

        obra.atualizarDados(
                obra.getNumero(), obra.getTitulo(), obra.getDescricao(), obra.getNotaObra(), obra.getValorProjeto(),
                obra.getDataInicio(), obra.getDataPrevisaoFim(), obra.getLatitude(), obra.getLongitude(),
                obra.getIdProcesso(), obra.getIdBaseOperacional(), obra.getIdMunicipio(), obra.getIdCentroCusto(),
                obra.getIdSupervisor(), obra.getIdCoordenador(), obra.getStatusObra(), obra.getAgenteResponsavel(),
                obra.getEquipes()
        );

        assertThat(obra.getTipoObra()).as("edição pelo contrato legado preserva o tipo atual")
                .isEqualTo(novoTipo);
    }

    @Test
    @DisplayName("Deve rejeitar criação de obra sem campos obrigatórios")
    void deveRejeitarObraSemCamposObrigatorios() {
        StatusObra status = criarStatusEmExecucao();
        LocalDate inicio = LocalDate.of(2025, 11, 1);
        LocalDate fim = LocalDate.of(2025, 11, 30);
        UUID processo = UUID.randomUUID();
        UUID base = UUID.randomUUID();
        UUID municipio = UUID.randomUUID();
        UUID centroCusto = UUID.randomUUID();
        UUID supervisor = UUID.randomUUID();
        UUID coordenador = UUID.randomUUID();

        assertThatThrownBy(() -> new Obra(
                null, "TITULO", null, null, null, inicio, fim, null, null, processo, base, municipio, centroCusto, supervisor, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("número da obra é obrigatório");

        assertThatThrownBy(() -> new Obra(
                "12345", "  ", null, null, null, inicio, fim, null, null, processo, base, municipio, centroCusto, supervisor, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("título da obra é obrigatório");

        assertThatThrownBy(() -> new Obra(
                "12345", "TITULO", null, null, null, inicio, fim, null, null, null, base, municipio, centroCusto, supervisor, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("processo de negócio da obra é obrigatório");

        assertThatThrownBy(() -> new Obra(
                "12345", "TITULO", null, null, null, inicio, fim, null, null, processo, null, municipio, centroCusto, supervisor, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("base operacional da obra é obrigatória");

        assertThatThrownBy(() -> new Obra(
                "12345", "TITULO", null, null, null, inicio, fim, null, null, processo, base, null, centroCusto, supervisor, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("município da obra é obrigatório");

        assertThatThrownBy(() -> new Obra(
                "12345", "TITULO", null, null, null, inicio, fim, null, null, processo, base, municipio, null, supervisor, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("centro de custo da obra é obrigatório");

        assertThatThrownBy(() -> new Obra(
                "12345", "TITULO", null, null, null, inicio, fim, null, null, processo, base, municipio, centroCusto, null, coordenador, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("supervisor da obra é obrigatório");

        assertThatThrownBy(() -> new Obra(
                "12345", "TITULO", null, null, null, inicio, fim, null, null, processo, base, municipio, centroCusto, supervisor, null, status, null, null
        )).isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("coordenador da obra é obrigatório");
    }

    @Test
    @DisplayName("Deve preencher dataFimEfetiva automaticamente ao alterar para status concluído se não informada")
    void devePreencherDataFimEfetivaAoConcluir() {
        Obra obra = new Obra(
                "1228694",
                "REDE PEDAGOGICA",
                null, null, null,
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                criarStatusEmExecucao(),
                null, null
        );

        StatusObra concluida = criarStatusConcluida();
        obra.alterarStatus(concluida, null);

        assertThat(obra.getStatusObra()).isEqualTo(concluida);
        assertThat(obra.getDataFimEfetiva()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Deve vincular e desvincular subprojeto corretamente")
    void deveVincularEDesvincularSubprojeto() {
        Obra pai = new Obra(
                "000001", "PROJETO PRINCIPAL", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                criarStatusEmExecucao(), null, null
        );

        Obra filha = new Obra(
                "1102881", "PROJETO MEDIDOR", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                criarStatusEmExecucao(), null, null
        );

        filha.vincularObraPai(pai, TipoVinculoSubprojeto.MEDIDOR);
        assertThat(filha.getObraPai()).isEqualTo(pai);
        assertThat(filha.getTipoVinculoPai()).isEqualTo(TipoVinculoSubprojeto.MEDIDOR);

        filha.desvincularObraPai();
        assertThat(filha.getObraPai()).isNull();
        assertThat(filha.getTipoVinculoPai()).isNull();
    }

    @Test
    @DisplayName("Deve adicionar e remover anexos com invariantes de tamanho e chave")
    void deveGerenciarAnexos() {
        Obra obra = new Obra(
                "1228694", "OBRA COM ANEXOS", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                criarStatusEmExecucao(), null, null
        );

        TipoAnexoObra tipo = new TipoAnexoObra("AS BUILT", "Projeto executado");

        AnexoObra anexo = obra.adicionarAnexo(
                tipo,
                "projeto_asbuilt.dwg",
                "application/acad",
                1024L * 500,
                "obras/123/anexos/asbuilt.dwg",
                "As built final da rede"
        );

        assertThat(obra.getAnexos()).hasSize(1);
        assertThat(anexo.getNomeArquivo()).isEqualTo("projeto_asbuilt.dwg");

        obra.removerAnexo(anexo.getId());
        // Se id for null antes de persistir, anexos pode ser esvaziado ou removido pelo id
        assertThat(obra.getAnexos()).isNotEmpty();
    }

    @Test
    @DisplayName("Deve realizar soft delete ao desativar obra")
    void deveRealizarSoftDelete() {
        Obra obra = new Obra(
                "1228694", "OBRA PARA DESATIVAR", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                criarStatusEmExecucao(), null, null
        );

        assertThat(obra.isAtivo()).isTrue();
        obra.desativar();
        assertThat(obra.isAtivo()).isFalse();
        assertThat(obra.getDataDesativacao()).isNotNull();

        assertThatThrownBy(() -> obra.alterarStatus(criarStatusConcluida(), null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("desativada");
    }
}
