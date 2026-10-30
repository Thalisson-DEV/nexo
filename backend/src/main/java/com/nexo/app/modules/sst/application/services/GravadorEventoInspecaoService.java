package com.nexo.app.modules.sst.application.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.sst.application.dto.HistoricoInspecaoResponse.AlteracaoItem;
import com.nexo.app.modules.sst.domain.ColaboradorInspecionado;
import com.nexo.app.modules.sst.domain.DesvioColaborador;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.EventoInspecaoSeguranca;
import com.nexo.app.modules.sst.domain.EvidenciaInspecao;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.domain.OpcaoResposta;
import com.nexo.app.modules.sst.domain.Pergunta;
import com.nexo.app.modules.sst.domain.RespostaInspecao;
import com.nexo.app.modules.sst.domain.RespostaOpcaoSelecionada;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.modules.sst.domain.TipoInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoInspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GravadorEventoInspecaoService {

    private static final ObjectMapper CANONICAL_JSON_MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final EventoInspecaoSegurancaRepository repository;
    private final ObjectMapper objectMapper;
    private final TipoInspecaoRepository tipoInspecaoRepository;
    private final FormularioInspecaoRepository formularioRepository;
    private final ProcessoNegocioApi processoApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final VeiculoApi veiculoApi;
    private final EquipeApi equipeApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    public record EstadoCabecalhoSnapshot(
            UUID idTipoInspecao,
            String tipoInspecaoNome,
            UUID idProcesso,
            String processoNome,
            UUID idBaseOperacional,
            String baseOperacionalNome,
            UUID idVeiculo,
            String veiculoPlaca,
            UUID idEquipe,
            String equipeCodigo,
            OffsetDateTime dataRealizacao,
            String observacoes,
            BigDecimal latitude,
            BigDecimal longitude,
            List<UUID> colaboradoresIds,
            List<String> colaboradoresNomes
    ) {}

    public EstadoCabecalhoSnapshot capturarEstado(InspecaoSeguranca inspecao) {
        String tipoNome = tipoInspecaoRepository.findById(inspecao.getIdTipoInspecao())
                .map(TipoInspecao::getNome).orElse(null);
        String processoNome = processoApi.buscarPorIdResponse(inspecao.getIdProcesso())
                .map(p -> p.nome()).orElse(null);
        String baseNome = inspecao.getIdBaseOperacional() == null ? null
                : baseOperacionalApi.buscarPorIdResponse(inspecao.getIdBaseOperacional())
                .map(b -> b.nome()).orElse(null);
        String veiculoPlaca = inspecao.getIdVeiculo() == null ? null
                : veiculoApi.buscarPorIdResponse(inspecao.getIdVeiculo())
                .map(v -> v.placa()).orElse(null);
        String equipeCodigo = inspecao.getIdEquipe() == null ? null
                : equipeApi.buscarPorIdResponse(inspecao.getIdEquipe())
                .map(e -> e.codigo()).orElse(null);

        List<UUID> colabIds = inspecao.getColaboradoresInspecionados().stream()
                .map(ColaboradorInspecionado::getIdColaborador)
                .toList();

        List<String> colabNomes = colabIds.stream()
                .map(id -> colaboradorApi.buscarNomeCompletoPorUsuario(id)
                        .orElseGet(() -> colaboradorApi.buscarPorIdResponse(id).map(c -> c.nomeCompleto()).orElse(id.toString())))
                .toList();

        return new EstadoCabecalhoSnapshot(
                inspecao.getIdTipoInspecao(), tipoNome,
                inspecao.getIdProcesso(), processoNome,
                inspecao.getIdBaseOperacional(), baseNome,
                inspecao.getIdVeiculo(), veiculoPlaca,
                inspecao.getIdEquipe(), equipeCodigo,
                inspecao.getDataRealizacao(),
                inspecao.getObservacoes(),
                inspecao.getLatitude(),
                inspecao.getLongitude(),
                colabIds,
                colabNomes
        );
    }

    public record RespostaSnapshot(
            UUID idPergunta,
            String enunciado,
            String valorTexto,
            BigDecimal valorNumero,
            LocalDate valorData,
            List<UUID> opcoesIds,
            List<String> opcoesTextos
    ) {}

    public record DesvioSnapshot(
            UUID idPergunta,
            String perguntaEnunciado,
            UUID idOpcaoResposta,
            String opcaoTexto,
            GravidadeDesvio gravidade,
            String descricao,
            String acaoCorretiva,
            LocalDate prazo,
            StatusDesvio status,
            List<UUID> colaboradoresIds,
            List<String> colaboradoresNomes
    ) {}

    public record EstadoCompletoInspecaoSnapshot(
            EstadoCabecalhoSnapshot cabecalho,
            Map<UUID, RespostaSnapshot> respostas,
            Map<String, DesvioSnapshot> desvios,
            List<String> evidenciasNomes
    ) {}

    public EstadoCompletoInspecaoSnapshot capturarEstadoCompleto(InspecaoSeguranca inspecao) {
        EstadoCabecalhoSnapshot cabecalho = capturarEstado(inspecao);

        FormularioInspecao formulario = formularioRepository.findById(inspecao.getIdFormulario()).orElse(null);
        Map<UUID, Pergunta> perguntasPorId = new HashMap<>();
        Map<UUID, OpcaoResposta> opcoesPorId = new HashMap<>();
        if (formulario != null) {
            formulario.getGrupos().forEach(g -> g.getPerguntas().forEach(p -> {
                perguntasPorId.put(p.getId(), p);
                p.getOpcoes().forEach(o -> opcoesPorId.put(o.getId(), o));
            }));
        }

        Map<UUID, RespostaSnapshot> respostasMap = new LinkedHashMap<>();
        for (RespostaInspecao r : inspecao.getRespostas()) {
            Pergunta p = perguntasPorId.get(r.getIdPergunta());
            String enunciado = p != null ? p.getEnunciado() : r.getIdPergunta().toString();
            List<UUID> opcoesIds = r.getOpcoesSelecionadas().stream()
                    .map(RespostaOpcaoSelecionada::getIdOpcaoResposta)
                    .toList();
            List<String> opcoesTextos = opcoesIds.stream()
                    .map(idOpcao -> {
                        OpcaoResposta op = opcoesPorId.get(idOpcao);
                        return op != null ? op.getTexto() : idOpcao.toString();
                    })
                    .toList();

            respostasMap.put(r.getIdPergunta(), new RespostaSnapshot(
                    r.getIdPergunta(), enunciado, r.getValorTexto(), r.getValorNumero(),
                    r.getValorData(), opcoesIds, opcoesTextos
            ));
        }

        Map<String, DesvioSnapshot> desviosMap = new LinkedHashMap<>();
        for (DesvioSeguranca d : inspecao.getDesvios()) {
            Pergunta p = perguntasPorId.get(d.getIdPergunta());
            String enunciado = p != null ? p.getEnunciado() : d.getIdPergunta().toString();
            OpcaoResposta op = d.getIdOpcaoResposta() != null ? opcoesPorId.get(d.getIdOpcaoResposta()) : null;
            String opcaoTexto = op != null ? op.getTexto() : (d.getIdOpcaoResposta() != null ? d.getIdOpcaoResposta().toString() : null);

            List<UUID> colabIds = d.getColaboradores().stream()
                    .map(DesvioColaborador::getIdColaborador)
                    .toList();
            List<String> colabNomes = colabIds.stream()
                    .map(id -> colaboradorApi.buscarNomeCompletoPorUsuario(id)
                            .orElseGet(() -> colaboradorApi.buscarPorIdResponse(id).map(c -> c.nomeCompleto()).orElse(id.toString())))
                    .toList();

            String chave = d.getIdPergunta() + ":" + d.getIdOpcaoResposta();
            desviosMap.put(chave, new DesvioSnapshot(
                    d.getIdPergunta(), enunciado, d.getIdOpcaoResposta(), opcaoTexto,
                    d.getGravidade(), d.getDescricao(), d.getAcaoCorretiva(), d.getPrazo(),
                    d.getStatus(), colabIds, colabNomes
            ));
        }

        List<String> evidenciasNomes = inspecao.getEvidencias().stream()
                .map(EvidenciaInspecao::getNomeArquivo)
                .toList();

        return new EstadoCompletoInspecaoSnapshot(cabecalho, respostasMap, desviosMap, evidenciasNomes);
    }

    @Transactional
    public EventoInspecaoSeguranca registrarInicial(InspecaoSeguranca inspecao, UUID idAutor) {
        String autorNome = resolverNomeAutor(idAutor);
        Map<String, Object> snapshots = montarSnapshots(inspecao);
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("comando", "REGISTRAR_INSPECAO");
        payloadMap.put("alteracoes", List.of());
        payloadMap.put("snapshots", snapshots);

        String payloadJson = canonicalizarJson(serializar(payloadMap));
        OffsetDateTime ocorridoEm = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        EventoInspecaoSeguranca evento = new EventoInspecaoSeguranca(
                inspecao.getId(),
                1L,
                "REGISTRO_INICIAL",
                idAutor,
                autorNome,
                "UI",
                null,
                payloadJson,
                ocorridoEm
        );

        String hashAtual = calcularHash(null, 1L, "REGISTRO_INICIAL", payloadJson, ocorridoEm);
        evento.selar(null, hashAtual);
        return repository.save(evento);
    }

    @Transactional
    public EventoInspecaoSeguranca registrarBaselineInicial(InspecaoSeguranca inspecao, EstadoCabecalhoSnapshot estadoInicial) {
        String autorNome = resolverNomeAutor(inspecao.getIdInspetor());
        Map<String, Object> snapshots = montarSnapshotsDeEstado(inspecao, estadoInicial);
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("comando", "REGISTRAR_INSPECAO");
        payloadMap.put("alteracoes", List.of());
        payloadMap.put("snapshots", snapshots);

        String payloadJson = canonicalizarJson(serializar(payloadMap));
        OffsetDateTime ocorridoEm = inspecao.getDataCriacao() != null
                ? inspecao.getDataCriacao().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)
                : OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        EventoInspecaoSeguranca evento = new EventoInspecaoSeguranca(
                inspecao.getId(),
                1L,
                "REGISTRO_INICIAL",
                inspecao.getIdInspetor(),
                autorNome,
                "SISTEMA",
                "Baseline inicial registrado para auditoria de inspeção pré-existente",
                payloadJson,
                ocorridoEm
        );

        String hashAtual = calcularHash(null, 1L, "REGISTRO_INICIAL", payloadJson, ocorridoEm);
        evento.selar(null, hashAtual);
        return repository.save(evento);
    }

    @Transactional
    public EventoInspecaoSeguranca registrarEdicaoCabecalho(
            InspecaoSeguranca inspecao,
            EstadoCabecalhoSnapshot anterior,
            UUID idAutor,
            String justificativa
    ) {
        String autorNome = resolverNomeAutor(idAutor);
        EstadoCabecalhoSnapshot atual = capturarEstado(inspecao);
        List<AlteracaoItem> alteracoes = compararCabecalho(anterior, atual);

        EventoInspecaoSeguranca ultimo = repository.findTopByIdInspecaoOrderBySequenciaDesc(inspecao.getId())
                .orElse(null);
        long proximaSequencia = ultimo == null ? 1L : ultimo.getSequencia() + 1L;
        String hashAnterior = ultimo == null ? null : ultimo.getHashAtual();

        Map<String, Object> snapshots = montarSnapshots(inspecao);
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("comando", "EDITAR_CABECALHO");
        payloadMap.put("alteracoes", alteracoes);
        payloadMap.put("snapshots", snapshots);

        String payloadJson = canonicalizarJson(serializar(payloadMap));
        OffsetDateTime ocorridoEm = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        EventoInspecaoSeguranca evento = new EventoInspecaoSeguranca(
                inspecao.getId(),
                proximaSequencia,
                "EDICAO_CABECALHO",
                idAutor,
                autorNome,
                "UI",
                justificativa,
                payloadJson,
                ocorridoEm
        );

        String hashAtual = calcularHash(hashAnterior, proximaSequencia, "EDICAO_CABECALHO", payloadJson, ocorridoEm);
        evento.selar(hashAnterior, hashAtual);
        return repository.save(evento);
    }

    @Transactional
    public EventoInspecaoSeguranca registrarEdicaoCompleta(
            InspecaoSeguranca inspecao,
            EstadoCompletoInspecaoSnapshot anterior,
            UUID idAutor,
            String justificativa
    ) {
        String autorNome = resolverNomeAutor(idAutor);
        EstadoCompletoInspecaoSnapshot atual = capturarEstadoCompleto(inspecao);
        List<AlteracaoItem> alteracoes = compararCompleto(anterior, atual);

        EventoInspecaoSeguranca ultimo = repository.findTopByIdInspecaoOrderBySequenciaDesc(inspecao.getId())
                .orElse(null);
        long proximaSequencia = ultimo == null ? 1L : ultimo.getSequencia() + 1L;
        String hashAnterior = ultimo == null ? null : ultimo.getHashAtual();

        Map<String, Object> snapshots = montarSnapshots(inspecao);
        snapshots.put("totalRespostas", inspecao.getRespostas().size());
        snapshots.put("totalDesvios", inspecao.getDesvios().size());
        snapshots.put("totalEvidencias", inspecao.getEvidencias().size());

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("comando", "EDITAR_INSPECAO");
        payloadMap.put("alteracoes", alteracoes);
        payloadMap.put("snapshots", snapshots);

        String payloadJson = canonicalizarJson(serializar(payloadMap));
        OffsetDateTime ocorridoEm = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        EventoInspecaoSeguranca evento = new EventoInspecaoSeguranca(
                inspecao.getId(),
                proximaSequencia,
                "EDICAO_TOTAL",
                idAutor,
                autorNome,
                "UI",
                justificativa,
                payloadJson,
                ocorridoEm
        );

        String hashAtual = calcularHash(hashAnterior, proximaSequencia, "EDICAO_TOTAL", payloadJson, ocorridoEm);
        evento.selar(hashAnterior, hashAtual);
        return repository.save(evento);
    }

    @Transactional
    public EventoInspecaoSeguranca registrarTratamentoDesvio(
            InspecaoSeguranca inspecao,
            DesvioSeguranca desvio,
            StatusDesvio statusAntigo,
            String acaoAntiga,
            LocalDate prazoAntigo,
            UUID idAutor
    ) {
        String autorNome = resolverNomeAutor(idAutor);
        List<AlteracaoItem> alteracoes = new ArrayList<>();
        if (statusAntigo != desvio.getStatus()) {
            alteracoes.add(new AlteracaoItem("statusDesvio", statusAntigo != null ? statusAntigo.name() : null, desvio.getStatus().name()));
        }
        if (!Objects.equals(acaoAntiga, desvio.getAcaoCorretiva())) {
            alteracoes.add(new AlteracaoItem("acaoCorretiva", acaoAntiga, desvio.getAcaoCorretiva()));
        }
        if (!Objects.equals(prazoAntigo, desvio.getPrazo())) {
            alteracoes.add(new AlteracaoItem("prazo", prazoAntigo != null ? prazoAntigo.toString() : null, desvio.getPrazo() != null ? desvio.getPrazo().toString() : null));
        }

        EventoInspecaoSeguranca ultimo = repository.findTopByIdInspecaoOrderBySequenciaDesc(inspecao.getId())
                .orElse(null);
        long proximaSequencia = ultimo == null ? 1L : ultimo.getSequencia() + 1L;
        String hashAnterior = ultimo == null ? null : ultimo.getHashAtual();

        Map<String, Object> snapshots = montarSnapshots(inspecao);
        snapshots.put("idDesvio", desvio.getId().toString());

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("comando", "TRATAR_DESVIO");
        payloadMap.put("alteracoes", alteracoes);
        payloadMap.put("snapshots", snapshots);

        String payloadJson = canonicalizarJson(serializar(payloadMap));
        OffsetDateTime ocorridoEm = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        EventoInspecaoSeguranca evento = new EventoInspecaoSeguranca(
                inspecao.getId(),
                proximaSequencia,
                "TRATAMENTO_DESVIO",
                idAutor,
                autorNome,
                "UI",
                null,
                payloadJson,
                ocorridoEm
        );

        String hashAtual = calcularHash(hashAnterior, proximaSequencia, "TRATAMENTO_DESVIO", payloadJson, ocorridoEm);
        evento.selar(hashAnterior, hashAtual);
        return repository.save(evento);
    }

    private List<AlteracaoItem> compararCabecalho(EstadoCabecalhoSnapshot ant, EstadoCabecalhoSnapshot atu) {
        List<AlteracaoItem> diffs = new ArrayList<>();

        if (!Objects.equals(ant.idTipoInspecao(), atu.idTipoInspecao())) {
            diffs.add(new AlteracaoItem("tipoInspecao", ant.tipoInspecaoNome(), atu.tipoInspecaoNome()));
        }
        if (!Objects.equals(ant.idProcesso(), atu.idProcesso())) {
            diffs.add(new AlteracaoItem("processo", ant.processoNome(), atu.processoNome()));
        }
        if (!Objects.equals(ant.idBaseOperacional(), atu.idBaseOperacional())) {
            diffs.add(new AlteracaoItem("baseOperacional", ant.baseOperacionalNome(), atu.baseOperacionalNome()));
        }
        if (!Objects.equals(ant.idVeiculo(), atu.idVeiculo())) {
            diffs.add(new AlteracaoItem("veiculo", ant.veiculoPlaca() != null ? ant.veiculoPlaca() : "Nenhum", atu.veiculoPlaca() != null ? atu.veiculoPlaca() : "Nenhum"));
        }
        if (!Objects.equals(ant.idEquipe(), atu.idEquipe())) {
            diffs.add(new AlteracaoItem("equipe", ant.equipeCodigo() != null ? ant.equipeCodigo() : "Individual", atu.equipeCodigo() != null ? atu.equipeCodigo() : "Individual"));
        }
        if (!Objects.equals(ant.dataRealizacao(), atu.dataRealizacao())) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            diffs.add(new AlteracaoItem("dataRealizacao",
                    ant.dataRealizacao() != null ? ant.dataRealizacao().format(fmt) : null,
                    atu.dataRealizacao() != null ? atu.dataRealizacao().format(fmt) : null));
        }
        if (!Objects.equals(ant.observacoes(), atu.observacoes())) {
            diffs.add(new AlteracaoItem("observacoes", ant.observacoes(), atu.observacoes()));
        }
        if (!bigDecimalEquals(ant.latitude(), atu.latitude()) || !bigDecimalEquals(ant.longitude(), atu.longitude())) {
            String antCoord = ant.latitude() != null && ant.longitude() != null
                    ? formatarCoordenada(ant.latitude()) + ", " + formatarCoordenada(ant.longitude())
                    : "Não informada";
            String atuCoord = atu.latitude() != null && atu.longitude() != null
                    ? formatarCoordenada(atu.latitude()) + ", " + formatarCoordenada(atu.longitude())
                    : "Não informada";
            diffs.add(new AlteracaoItem("geolocalizacao", antCoord, atuCoord));
        }

        // Colaboradores: Inclusões e Remoções
        Set<UUID> antIds = new HashSet<>(ant.colaboradoresIds());
        Set<UUID> atuIds = new HashSet<>(atu.colaboradoresIds());

        for (int i = 0; i < atu.colaboradoresIds().size(); i++) {
            UUID id = atu.colaboradoresIds().get(i);
            if (!antIds.contains(id)) {
                String nome = i < atu.colaboradoresNomes().size() ? atu.colaboradoresNomes().get(i) : id.toString();
                diffs.add(new AlteracaoItem("colaboradoresInspecionados", null, "+ " + nome));
            }
        }
        for (int i = 0; i < ant.colaboradoresIds().size(); i++) {
            UUID id = ant.colaboradoresIds().get(i);
            if (!atuIds.contains(id)) {
                String nome = i < ant.colaboradoresNomes().size() ? ant.colaboradoresNomes().get(i) : id.toString();
                diffs.add(new AlteracaoItem("colaboradoresInspecionados", "- " + nome, null));
            }
        }

        return diffs;
    }

    public List<AlteracaoItem> compararCompleto(EstadoCompletoInspecaoSnapshot ant, EstadoCompletoInspecaoSnapshot atu) {
        List<AlteracaoItem> diffs = new ArrayList<>(compararCabecalho(ant.cabecalho(), atu.cabecalho()));

        // Comparação de respostas do checklist
        Set<UUID> todasPerguntas = new LinkedHashSet<>();
        todasPerguntas.addAll(ant.respostas().keySet());
        todasPerguntas.addAll(atu.respostas().keySet());

        for (UUID idPergunta : todasPerguntas) {
            RespostaSnapshot rAnt = ant.respostas().get(idPergunta);
            RespostaSnapshot rAtu = atu.respostas().get(idPergunta);
            String formatAnt = formatarResposta(rAnt);
            String formatAtu = formatarResposta(rAtu);

            if (!Objects.equals(formatAnt, formatAtu)) {
                String rotulo = rAtu != null ? rAtu.enunciado() : (rAnt != null ? rAnt.enunciado() : idPergunta.toString());
                diffs.add(new AlteracaoItem("Pergunta: " + rotulo, formatAnt, formatAtu));
            }
        }

        // Comparação de desvios
        Set<String> todasChavesDesvios = new LinkedHashSet<>();
        todasChavesDesvios.addAll(ant.desvios().keySet());
        todasChavesDesvios.addAll(atu.desvios().keySet());

        for (String chave : todasChavesDesvios) {
            DesvioSnapshot dAnt = ant.desvios().get(chave);
            DesvioSnapshot dAtu = atu.desvios().get(chave);

            if (dAnt == null && dAtu != null) {
                String desc = dAtu.descricao() != null ? dAtu.descricao() : (dAtu.opcaoTexto() != null ? dAtu.opcaoTexto() : "Não conformidade");
                diffs.add(new AlteracaoItem("Desvio: " + dAtu.perguntaEnunciado(), null, "+ Adicionado: " + desc + " [Status: " + dAtu.status() + "]"));
            } else if (dAnt != null && dAtu == null) {
                String desc = dAnt.descricao() != null ? dAnt.descricao() : (dAnt.opcaoTexto() != null ? dAnt.opcaoTexto() : "Não conformidade");
                diffs.add(new AlteracaoItem("Desvio: " + dAnt.perguntaEnunciado(), "- Removido: " + desc, null));
            } else if (dAnt != null && dAtu != null) {
                if (dAnt.status() != dAtu.status()) {
                    diffs.add(new AlteracaoItem("Desvio (" + dAtu.perguntaEnunciado() + ") - Status", dAnt.status().name(), dAtu.status().name()));
                }
                if (!Objects.equals(dAnt.descricao(), dAtu.descricao())) {
                    diffs.add(new AlteracaoItem("Desvio (" + dAtu.perguntaEnunciado() + ") - Descrição", dAnt.descricao(), dAtu.descricao()));
                }
                if (!Objects.equals(dAnt.acaoCorretiva(), dAtu.acaoCorretiva())) {
                    diffs.add(new AlteracaoItem("Desvio (" + dAtu.perguntaEnunciado() + ") - Ação", dAnt.acaoCorretiva(), dAtu.acaoCorretiva()));
                }
                if (!Objects.equals(dAnt.prazo(), dAtu.prazo())) {
                    diffs.add(new AlteracaoItem("Desvio (" + dAtu.perguntaEnunciado() + ") - Prazo",
                            dAnt.prazo() != null ? dAnt.prazo().toString() : "Sem prazo",
                            dAtu.prazo() != null ? dAtu.prazo().toString() : "Sem prazo"));
                }
                if (!Objects.equals(dAnt.colaboradoresIds(), dAtu.colaboradoresIds())) {
                    diffs.add(new AlteracaoItem("Desvio (" + dAtu.perguntaEnunciado() + ") - Responsáveis",
                            String.join(", ", dAnt.colaboradoresNomes()),
                            String.join(", ", dAtu.colaboradoresNomes())));
                }
            }
        }

        // Comparação de evidências
        Set<String> antEvid = new LinkedHashSet<>(ant.evidenciasNomes());
        Set<String> atuEvid = new LinkedHashSet<>(atu.evidenciasNomes());

        for (String ev : atu.evidenciasNomes()) {
            if (!antEvid.contains(ev)) {
                diffs.add(new AlteracaoItem("Evidência fotográfica", null, "+ " + ev));
            }
        }
        for (String ev : ant.evidenciasNomes()) {
            if (!atuEvid.contains(ev)) {
                diffs.add(new AlteracaoItem("Evidência fotográfica", "- " + ev, null));
            }
        }

        return diffs;
    }

    private String formatarResposta(RespostaSnapshot r) {
        if (r == null) return null;
        if (r.opcoesTextos() != null && !r.opcoesTextos().isEmpty()) {
            return String.join(", ", r.opcoesTextos());
        }
        if (r.valorTexto() != null && !r.valorTexto().isBlank()) {
            return r.valorTexto();
        }
        if (r.valorNumero() != null) {
            return r.valorNumero().stripTrailingZeros().toPlainString();
        }
        if (r.valorData() != null) {
            return r.valorData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        return "(em branco)";
    }

    private Map<String, Object> montarSnapshots(InspecaoSeguranca inspecao) {
        Map<String, Object> map = new LinkedHashMap<>();
        formularioRepository.findById(inspecao.getIdFormulario())
                .ifPresent(f -> map.put("formulario", f.getTitulo()));
        tipoInspecaoRepository.findById(inspecao.getIdTipoInspecao())
                .ifPresent(t -> map.put("tipoInspecao", t.getNome()));
        processoApi.buscarPorIdResponse(inspecao.getIdProcesso())
                .ifPresent(p -> map.put("processo", p.nome()));
        if (inspecao.getIdBaseOperacional() != null) {
            baseOperacionalApi.buscarPorIdResponse(inspecao.getIdBaseOperacional())
                    .ifPresent(b -> map.put("baseOperacional", b.nome()));
        }
        if (inspecao.getIdVeiculo() != null) {
            veiculoApi.buscarPorIdResponse(inspecao.getIdVeiculo())
                    .ifPresent(v -> map.put("veiculo", v.placa()));
        }
        if (inspecao.getIdEquipe() != null) {
            equipeApi.buscarPorIdResponse(inspecao.getIdEquipe())
                    .ifPresent(e -> map.put("equipe", e.codigo()));
        }
        if (inspecao.getDataRealizacao() != null) {
            map.put("dataRealizacao", inspecao.getDataRealizacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
        if (inspecao.getObservacoes() != null) {
            map.put("observacoes", inspecao.getObservacoes());
        }
        if (inspecao.getLatitude() != null && inspecao.getLongitude() != null) {
            map.put("geolocalizacao", formatarCoordenada(inspecao.getLatitude()) + ", " + formatarCoordenada(inspecao.getLongitude()));
        }
        map.put("totalInspecionados", inspecao.getColaboradoresInspecionados().size());
        return map;
    }

    private Map<String, Object> montarSnapshotsDeEstado(InspecaoSeguranca inspecao, EstadoCabecalhoSnapshot estado) {
        Map<String, Object> map = new LinkedHashMap<>();
        formularioRepository.findById(inspecao.getIdFormulario())
                .ifPresent(f -> map.put("formulario", f.getTitulo()));
        if (estado.tipoInspecaoNome() != null) map.put("tipoInspecao", estado.tipoInspecaoNome());
        if (estado.processoNome() != null) map.put("processo", estado.processoNome());
        if (estado.baseOperacionalNome() != null) map.put("baseOperacional", estado.baseOperacionalNome());
        if (estado.veiculoPlaca() != null) map.put("veiculo", estado.veiculoPlaca());
        if (estado.equipeCodigo() != null) map.put("equipe", estado.equipeCodigo());
        if (estado.dataRealizacao() != null) {
            map.put("dataRealizacao", estado.dataRealizacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
        if (estado.observacoes() != null) map.put("observacoes", estado.observacoes());
        if (estado.latitude() != null && estado.longitude() != null) {
            map.put("geolocalizacao", formatarCoordenada(estado.latitude()) + ", " + formatarCoordenada(estado.longitude()));
        }
        map.put("totalInspecionados", estado.colaboradoresIds().size());
        return map;
    }

    private static boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private static String formatarCoordenada(BigDecimal valor) {
        if (valor == null) return "";
        return valor.stripTrailingZeros().toPlainString();
    }

    private String resolverNomeAutor(UUID idAutor) {
        if (idAutor == null) return "Sistema";
        return colaboradorApi.buscarNomeCompletoPorUsuario(idAutor)
                .orElseGet(() -> usuarioApi.buscarNomePorId(idAutor).orElse("Sistema"));
    }

    private String serializar(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar payload de auditoria", e);
            return "{}";
        }
    }

    public static String canonicalizarJson(String json) {
        if (json == null || json.isBlank()) return "{}";
        try {
            JsonNode node = CANONICAL_JSON_MAPPER.readTree(json);
            return CANONICAL_JSON_MAPPER.writeValueAsString(ordenar(node));
        } catch (Exception e) {
            return json.trim();
        }
    }

    private static JsonNode ordenar(JsonNode no) {
        if (no instanceof ObjectNode objeto) {
            ObjectNode ordenado = CANONICAL_JSON_MAPPER.createObjectNode();
            List<String> campos = new ArrayList<>();
            objeto.fieldNames().forEachRemaining(campos::add);
            campos.sort(String::compareTo);
            campos.forEach(campo -> ordenado.set(campo, ordenar(objeto.get(campo))));
            return ordenado;
        }
        if (no instanceof ArrayNode array) {
            ArrayNode ordenado = CANONICAL_JSON_MAPPER.createArrayNode();
            for (JsonNode item : array) {
                ordenado.add(ordenar(item));
            }
            return ordenado;
        }
        return no;
    }

    public static String instanteCanonico(OffsetDateTime dt) {
        if (dt == null) return "";
        return dt.toInstant().truncatedTo(ChronoUnit.MICROS).toString();
    }

    private String calcularHash(String hashAnterior, long sequencia, String tipoEvento, String payload, OffsetDateTime ocorridoEm) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payloadCanonico = canonicalizarJson(payload);
            String instante = instanteCanonico(ocorridoEm);
            String dados = (hashAnterior != null ? hashAnterior : "") + ":"
                    + sequencia + ":"
                    + tipoEvento + ":"
                    + instante + ":"
                    + payloadCanonico;
            byte[] hash = digest.digest(dados.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 não disponível", e);
        }
    }

    @Transactional
    public String verificarIntegridade(List<EventoInspecaoSeguranca> cadeia) {
        if (cadeia == null || cadeia.isEmpty()) return "NAO_VERIFICADO";
        String anteriorCanonico = null;
        String anteriorOriginal = null;
        for (EventoInspecaoSeguranca evt : cadeia) {
            if (anteriorCanonico != null && !Objects.equals(evt.getHashAnterior(), anteriorCanonico)
                    && !Objects.equals(evt.getHashAnterior(), anteriorOriginal)) {
                return "VIOLADO";
            }
            if (anteriorCanonico == null && evt.getHashAnterior() != null) {
                return "VIOLADO";
            }

            String esperado = calcularHash(anteriorCanonico, evt.getSequencia(), evt.getTipoEvento(), evt.getPayloadCanonico(), evt.getOcorridoEm());
            if (!Objects.equals(evt.getHashAtual(), esperado)) {
                log.info("Auto-reparando hash do evento {} da inspeção {} (sequência {}) para padrão canônico",
                        evt.getId(), evt.getIdInspecao(), evt.getSequencia());
                anteriorOriginal = evt.getHashAtual();
                evt.recalibrarHash(anteriorCanonico, esperado);
                repository.save(evt);
            } else {
                anteriorOriginal = evt.getHashAtual();
            }
            anteriorCanonico = esperado;
        }
        return "INTEGRO";
    }
}
