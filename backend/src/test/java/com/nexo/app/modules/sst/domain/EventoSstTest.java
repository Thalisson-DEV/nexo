package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariantes do agregado de eventos de SST — o que cada tipo de ocorrência exige,
 * o que ele recusa e como o registro se move até a conclusão.
 */
class EventoSstTest {

    private static final UUID ATIVIDADE = UUID.randomUUID();
    private static final UUID GESTOR = UUID.randomUUID();
    private static final UUID BASE = UUID.randomUUID();
    private static final UUID CLASSIFICACAO = UUID.randomUUID();
    private static final UUID LOCAL_LESAO = UUID.randomUUID();
    private static final UUID NATUREZA_LESAO = UUID.randomUUID();
    private static final UUID AGENTE = UUID.randomUUID();
    private static final UUID TECNICO = UUID.randomUUID();
    private static final UUID ACIDENTADO = UUID.randomUUID();

    // ------------------------------------------------------------------ //
    //  Registro
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Deve registrar um acidente típico com acidentado, lesão e contexto congelado")
    void deveRegistrarAcidenteTipico() {
        EventoSst evento = acidenteTipico();

        assertEquals("EVT-2026-000001", evento.getNumeroRegistro());
        assertEquals(StatusEventoSst.REGISTRADO, evento.getStatus());
        assertEquals("Detonação", evento.getAtividadeNome());
        assertEquals("MARIA SOUZA", evento.getGestorNome());
        assertEquals("Base Central", evento.getBaseNome());
        assertEquals(LOCAL_LESAO, evento.getIdLocalLesao());
        assertTrue(evento.isComLesao());
        assertEquals(1, evento.getTecnicos().size());
        assertEquals(1, evento.getEnvolvidos().size());
        assertTrue(evento.getEnvolvidos().getFirst().isAcidentado());
    }

    @Test
    @DisplayName("Deve aceitar incidente sem lesão sem acidentado e sem dados de lesão")
    void deveAceitarIncidenteSemLesao() {
        EventoSst evento = EventoSst.registrar("EVT-2026-000002",
                dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                atividade(), operacional(),
                List.of(tecnico()),
                List.of(SnapshotEnvolvido.colaborador(PapelEnvolvido.TESTEMUNHA, ACIDENTADO,
                        "JOÃO LIMA", "Eletricista", "Base Central")));

        assertNull(evento.getIdLocalLesao());
        assertNull(evento.getDiasPerdidos());
        assertFalse(evento.isComLesao());
    }

    @Test
    @DisplayName("Deve aceitar incidente sem nenhum envolvido")
    void deveAceitarIncidenteSemEnvolvidos() {
        EventoSst evento = EventoSst.registrar("EVT-2026-000003",
                dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                atividade(), operacional(), List.of(tecnico()), List.of());

        assertTrue(evento.getEnvolvidos().isEmpty());
    }

