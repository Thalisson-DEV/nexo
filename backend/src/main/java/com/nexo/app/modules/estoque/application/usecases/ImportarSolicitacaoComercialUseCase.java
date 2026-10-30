package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoComercialRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoComercialResponse;
import com.nexo.app.modules.estoque.application.dto.ProcessamentoImportacao;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoComercialImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarSolicitacaoComercialUseCase {

    private static final int LIMITE_LINHAS = 2000;
    private static final List<String> CABECALHOS = List.of(
            "Estoque Origem", "Estoque Destino", "Codigo Material",
            "Quantidade", "Data Prevista Saque", "Observacao"
    );

    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SolicitacaoMaterialUseCases solicitacoes;

    public ImportacaoSolicitacaoComercialResponse executarValidacao(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo .csv preenchido conforme o modelo oficial.");
        }

        String nomeOriginal = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nomeOriginal.endsWith(".csv")) {
            throw new IllegalArgumentException("Formato não suportado. Envie somente um arquivo .csv UTF-8 delimitado por ponto e vírgula (;).");
        }

        List<SolicitacaoComercialImportacaoDTO> registros = lerCsv(arquivo);

        List<SolicitacaoComercialImportacaoDTO> validados = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        for (SolicitacaoComercialImportacaoDTO dto : registros) {
            SolicitacaoComercialImportacaoDTO validado = validarRegistro(dto);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            } else {
                sucessos++;
            }
            validados.add(validado);
        }

        return new ImportacaoSolicitacaoComercialResponse(validados, validados.size(), sucessos, falhas,
                ProcessamentoImportacao.validacao(sucessos, falhas));
    }

    @Transactional
    public ImportacaoSolicitacaoComercialResponse confirmarImportacao(
            ConfirmarImportacaoSolicitacaoComercialRequest request,
            UUID idUsuarioLogado
    ) {
        if (request == null || request.registros() == null || request.registros().isEmpty()) {
            throw new IllegalArgumentException("Nenhum registro para confirmar importação.");
        }

        List<SolicitacaoComercialImportacaoDTO> revalidados = request.registros().stream()
                .map(this::validarRegistro)
                .toList();

        List<SolicitacaoComercialImportacaoDTO> validos = revalidados.stream()
                .filter(r -> r.status() != ImportacaoStatus.COM_ERRO)
                .toList();

        // Agrupamento por Estoque Destino (cada estoque destino é uma solicitação independente)
        Map<String, List<SolicitacaoComercialImportacaoDTO>> grupos = validos.stream().collect(
                Collectors.groupingBy(this::gerarChaveGrupo, LinkedHashMap::new, Collectors.toList())
        );

        Map<String, String> errosGrupo = new LinkedHashMap<>();
        Set<String> gruposSucesso = new HashSet<>();
        int criados = 0;

        for (Map.Entry<String, List<SolicitacaoComercialImportacaoDTO>> entry : grupos.entrySet()) {
            List<SolicitacaoComercialImportacaoDTO> itensGrupo = entry.getValue();
            if (itensGrupo.isEmpty()) continue;

            SolicitacaoComercialImportacaoDTO primeiro = itensGrupo.get(0);
            UUID idOrigem = primeiro.idEstoqueOrigem();
            UUID idDestino = primeiro.idEstoqueDestino();

            List<SolicitacaoRequest.ItemRequest> itensRequest = new ArrayList<>();
            for (SolicitacaoComercialImportacaoDTO item : itensGrupo) {
                if (item.idMaterial() != null && item.quantidade() != null && item.quantidade().compareTo(BigDecimal.ZERO) > 0) {
                    itensRequest.add(new SolicitacaoRequest.ItemRequest(item.idMaterial(), item.quantidade()));
                }
            }

            if (itensRequest.isEmpty()) {
                errosGrupo.put(entry.getKey(), "Itens: A solicitação não possui itens válidos para criação.");
                continue;
            }

            try {
                Estoque estoqueDestino = estoqueRepository.findById(idDestino)
                        .orElseThrow(() -> new IllegalArgumentException("Estoque de destino: Estoque de destino não encontrado."));
                SolicitacaoRequest solicitacaoRequest = new SolicitacaoRequest(
                        idOrigem, idDestino, primeiro.dataPrevista(), estoqueDestino.getIdEquipe(),
                        null, false, itensRequest, primeiro.observacao());

                solicitacoes.criarDireta(solicitacaoRequest, idUsuarioLogado, idUsuarioLogado);
                gruposSucesso.add(entry.getKey());
                criados++;
            } catch (Exception ex) {
                String msgErro = extrairMensagemErroComercial(ex);
                log.warn("Falha ao confirmar solicitacao comercial grupo {}: {}", entry.getKey(), msgErro);
                errosGrupo.put(entry.getKey(), msgErro);
            }
        }

        log.info("Importação de solicitações comerciais finalizada: {} grupos criados com sucesso por usuário {}",
                criados, idUsuarioLogado);

        List<SolicitacaoComercialImportacaoDTO> resposta = revalidados.stream()
                .map(r -> {
                    if (r.status() == ImportacaoStatus.COM_ERRO) {
                        return r;
                    }
                    String chave = gerarChaveGrupo(r);
                    if (gruposSucesso.contains(chave)) {
                        return r.withStatus(ImportacaoStatus.CONCLUIDO, null);
                    }
                    if (errosGrupo.containsKey(chave)) {
                        return r.withStatus(ImportacaoStatus.COM_ERRO, errosGrupo.get(chave));
                    }
                    return r.withStatus(ImportacaoStatus.CONCLUIDO, null);
                })
                .toList();

        long totalFalhas = resposta.stream().filter(r -> r.status() == ImportacaoStatus.COM_ERRO).count();
        long totalSucesso = resposta.stream().filter(r -> r.status() == ImportacaoStatus.CONCLUIDO).count();

        return new ImportacaoSolicitacaoComercialResponse(
                resposta, resposta.size(), (int) totalSucesso, (int) totalFalhas,
                ProcessamentoImportacao.confirmacao((int) totalSucesso, (int) totalFalhas)
        );
    }

    private String gerarChaveGrupo(SolicitacaoComercialImportacaoDTO item) {
        String destinoKey = item.idEstoqueDestino() != null
                ? item.idEstoqueDestino().toString()
                : (item.nomeEstoqueDestino() != null ? item.nomeEstoqueDestino().trim().toUpperCase(Locale.ROOT) : "DEST_INDEFINIDO");
        String origemKey = item.idEstoqueOrigem() != null ? item.idEstoqueOrigem().toString()
                : (item.nomeEstoqueOrigem() != null ? item.nomeEstoqueOrigem().trim().toUpperCase(Locale.ROOT) : "");
        String dataKey = item.dataPrevista() == null ? "" : item.dataPrevista().toString();
        return "DEST::" + destinoKey + "::" + origemKey + "::" + dataKey;
    }

    private String extrairMensagemErroComercial(Exception ex) {
        if (ex instanceof PreenchimentoInvalidoException preenchimento) {
            return preenchimento.getErros().entrySet().stream()
                    .map(erro -> rotuloCampoComercial(erro.getKey()) + ": " + erro.getValue())
                    .collect(Collectors.joining("; "));
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "Não foi possível criar esta solicitação comercial.";
    }

    private String rotuloCampoComercial(String campo) {
        if (campo == null) return "Campo";
        return switch (campo) {
            case "idEstoqueOrigem", "origem", "nomeEstoqueOrigem" -> "Estoque de origem";
            case "idEstoqueDestino", "destino", "nomeEstoqueDestino" -> "Estoque de destino";
            case "idEquipe", "equipe" -> "Equipe";
            case "dataPrevista" -> "Data prevista";
            case "observacao" -> "Observação";
            case "itens" -> "Itens";
            default -> {
                if (campo.startsWith("itens[") && campo.endsWith(".idMaterial")) {
                    yield "Material do item";
                }
                if (campo.startsWith("itens[") && campo.endsWith(".quantidade")) {
                    yield "Quantidade do item";
                }
                if (campo.startsWith("itens[")) {
                    yield "Item da solicitação";
                }
                yield campo;
            }
        };
    }

    public SolicitacaoComercialImportacaoDTO validarRegistro(SolicitacaoComercialImportacaoDTO dto) {
        List<String> erros = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        UUID idOrigem = dto.idEstoqueOrigem();
        Estoque origemResolvida = null;
        if (idOrigem != null) {
            Optional<Estoque> est = estoqueRepository.findById(idOrigem);
            if (est.isEmpty() && dto.nomeEstoqueOrigem() != null && !dto.nomeEstoqueOrigem().isBlank()) {
                est = estoqueRepository.findByNomeIdentificadorIgnoreCase(dto.nomeEstoqueOrigem().trim());
            }
            if (est.isEmpty()) {
                erros.add("Estoque de origem: Estoque de origem não encontrado.");
            } else {
                if (!est.get().isAtivo()) {
                    erros.add("Estoque de origem: Estoque de origem está inativo.");
                } else if (est.get().getId() != null) {
                    idOrigem = est.get().getId();
                    origemResolvida = est.get();
                }
            }
        } else if (dto.nomeEstoqueOrigem() == null || dto.nomeEstoqueOrigem().isBlank()) {
            erros.add("Estoque de origem: Estoque de origem é obrigatório.");
        } else {
            String termo = dto.nomeEstoqueOrigem().trim();
            Optional<Estoque> est = estoqueRepository.findByNomeIdentificadorIgnoreCase(termo);
            if (est.isEmpty() && isUuid(termo)) {
                est = estoqueRepository.findById(UUID.fromString(termo));
            }
            if (est.isEmpty()) {
                erros.add("Estoque de origem: Estoque de origem '" + termo + "' não encontrado.");
            } else if (!est.get().isAtivo()) {
                erros.add("Estoque de origem: Estoque de origem '" + termo + "' está inativo.");
            } else {
                idOrigem = est.get().getId();
                origemResolvida = est.get();
            }
        }

        if (origemResolvida != null && (origemResolvida.getTipoEstoque() == null
                || !"FISICO".equalsIgnoreCase(origemResolvida.getTipoEstoque().getNome()))) {
            erros.add("Estoque de origem: O estoque de origem deve ser do tipo FISICO.");
        }

        UUID idDestino = dto.idEstoqueDestino();
        Estoque destinoResolvido = null;
        if (idDestino != null) {
            Optional<Estoque> est = estoqueRepository.findById(idDestino);
            if (est.isEmpty() && dto.nomeEstoqueDestino() != null && !dto.nomeEstoqueDestino().isBlank()) {
                est = estoqueRepository.findByNomeIdentificadorIgnoreCase(dto.nomeEstoqueDestino().trim());
            }
            if (est.isEmpty()) {
                erros.add("Estoque de destino: Estoque de destino não encontrado.");
            } else {
                if (!est.get().isAtivo()) {
                    erros.add("Estoque de destino: Estoque de destino está inativo.");
                } else if (est.get().getId() != null) {
                    idDestino = est.get().getId();
                    destinoResolvido = est.get();
                }
            }
        } else if (dto.nomeEstoqueDestino() == null || dto.nomeEstoqueDestino().isBlank()) {
            erros.add("Estoque de destino: Estoque de destino é obrigatório.");
        } else {
            String termo = dto.nomeEstoqueDestino().trim();
            Optional<Estoque> est = estoqueRepository.findByNomeIdentificadorIgnoreCase(termo);
            if (est.isEmpty() && isUuid(termo)) {
                est = estoqueRepository.findById(UUID.fromString(termo));
            }
            if (est.isEmpty()) {
                erros.add("Estoque de destino: Estoque de destino '" + termo + "' não encontrado.");
            } else if (!est.get().isAtivo()) {
                erros.add("Estoque de destino: Estoque de destino '" + termo + "' está inativo.");
            } else {
                idDestino = est.get().getId();
                destinoResolvido = est.get();
            }
        }

        if (destinoResolvido != null
                && (!destinoResolvido.isTipoCampo() || destinoResolvido.getIdEquipe() == null)) {
            erros.add("Estoque de destino: O estoque de destino deve ser do tipo CAMPO e estar vinculado a uma equipe.");
        }

        if (idOrigem != null && idDestino != null && idOrigem.equals(idDestino)) {
            erros.add("Estoque de destino: O estoque de destino deve ser diferente do estoque de origem.");
        }

        UUID idMaterial = dto.idMaterial();
        String nomeMaterial = dto.nomeMaterial();
        if (idMaterial != null) {
            Optional<Material> mat = materialRepository.findById(idMaterial);
            if (mat.isEmpty() && dto.codigoMaterial() != null && !dto.codigoMaterial().isBlank()) {
                mat = materialRepository.findByCodigoIgnoreCase(dto.codigoMaterial().trim());
            }
            if (mat.isPresent()) {
                if (!mat.get().isAtivo()) {
                    erros.add("Material: Material está inativo no sistema.");
                } else {
                    if (mat.get().getId() != null) {
                        idMaterial = mat.get().getId();
                    }
                    nomeMaterial = mat.get().getDescricao();
                }
            }
        } else if (dto.codigoMaterial() == null || dto.codigoMaterial().isBlank()) {
            erros.add("Material: Código do material é obrigatório.");
        } else {
            String codTermo = dto.codigoMaterial().trim();
            Optional<Material> mat = materialRepository.findByCodigoIgnoreCase(codTermo);
            if (mat.isEmpty() && isUuid(codTermo)) {
                mat = materialRepository.findById(UUID.fromString(codTermo));
            }
            if (mat.isEmpty()) {
                erros.add("Material: Material '" + codTermo + "' não encontrado.");
            } else if (!mat.get().isAtivo()) {
                erros.add("Material: Material '" + codTermo + "' está inativo no sistema.");
            } else {
                idMaterial = mat.get().getId();
                nomeMaterial = mat.get().getDescricao();
            }
        }

        if (dto.quantidade() == null) {
            erros.add("Quantidade: Quantidade do material é obrigatória.");
        } else if (dto.quantidade().compareTo(BigDecimal.ZERO) <= 0) {
            erros.add("Quantidade: Quantidade deve ser maior que zero.");
        }

        if (dto.dataPrevista() == null) {
            erros.add("Data prevista: Data prevista de saque é obrigatória (use DD/MM/AAAA).");
        } else if (dto.dataPrevista().isBefore(LocalDate.now().plusDays(2))) {
            avisos.add("Data prevista: Solicitada fora do prazo recomendado de 48h.");
        }

        if (!erros.isEmpty()) {
            return dto.withResolved(idOrigem, idDestino, idMaterial, nomeMaterial,
                    ImportacaoStatus.COM_ERRO, String.join("; ", erros));
        }

        if (!avisos.isEmpty()) {
            return dto.withResolved(idOrigem, idDestino, idMaterial, nomeMaterial,
                    ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, String.join("; ", avisos));
        }

        return dto.withResolved(idOrigem, idDestino, idMaterial, nomeMaterial,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
    }

    public byte[] obterModeloCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(String.join(";", CABECALHOS)).append("\r\n");
        sb.append("ALMOX CENTRAL;EQ-01;MAT-001;10.500;25/09/2026;Atendimento operacional programado\r\n");
        sb.append("ALMOX CENTRAL;EQ-01;MAT-002;5.000;25/09/2026;Atendimento operacional programado\r\n");
        sb.append("ALMOX CENTRAL;EQ-02;MAT-003;2.000;26/09/2026;Reposicao de estoque de campo\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<SolicitacaoComercialImportacaoDTO> lerCsv(MultipartFile arquivo) {
        List<SolicitacaoComercialImportacaoDTO> lista = new ArrayList<>();
        try {
            byte[] bytes = arquivo.getBytes();
            String conteudo = new String(bytes, StandardCharsets.UTF_8);
            if (conteudo.startsWith("\uFEFF")) {
                conteudo = conteudo.substring(1);
            }

            char separador = detectarSeparador(conteudo);
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setDelimiter(separador)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .setCommentMarker('#')
                    .build();

            try (CSVParser parser = format.parse(new StringReader(conteudo))) {
                Map<String, Integer> headerMap = mapearCabecalhos(parser.getHeaderNames());
                int numLinha = 1;

                for (CSVRecord record : parser) {
                    numLinha++;
                    if (lista.size() >= LIMITE_LINHAS) break;
                    if (record.values().length == 0 || Arrays.stream(record.values()).allMatch(String::isBlank)) {
                        continue;
                    }

                    lista.add(parsearRegistro(record, headerMap, numLinha));
                }
            }
        } catch (Exception e) {
            log.error("Falha ao ler arquivo CSV de solicitações comerciais", e);
            throw new IllegalArgumentException("Falha ao ler CSV: " + e.getMessage(), e);
        }
        return lista;
    }

    private SolicitacaoComercialImportacaoDTO parsearRegistro(CSVRecord record, Map<String, Integer> headerMap, int numLinha) {
        String origem = obterColuna(record, headerMap, "estoqueorigem", "origem", "almoxarifadoorigem");
        String destino = obterColuna(record, headerMap, "estoquedestino", "destino");
        String codMat = obterColuna(record, headerMap, "codigomaterial", "material", "codigo");
        String qtdStr = obterColuna(record, headerMap, "quantidade", "qtd");
        String dataPrevistaStr = obterColuna(record, headerMap, "dataprevistasaque", "dataprevista", "previsaosaque");
        String obs = obterColuna(record, headerMap, "observacao", "obs");

        BigDecimal quantidade = parseQuantidade(qtdStr);
        LocalDate dataPrevista = parseData(dataPrevistaStr);

        return new SolicitacaoComercialImportacaoDTO(
                numLinha, origem, null, destino, null, null, null,
                codMat, null, null, quantidade, dataPrevista, obs, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private LocalDate parseData(String valor) {
        if (valor == null || valor.isBlank()) return null;
        for (DateTimeFormatter formato : List.of(
                DateTimeFormatter.ofPattern("dd/MM/uuuu"), DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return LocalDate.parse(valor.trim(), formato);
            } catch (DateTimeParseException ignored) {
                // Tenta o próximo formato aceito.
            }
        }
        return null;
    }

    private BigDecimal parseQuantidade(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            String norm = str.trim().replace(",", ".");
            return new BigDecimal(norm);
        } catch (Exception e) {
            return null;
        }
    }

    private char detectarSeparador(String conteudo) {
        String primeiraLinha = conteudo.split("\r?\n")[0];
        long pontoVirgula = primeiraLinha.chars().filter(c -> c == ';').count();
        long virgula = primeiraLinha.chars().filter(c -> c == ',').count();
        long tab = primeiraLinha.chars().filter(c -> c == '\t').count();
        if (tab > pontoVirgula && tab > virgula) return '\t';
        return pontoVirgula >= virgula ? ';' : ',';
    }

    private Map<String, Integer> mapearCabecalhos(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        if (headers == null) return map;
        for (int i = 0; i < headers.size(); i++) {
            String raw = headers.get(i);
            if (raw == null) continue;
            String norm = java.text.Normalizer.normalize(raw.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT),
                    java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .replace("_", "")
                    .replace(" ", "")
                    .replace("-", "");
            map.put(norm, i);
        }
        return map;
    }

    private String obterColuna(CSVRecord record, Map<String, Integer> headerMap, String... chaves) {
        for (String chave : chaves) {
            Integer idx = headerMap.get(chave);
            if (idx != null && idx < record.size()) {
                String val = record.get(idx);
                if (val != null && !val.isBlank()) {
                    return val.trim();
                }
            }
        }
        return null;
    }

    private boolean isUuid(String valor) {
        if (valor == null || valor.length() != 36) return false;
        try {
            UUID.fromString(valor);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