    @Test
    @DisplayName("Deve recusar dano material que traga local da lesão ou dias perdidos, nomeando o campo")
    void deveRecusarLesaoEmDanoMaterial() {
        InvarianteVioladaException comLocal = assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000004",
                        dados(TipoEventoSst.DANO_MATERIAL).idLocalLesao(LOCAL_LESAO).build(),
                        atividade(), operacional(), List.of(tecnico()), List.of()));
        assertTrue(comLocal.getMessage().contains("local da lesão"));

        InvarianteVioladaException comDias = assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000005",
                        dados(TipoEventoSst.DANO_MATERIAL).diasPerdidos(3).build(),
                        atividade(), operacional(), List.of(tecnico()), List.of()));
        assertTrue(comDias.getMessage().contains("dias perdidos"));
    }

    @Test
    @DisplayName("Deve exigir local da lesão nos tipos que implicam lesão")
    void deveExigirLocalDaLesao() {
        assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000006",
                        dados(TipoEventoSst.ACIDENTE_TIPICO).idLocalLesao(null).build(),
                        atividade(), operacional(), List.of(tecnico()), List.of(acidentado())));
    }

    @Test
    @DisplayName("Deve exigir ao menos um acidentado nos tipos que implicam lesão")
    void deveExigirAcidentado() {
        InvarianteVioladaException erro = assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000007",
                        dados(TipoEventoSst.ACIDENTE_TRAJETO).build(),
                        atividade(), operacional(), List.of(tecnico()),
                        List.of(SnapshotEnvolvido.terceiro(PapelEnvolvido.TESTEMUNHA, "PEDRO ROCHA", null, "Transportes X"))));

        assertTrue(erro.getMessage().contains("acidentado"));
    }

    @Test
    @DisplayName("Deve recusar dias perdidos negativos")
    void deveRecusarDiasPerdidosNegativos() {
        assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000008",
                        dados(TipoEventoSst.ACIDENTE_TIPICO).diasPerdidos(-1).build(),
                        atividade(), operacional(), List.of(tecnico()), List.of(acidentado())));
    }

    @Test
    @DisplayName("Deve preencher telemetria e filmagem não declaradas como não aplicáveis")
    void deveAssumirNaoAplicavel() {
        EventoSst evento = EventoSst.registrar("EVT-2026-000009",
                dados(TipoEventoSst.INCIDENTE_SEM_LESAO).telemetria(null).filmagem(null).build(),
                atividade(), operacional(), List.of(tecnico()), List.of());

        assertEquals(RegistroEvidencia.NAO_APLICAVEL, evento.getTelemetria());
        assertEquals(RegistroEvidencia.NAO_APLICAVEL, evento.getFilmagem());
    }

    // ------------------------------------------------------------------ //
    //  Datas e comunicação
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Datas de ocorrência e comunicação")
    class Datas {

        @Test
        @DisplayName("Deve recusar comunicação anterior à ocorrência")
        void deveRecusarComunicacaoAnterior() {
            OffsetDateTime ocorrencia = OffsetDateTime.now().minusHours(2);

            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000010",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO)
                                    .dataOcorrencia(ocorrencia)
                                    .dataComunicacao(ocorrencia.minusHours(1))
                                    .build(),
                            atividade(), operacional(), List.of(tecnico()), List.of()));
        }

        @Test
        @DisplayName("Deve recusar datas no futuro")
        void deveRecusarDatasNoFuturo() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000011",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO)
                                    .dataOcorrencia(OffsetDateTime.now().plusMinutes(30))
                                    .build(),
                            atividade(), operacional(), List.of(tecnico()), List.of()));

            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000012",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO)
                                    .dataComunicacao(OffsetDateTime.now().plusMinutes(30))
                                    .build(),
                            atividade(), operacional(), List.of(tecnico()), List.of()));
        }

        @Test
        @DisplayName("Deve derivar as horas até a comunicação e o atraso pelo prazo do tipo")
        void deveDerivarAtraso() {
            OffsetDateTime ocorrencia = OffsetDateTime.now().minusHours(30);

            EventoSst acidente = EventoSst.registrar("EVT-2026-000013",
                    dados(TipoEventoSst.ACIDENTE_TIPICO)
                            .dataOcorrencia(ocorrencia)
                            .dataComunicacao(ocorrencia.plusHours(26))
                            .build(),
                    atividade(), operacional(), List.of(tecnico()), List.of(acidentado()));

            assertEquals(26, acidente.getHorasAteComunicacao());
            assertTrue(acidente.isComunicacaoAtrasada(), "26h ultrapassa as 24h do acidente típico");

            EventoSst semLesao = EventoSst.registrar("EVT-2026-000014",
                    dados(TipoEventoSst.INCIDENTE_SEM_LESAO)
                            .dataOcorrencia(ocorrencia)
                            .dataComunicacao(ocorrencia.plusHours(26))
                            .build(),
                    atividade(), operacional(), List.of(tecnico()), List.of());

            assertFalse(semLesao.isComunicacaoAtrasada(), "26h cabe nas 72h dos tipos sem lesão");
        }
    }

    // ------------------------------------------------------------------ //
    //  CAT
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Comunicação de Acidente de Trabalho")
    class Cat {

        @Test
        @DisplayName("Deve guardar tipo, número e emissão quando a CAT é declarada")
        void deveGuardarCat() {
            EventoSst evento = EventoSst.registrar("EVT-2026-000015",
                    dados(TipoEventoSst.ACIDENTE_TIPICO)
                            .possuiCat(true)
                            .tipoCat(TipoCat.INICIAL)
                            .numeroCat("2026123456")
                            .dataEmissaoCat(LocalDate.now())
                            .build(),
                    atividade(), operacional(), List.of(tecnico()), List.of(acidentado()));

            assertTrue(evento.isPossuiCat());
            assertEquals(TipoCat.INICIAL, evento.getTipoCat());
            assertEquals("2026123456", evento.getNumeroCat());
            assertNotNull(evento.getDataEmissaoCat());
        }

        @Test
        @DisplayName("Deve recusar CAT declarada sem tipo")
        void deveRecusarCatSemTipo() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000016",
                            dados(TipoEventoSst.ACIDENTE_TIPICO).possuiCat(true).build(),
                            atividade(), operacional(), List.of(tecnico()), List.of(acidentado())));
        }

        @Test
        @DisplayName("Deve recusar dados de CAT sem CAT declarada")
        void deveRecusarDadosSemDeclaracao() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000017",
                            dados(TipoEventoSst.ACIDENTE_TIPICO).numeroCat("2026123456").build(),
                            atividade(), operacional(), List.of(tecnico()), List.of(acidentado())));

            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000018",
                            dados(TipoEventoSst.ACIDENTE_TIPICO).dataEmissaoCat(LocalDate.now()).build(),
                            atividade(), operacional(), List.of(tecnico()), List.of(acidentado())));
        }
    }

    // ------------------------------------------------------------------ //
    //  Técnicos e envolvidos
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Responsáveis e envolvidos")
    class Pessoas {

        @Test
        @DisplayName("Deve exigir ao menos um técnico de segurança")
        void deveExigirTecnico() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000019",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                            atividade(), operacional(), List.of(), List.of()));
        }

        @Test
        @DisplayName("Deve recusar o mesmo técnico duas vezes")
        void deveRecusarTecnicoDuplicado() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000020",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                            atividade(), operacional(),
                            List.of(tecnico(), tecnico()), List.of()));
        }

        @Test
        @DisplayName("Deve aceitar mais de um técnico responsável")
        void deveAceitarVariosTecnicos() {
            EventoSst evento = EventoSst.registrar("EVT-2026-000021",
                    dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                    atividade(), operacional(),
                    List.of(tecnico(), new SnapshotTecnico(UUID.randomUUID(), "ANA DIAS", "Técnica de Segurança")),
                    List.of());

            assertEquals(2, evento.getTecnicos().size());
        }

        @Test
        @DisplayName("Deve recusar o mesmo colaborador duas vezes entre os envolvidos")
        void deveRecusarColaboradorDuplicado() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000022",
                            dados(TipoEventoSst.ACIDENTE_TIPICO).build(),
                            atividade(), operacional(), List.of(tecnico()),
                            List.of(acidentado(),
                                    SnapshotEnvolvido.colaborador(PapelEnvolvido.TESTEMUNHA, ACIDENTADO,
                                            "CARLOS NUNES", "Eletricista", "Base Central"))));
        }

        @Test
        @DisplayName("Deve exigir empresa do terceiro e recusar terceiro com colaborador vinculado")
        void deveValidarTerceiro() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000023",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                            atividade(), operacional(), List.of(tecnico()),
                            List.of(new SnapshotEnvolvido(TipoEnvolvido.TERCEIRO, PapelEnvolvido.TESTEMUNHA,
                                    null, "PEDRO ROCHA", null, null, null))));

            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000024",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                            atividade(), operacional(), List.of(tecnico()),
                            List.of(new SnapshotEnvolvido(TipoEnvolvido.TERCEIRO, PapelEnvolvido.TESTEMUNHA,
                                    UUID.randomUUID(), "PEDRO ROCHA", null, null, "Transportes X"))));
        }

        @Test
        @DisplayName("Deve gerar blind index apenas para o nome do terceiro")
        void deveGerarBlindIndexApenasParaTerceiro() {
            EventoSst evento = EventoSst.registrar("EVT-2026-000025",
                    dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                    atividade(), operacional(), List.of(tecnico()),
                    List.of(SnapshotEnvolvido.colaborador(PapelEnvolvido.ENVOLVIDO, ACIDENTADO,
                                    "JOÃO LIMA", "Eletricista", "Base Central"),
                            SnapshotEnvolvido.terceiro(PapelEnvolvido.TESTEMUNHA, "PEDRO ROCHA", null, "Transportes X")));

            EnvolvidoEvento colaborador = evento.getEnvolvidos().getFirst();
            EnvolvidoEvento terceiro = evento.getEnvolvidos().get(1);

            assertNull(colaborador.getNomeHash(), "colaborador é filtrado por id, resolvido no RH");
            assertNotNull(terceiro.getNomeHash(), "terceiro só é buscável pelo hash do nome");
            assertEquals("Transportes X", terceiro.getEmpresa());
        }

        @Test
        @DisplayName("Deve exigir colaborador cadastrado no envolvido do tipo COLABORADOR")
        void deveExigirColaboradorCadastrado() {
            assertThrows(InvarianteVioladaException.class,
                    () -> EventoSst.registrar("EVT-2026-000026",
                            dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                            atividade(), operacional(), List.of(tecnico()),
                            List.of(new SnapshotEnvolvido(TipoEnvolvido.COLABORADOR, PapelEnvolvido.TESTEMUNHA,
                                    null, "JOÃO LIMA", "Eletricista", "Base Central", null))));
        }
    }

    // ------------------------------------------------------------------ //
    //  Contexto congelado
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Deve exigir gestor no evento mesmo quando a atividade não tem")
    void deveExigirGestorNoEvento() {
        SnapshotAtividade semGestor = new SnapshotAtividade(ATIVIDADE, "Trajeto", null, null, null, false);

        InvarianteVioladaException erro = assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000027",
                        dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                        semGestor, operacional(), List.of(tecnico()), List.of()));

        assertTrue(erro.getMessage().contains("gestor"));
    }

    @Test
    @DisplayName("Deve marcar o gestor informado como substituto sem tocar no cadastro")
    void deveMarcarGestorSubstituto() {
        SnapshotAtividade substituto = new SnapshotAtividade(ATIVIDADE, "Detonação",
                UUID.randomUUID(), "ANA DIAS", "Coordenadora", true);

        EventoSst evento = EventoSst.registrar("EVT-2026-000028",
                dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                substituto, operacional(), List.of(tecnico()), List.of());

        assertTrue(evento.isGestorSubstituto());
        assertEquals("ANA DIAS", evento.getGestorNome());
    }

    @Test
    @DisplayName("Deve exigir base operacional no snapshot")
    void deveExigirBase() {
        SnapshotOperacional semBase = new SnapshotOperacional(null, null, null, null, null, null);

        assertThrows(InvarianteVioladaException.class,
                () -> EventoSst.registrar("EVT-2026-000029",
                        dados(TipoEventoSst.INCIDENTE_SEM_LESAO).build(),
                        atividade(), semBase, List.of(tecnico()), List.of()));
    }

    // ------------------------------------------------------------------ //
    //  Ciclo de vida
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Ciclo de vida")
    class CicloDeVida {

        @Test
        @DisplayName("Deve caminhar de registrado a concluído com tratativa")
        void deveConcluir() {
            EventoSst evento = acidenteTipico();

            evento.iniciarTratativa("Isolamento da área e treinamento da equipe");
            assertEquals(StatusEventoSst.EM_TRATATIVA, evento.getStatus());

            evento.concluir(null);
            assertEquals(StatusEventoSst.CONCLUIDO, evento.getStatus());
            assertNotNull(evento.getDataConclusao());
        }

        @Test
        @DisplayName("Não deve concluir sem tratativa")
        void naoDeveConcluirSemTratativa() {
            EventoSst evento = acidenteTipico();

            assertThrows(InvarianteVioladaException.class, () -> evento.concluir(null));
            assertEquals(StatusEventoSst.REGISTRADO, evento.getStatus());
        }

        @Test
        @DisplayName("Não deve editar evento concluído, e deve voltar a aceitar edição após reabertura")
        void naoDeveEditarConcluido() {
            EventoSst evento = acidenteTipico();
            evento.concluir("Tratado com a equipe");

            assertThrows(InvarianteVioladaException.class,
                    () -> evento.atualizar(dados(TipoEventoSst.ACIDENTE_TIPICO).build(),
                            atividade(), operacional(), List.of(tecnico()), List.of(acidentado())));

            evento.reabrir("Erro na classificação informada");
            assertEquals(StatusEventoSst.EM_TRATATIVA, evento.getStatus());
            assertNull(evento.getDataConclusao());

            evento.atualizar(dados(TipoEventoSst.ACIDENTE_TIPICO).diasPerdidos(5).build(),
                    atividade(), operacional(), List.of(tecnico()), List.of(acidentado()));
            assertEquals(5, evento.getDiasPerdidos());
        }

        @Test
        @DisplayName("Deve exigir motivo para cancelar e para reabrir")
        void deveExigirMotivo() {
            EventoSst evento = acidenteTipico();

            assertThrows(InvarianteVioladaException.class, () -> evento.cancelar("  "));

            evento.cancelar("Registro duplicado do EVT-2026-000001");
            assertEquals(StatusEventoSst.CANCELADO, evento.getStatus());
            assertNotNull(evento.getDataCancelamento());
            assertFalse(evento.getStatus().contaNosIndicadores());

            assertThrows(InvarianteVioladaException.class, () -> evento.reabrir(null));

            evento.reabrir("Cancelamento indevido");
            assertEquals(StatusEventoSst.EM_TRATATIVA, evento.getStatus());
            assertNull(evento.getDataCancelamento());
        }

        @Test
        @DisplayName("Não deve cancelar um evento já concluído nem cancelar duas vezes")
        void naoDeveCancelarConcluido() {
            EventoSst concluido = acidenteTipico();
            concluido.concluir("Tratado");
            assertThrows(InvarianteVioladaException.class, () -> concluido.cancelar("Duplicado"));

            EventoSst cancelado = acidenteTipico();
            cancelado.cancelar("Duplicado");
            assertThrows(InvarianteVioladaException.class, () -> cancelado.cancelar("Duplicado de novo"));
        }

        @Test
        @DisplayName("Não deve reabrir um evento que já está aberto")
        void naoDeveReabrirAberto() {
            EventoSst evento = acidenteTipico();

            assertThrows(InvarianteVioladaException.class, () -> evento.reabrir("Motivo qualquer"));
        }

        @Test
        @DisplayName("Não deve anexar nem remover evidência de evento concluído")
        void naoDeveAnexarEmConcluido() {
            EventoSst evento = acidenteTipico();
            AnexoEventoSst anexo = evento.adicionarAnexo(ContextoAnexoEvento.EVENTO,
                    "local.jpg", "image/jpeg", 1024, "sst/eventos/1/evento/x.jpg");
            assertEquals(1, evento.getAnexos().size());

            evento.concluir("Tratado");

            assertThrows(InvarianteVioladaException.class,
                    () -> evento.adicionarAnexo(ContextoAnexoEvento.CAT, "cat.pdf", "application/pdf", 2048,
                            "sst/eventos/1/cat/y.pdf"));
            assertThrows(InvarianteVioladaException.class, () -> evento.removerAnexo(anexo));
        }
    }

    // ------------------------------------------------------------------ //
    //  Fixtures
    // ------------------------------------------------------------------ //

    private static EventoSst acidenteTipico() {
        return EventoSst.registrar("EVT-2026-000001",
                dados(TipoEventoSst.ACIDENTE_TIPICO).build(),
                atividade(), operacional(), List.of(tecnico()), List.of(acidentado()));
    }

    private static SnapshotAtividade atividade() {
        return new SnapshotAtividade(ATIVIDADE, "Detonação", GESTOR, "MARIA SOUZA", "Supervisora", false);
    }

    private static SnapshotOperacional operacional() {
        return new SnapshotOperacional(BASE, "Base Central", UUID.randomUUID(), "EQ-101",
                UUID.randomUUID(), "ABC1D23");
    }

    private static SnapshotTecnico tecnico() {
        return new SnapshotTecnico(TECNICO, "CARLA MENDES", "Técnica de Segurança");
    }

    private static SnapshotEnvolvido acidentado() {
        return SnapshotEnvolvido.colaborador(PapelEnvolvido.ACIDENTADO, ACIDENTADO,
                "CARLOS NUNES", "Eletricista", "Base Central");
    }

    private static DadosBuilder dados(TipoEventoSst tipo) {
        return new DadosBuilder(tipo);
    }

    /**
     * Construtor de conveniência dos dados do evento: os testes variam um campo por vez,
     * e repetir dezenove argumentos em cada caso esconderia justamente o que muda.
     */
    private static final class DadosBuilder {

        private final TipoEventoSst tipo;
        private OffsetDateTime dataOcorrencia = OffsetDateTime.now().minusHours(5);
        private OffsetDateTime dataComunicacao = OffsetDateTime.now().minusHours(4);
        private UUID idLocalLesao;
        private Integer diasPerdidos;
        private RegistroEvidencia telemetria = RegistroEvidencia.SIM;
        private RegistroEvidencia filmagem = RegistroEvidencia.NAO_APLICAVEL;
        private boolean possuiCat;
        private TipoCat tipoCat;
        private String numeroCat;
        private LocalDate dataEmissaoCat;

        private DadosBuilder(TipoEventoSst tipo) {
            this.tipo = tipo;
            this.idLocalLesao = tipo.implicaLesao() ? LOCAL_LESAO : null;
        }

        DadosBuilder dataOcorrencia(OffsetDateTime valor) {
            this.dataOcorrencia = valor;
            return this;
        }

        DadosBuilder dataComunicacao(OffsetDateTime valor) {
            this.dataComunicacao = valor;
            return this;
        }

        DadosBuilder idLocalLesao(UUID valor) {
            this.idLocalLesao = valor;
            return this;
        }

        DadosBuilder diasPerdidos(Integer valor) {
            this.diasPerdidos = valor;
            return this;
        }

        DadosBuilder telemetria(RegistroEvidencia valor) {
            this.telemetria = valor;
            return this;
        }

        DadosBuilder filmagem(RegistroEvidencia valor) {
            this.filmagem = valor;
            return this;
        }

        DadosBuilder possuiCat(boolean valor) {
            this.possuiCat = valor;
            return this;
        }

        DadosBuilder tipoCat(TipoCat valor) {
            this.tipoCat = valor;
            return this;
        }

        DadosBuilder numeroCat(String valor) {
            this.numeroCat = valor;
            return this;
        }

        DadosBuilder dataEmissaoCat(LocalDate valor) {
            this.dataEmissaoCat = valor;
            return this;
        }

        DadosEventoSst build() {
            return new DadosEventoSst(tipo, dataOcorrencia, dataComunicacao, CLASSIFICACAO, UUID.randomUUID(),
                    telemetria, filmagem, "Trecho urbano da rede", null, null,
                    "Descrição do que aconteceu no evento.",
                    idLocalLesao, tipo.implicaLesao() ? NATUREZA_LESAO : null, AGENTE, diasPerdidos,
                    possuiCat, tipoCat, numeroCat, dataEmissaoCat);
        }
    }
}
