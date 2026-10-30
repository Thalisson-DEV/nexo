package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoMovimentacaoRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoMovimentacaoResponse;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoImportacaoDTO;
import com.nexo.app.modules.estoque.application.dto.ProcessamentoImportacao;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.domain.exception.BaseBusinessException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarMovimentacaoEstoqueUseCase {

    private static final int LIMITE_LINHAS = 2000;
    private static final List<String> CABECALHOS = List.of(
            "Tipo Movimentacao", "Estoque Origem", "Estoque Destino",
            "Codigo Material", "Quantidade", "Numeros de Serie",
            "Condicao", "Observacao"
    );

    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository serieRepository;
    private final MovimentacaoEstoqueUseCases movimentacaoEstoqueUseCases;

    public ImportacaoMovimentacaoResponse executarValidacao(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo .csv preenchido conforme o modelo oficial.");
        }

        String nomeOriginal = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nomeOriginal.endsWith(".csv")) {
            throw new IllegalArgumentException("Formato não suportado. Envie somente um arquivo .csv UTF-8 delimitado por ponto e vírgula (;).");
        }

        List<MovimentacaoImportacaoDTO> registros = lerCsv(arquivo);

        // Mapeia seriais no arquivo para detecção de duplicidades inter-linhas
        Map<String, Integer> seriaisArquivo = new HashMap<>();

        List<MovimentacaoImportacaoDTO> validados = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        for (MovimentacaoImportacaoDTO dto : registros) {
            MovimentacaoImportacaoDTO validado = validarRegistro(dto, seriaisArquivo);
            if (validado.status() == ImportacaoStatus.COM_ERRO) {
                falhas++;
            } else {
                sucessos++;
            }
            validados.add(validado);
        }

        return new ImportacaoMovimentacaoResponse(validados, validados.size(), sucessos, falhas,
                ProcessamentoImportacao.validacao(sucessos, falhas));
    }

    public ImportacaoMovimentacaoResponse confirmarImportacao(
            ConfirmarImportacaoMovimentacaoRequest request,
            UUID idUsuarioLogado
    ) {
        if (request == null || request.registros() == null || request.registros().isEmpty()) {
            throw new IllegalArgumentException("Nenhum registro para confirmar importação.");
        }

        Map<String, Integer> seriaisRevalidacao = new HashMap<>();
        List<MovimentacaoImportacaoDTO> revalidados = request.registros().stream()
                .map(r -> validarRegistro(r, seriaisRevalidacao))
                .toList();

        List<MovimentacaoImportacaoDTO> validos = revalidados.stream()
                .filter(r -> r.status() != ImportacaoStatus.COM_ERRO)
                .toList();

        // Agrupamento por (Tipo + Origem + Destino)
        Map<String, List<MovimentacaoImportacaoDTO>> grupos = validos.stream().collect(
                Collectors.groupingBy(this::gerarChaveGrupo, LinkedHashMap::new, Collectors.toList())
        );

        Map<String, String> errosGrupo = new LinkedHashMap<>();
        Set<String> gruposSucesso = new HashSet<>();
        int criados = 0;

        for (Map.Entry<String, List<MovimentacaoImportacaoDTO>> entry : grupos.entrySet()) {
            List<MovimentacaoImportacaoDTO> itensGrupo = entry.getValue();
            if (itensGrupo.isEmpty()) continue;

            MovimentacaoImportacaoDTO primeiro = itensGrupo.get(0);
            String tipo = primeiro.tipo();
            UUID idOrigem = primeiro.idEstoqueOrigem();
            UUID idDestino = primeiro.idEstoqueDestino();

            List<MovimentacaoBulkRequest.ItemMovimentacaoRequest> itensBulk = new ArrayList<>();
            for (MovimentacaoImportacaoDTO item : itensGrupo) {
                CondicaoMaterial cond = item.condicao() != null ? item.condicao() : CondicaoMaterial.NOVO;
                if (Boolean.TRUE.equals(item.detalhaSerie())) {
                    List<String> seriais = extrairNumerosSerie(item.numSerie());
                    for (String s : seriais) {
                        itensBulk.add(new MovimentacaoBulkRequest.ItemMovimentacaoRequest(
                                item.idMaterial(), BigDecimal.ONE, s, cond));
                    }
                } else {
                    itensBulk.add(new MovimentacaoBulkRequest.ItemMovimentacaoRequest(
                            item.idMaterial(), item.quantidade(), null, cond));
                }
            }

            if (itensBulk.isEmpty()) {
                errosGrupo.put(entry.getKey(), "A operação não possui itens válidos para execução.");
                continue;
            }

            try {
                if ("ENTRADA".equalsIgnoreCase(tipo)) {
                    movimentacaoEstoqueUseCases.entradaLote(
                            new MovimentacaoBulkRequest(null, idDestino, itensBulk),
                            idUsuarioLogado
                    );
                } else if ("SAIDA".equalsIgnoreCase(tipo)) {
                    movimentacaoEstoqueUseCases.baixaLote(
                            new MovimentacaoBulkRequest(idOrigem, null, itensBulk),
                            idUsuarioLogado
                    );
                } else if ("TRANSFERENCIA".equalsIgnoreCase(tipo)) {
                    movimentacaoEstoqueUseCases.transferenciaLote(
                            new MovimentacaoBulkRequest(idOrigem, idDestino, itensBulk),
                            idUsuarioLogado
                    );
                } else {
                    throw new InvarianteVioladaException("Tipo de movimentação desconhecido: " + tipo);
                }

                gruposSucesso.add(entry.getKey());
                criados += itensGrupo.size();
            } catch (Exception ex) {
                String msgErro = extrairMensagemErro(ex);
                log.warn("Falha ao confirmar importação grupo {}: {}", entry.getKey(), msgErro);
                errosGrupo.put(entry.getKey(), msgErro);
            }
        }

        log.info("Importação de movimentações de estoque finalizada: {} itens processados com sucesso por usuário {}",
                criados, idUsuarioLogado);

        List<MovimentacaoImportacaoDTO> resposta = revalidados.stream()
                .map(r -> {
                    if (r.status() == ImportacaoStatus.COM_ERRO) {
                        return r;
                    }
                    String chave = gerarChaveGrupo(r);
                    if (gruposSucesso.contains(chave)) {
                        return r.withStatus(ImportacaoStatus.CONCLUIDO, null, null);
                    }
                    if (errosGrupo.containsKey(chave)) {
                        return r.withStatus(ImportacaoStatus.COM_ERRO, "operacao", errosGrupo.get(chave));
                    }
                    return r.withStatus(ImportacaoStatus.CONCLUIDO, null, null);
                })
                .toList();

        long totalFalhas = resposta.stream().filter(r -> r.status() == ImportacaoStatus.COM_ERRO).count();
        long totalSucesso = resposta.stream().filter(r -> r.status() == ImportacaoStatus.CONCLUIDO).count();

        return new ImportacaoMovimentacaoResponse(
                resposta, resposta.size(), (int) totalSucesso, (int) totalFalhas,
                ProcessamentoImportacao.confirmacao((int) totalSucesso, (int) totalFalhas)
        );
    }

    public MovimentacaoImportacaoDTO validarRegistro(MovimentacaoImportacaoDTO dto, Map<String, Integer> seriaisArquivo) {
        List<String> erros = new ArrayList<>();
        String campoErro = null;

        // 1. Validar Tipo
        String tipoNorm = normalizarTipo(dto.tipo());
        if (tipoNorm == null) {
            erros.add("Tipo de movimentação: informe ENTRADA, SAIDA ou TRANSFERENCIA.");
            campoErro = "tipo";
        }

        // 2. Condição
        CondicaoMaterial condicaoEfetiva = dto.condicao() != null ? dto.condicao() : CondicaoMaterial.NOVO;

        // 3. Estoque de Origem
        UUID idOrigem = dto.idEstoqueOrigem();
        Estoque origemResolvida = null;
        if ("SAIDA".equals(tipoNorm) || "TRANSFERENCIA".equals(tipoNorm)) {
            if (idOrigem != null) {
                Optional<Estoque> est = estoqueRepository.findById(idOrigem);
                if (est.isEmpty() && dto.nomeEstoqueOrigem() != null && !dto.nomeEstoqueOrigem().isBlank()) {
                    est = buscarEstoquePorTermo(dto.nomeEstoqueOrigem());
                }
                if (est.isEmpty()) {
                    erros.add("Estoque de origem: Estoque de origem não encontrado.");
                    if (campoErro == null) campoErro = "origem";
                } else if (!est.get().isAtivo()) {
                    erros.add("Estoque de origem: Estoque de origem está inativo.");
                    if (campoErro == null) campoErro = "origem";
                } else {
                    idOrigem = est.get().getId();
                    origemResolvida = est.get();
                }
            } else if (dto.nomeEstoqueOrigem() == null || dto.nomeEstoqueOrigem().isBlank()) {
                erros.add("Estoque de origem: Estoque de origem é obrigatório para " + (tipoNorm != null ? tipoNorm : "esta operação") + ".");
                if (campoErro == null) campoErro = "origem";
            } else {
                String termo = dto.nomeEstoqueOrigem().trim();
                Optional<Estoque> est = buscarEstoquePorTermo(termo);
                if (est.isEmpty()) {
                    erros.add("Estoque de origem: Estoque de origem '" + termo + "' não encontrado.");
                    if (campoErro == null) campoErro = "origem";
                } else if (!est.get().isAtivo()) {
                    erros.add("Estoque de origem: Estoque de origem '" + termo + "' está inativo.");
                    if (campoErro == null) campoErro = "origem";
                } else {
                    idOrigem = est.get().getId();
                    origemResolvida = est.get();
                }
            }
        } else {
            // Para ENTRADA, origem não se aplica
            idOrigem = null;
        }

        // 4. Estoque de Destino
        UUID idDestino = dto.idEstoqueDestino();
        Estoque destinoResolvido = null;
        if ("ENTRADA".equals(tipoNorm) || "TRANSFERENCIA".equals(tipoNorm)) {
            if (idDestino != null) {
                Optional<Estoque> est = estoqueRepository.findById(idDestino);
                if (est.isEmpty() && dto.nomeEstoqueDestino() != null && !dto.nomeEstoqueDestino().isBlank()) {
                    est = buscarEstoquePorTermo(dto.nomeEstoqueDestino());
                }
                if (est.isEmpty()) {
                    erros.add("Estoque de destino: Estoque de destino não encontrado.");
                    if (campoErro == null) campoErro = "destino";
                } else if (!est.get().isAtivo()) {
                    erros.add("Estoque de destino: Estoque de destino está inativo.");
                    if (campoErro == null) campoErro = "destino";
                } else {
                    idDestino = est.get().getId();
                    destinoResolvido = est.get();
                }
            } else if (dto.nomeEstoqueDestino() == null || dto.nomeEstoqueDestino().isBlank()) {
                erros.add("Estoque de destino: Estoque de destino é obrigatório para " + (tipoNorm != null ? tipoNorm : "esta operação") + ".");
                if (campoErro == null) campoErro = "destino";
            } else {
                String termo = dto.nomeEstoqueDestino().trim();
                Optional<Estoque> est = buscarEstoquePorTermo(termo);
                if (est.isEmpty()) {
                    erros.add("Estoque de destino: Estoque de destino '" + termo + "' não encontrado.");
                    if (campoErro == null) campoErro = "destino";
                } else if (!est.get().isAtivo()) {
                    erros.add("Estoque de destino: Estoque de destino '" + termo + "' está inativo.");
                    if (campoErro == null) campoErro = "destino";
                } else {
                    idDestino = est.get().getId();
                    destinoResolvido = est.get();
                }
            }
        } else {
            // Para SAIDA, destino não se aplica
            idDestino = null;
        }

        // Validação cruzada de transferência
        if ("TRANSFERENCIA".equals(tipoNorm)) {
            if (idOrigem != null && idDestino != null && idOrigem.equals(idDestino)) {
                erros.add("Estoque de destino: O estoque de destino deve ser diferente do estoque de origem.");
                if (campoErro == null) campoErro = "destino";
            }
            if (condicaoEfetiva != CondicaoMaterial.NOVO) {
                erros.add("Condição: Transferências são permitidas apenas para materiais na condição NOVO.");
                if (campoErro == null) campoErro = "condicao";
            }
        }

        // 5. Material
        UUID idMaterial = dto.idMaterial();
        String nomeMaterial = dto.nomeMaterial();
        Boolean detalhaSerie = dto.detalhaSerie();
        Material matResolvido = null;

        if (idMaterial != null) {
            Optional<Material> mat = materialRepository.findById(idMaterial);
            if (mat.isEmpty() && dto.codigoMaterial() != null && !dto.codigoMaterial().isBlank()) {
                mat = buscarMaterialPorTermo(dto.codigoMaterial());
            }
            if (mat.isPresent()) {
                if (!mat.get().isAtivo()) {
                    erros.add("Material: Material está inativo no sistema.");
                    if (campoErro == null) campoErro = "material";
                } else {
                    matResolvido = mat.get();
                    idMaterial = matResolvido.getId();
                    nomeMaterial = matResolvido.getDescricao();
                    detalhaSerie = matResolvido.isDetalhaSerie();
                }
            } else {
                erros.add("Material: Material não encontrado no sistema.");
                if (campoErro == null) campoErro = "material";
            }
        } else if (dto.codigoMaterial() == null || dto.codigoMaterial().isBlank()) {
            erros.add("Material: Código do material é obrigatório.");
            if (campoErro == null) campoErro = "material";
        } else {
            String codTermo = dto.codigoMaterial().trim();
            Optional<Material> mat = buscarMaterialPorTermo(codTermo);
            if (mat.isEmpty()) {
                erros.add("Material: Material '" + codTermo + "' não encontrado.");
                if (campoErro == null) campoErro = "material";
            } else if (!mat.get().isAtivo()) {
                erros.add("Material: Material '" + codTermo + "' está inativo no sistema.");
                if (campoErro == null) campoErro = "material";
            } else {
                matResolvido = mat.get();
                idMaterial = matResolvido.getId();
                nomeMaterial = matResolvido.getDescricao();
                detalhaSerie = matResolvido.isDetalhaSerie();
            }
        }

        // 6. Quantidade e Números de Série
        BigDecimal qtdAjustada = dto.quantidade();
        List<String> seriais = extrairNumerosSerie(dto.numSerie());

        if (matResolvido != null) {
            if (matResolvido.isDetalhaSerie()) {
                if (seriais.isEmpty()) {
                    erros.add("Número de Série: O material '" + matResolvido.getCodigo() + "' exige número de série.");
                    if (campoErro == null) campoErro = "numSerie";
                } else {
                    // Checar duplicatas na mesma linha
                    Set<String> setUnicos = new HashSet<>();
                    for (String s : seriais) {
                        if (!setUnicos.add(s.toUpperCase(Locale.ROOT))) {
                            erros.add("Número de Série: Número de série duplicado na mesma linha: '" + s + "'.");
                            if (campoErro == null) campoErro = "numSerie";
                        }
                    }

                    // Checar duplicatas entre linhas do arquivo
                    if (seriaisArquivo != null) {
                        int numLinhaAtual = dto.linha() != null ? dto.linha() : 0;
                        for (String s : seriais) {
                            String sUpper = s.toUpperCase(Locale.ROOT);
                            if (seriaisArquivo.containsKey(sUpper)) {
                                erros.add("Número de Série: Número de série '" + s + "' repetido na linha " + seriaisArquivo.get(sUpper) + ".");
                                if (campoErro == null) campoErro = "numSerie";
                            } else {
                                seriaisArquivo.put(sUpper, numLinhaAtual);
                            }
                        }
                    }

                    // Validação de quantidade vs seriais
                    if (qtdAjustada != null && qtdAjustada.compareTo(BigDecimal.ZERO) > 0) {
                        if (qtdAjustada.compareTo(BigDecimal.valueOf(seriais.size())) != 0) {
                            erros.add("Quantidade: Quantidade informada (" + qtdAjustada + ") diverge da quantidade de seriais fornecidos (" + seriais.size() + ").");
                            if (campoErro == null) campoErro = "quantidade";
                        }
                    } else {
                        // Se não informou quantidade ou é <= 0, deduz pelo número de seriais
                        qtdAjustada = BigDecimal.valueOf(seriais.size());
                    }

                    // Validações no banco para cada serial
                    for (String s : seriais) {
                        if ("ENTRADA".equals(tipoNorm)) {
                            if (serieRepository.findByNumSerieAndAtivoTrue(s).isPresent()) {
                                erros.add("Número de Série: O número de série '" + s + "' já consta ativo no sistema.");
                                if (campoErro == null) campoErro = "numSerie";
                            }
                        } else if ("SAIDA".equals(tipoNorm) && origemResolvida != null) {
                            if (serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                                    origemResolvida.getId(), matResolvido.getId(), s, condicaoEfetiva).isEmpty()) {
                                erros.add("Número de Série: Serial '" + s + "' (" + condicaoEfetiva + ") não localizado no estoque de origem (" + origemResolvida.getNomeIdentificador() + ").");
                                if (campoErro == null) campoErro = "numSerie";
                            }
                        } else if ("TRANSFERENCIA".equals(tipoNorm) && origemResolvida != null) {
                            if (serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                                    origemResolvida.getId(), matResolvido.getId(), s, CondicaoMaterial.NOVO).isEmpty()) {
                                erros.add("Número de Série: Serial '" + s + "' (NOVO) não localizado no estoque de origem (" + origemResolvida.getNomeIdentificador() + ").");
                                if (campoErro == null) campoErro = "numSerie";
                            }
                        }
                    }
                }
            } else {
                // Material não detalha série
                if (!seriais.isEmpty()) {
                    erros.add("Número de Série: O material '" + matResolvido.getCodigo() + "' não permite controle por número de série.");
                    if (campoErro == null) campoErro = "numSerie";
                }
                if (qtdAjustada == null || qtdAjustada.compareTo(BigDecimal.ZERO) <= 0) {
                    erros.add("Quantidade: Quantidade do material deve ser maior que zero.");
                    if (campoErro == null) campoErro = "quantidade";
                } else if (("SAIDA".equals(tipoNorm) || "TRANSFERENCIA".equals(tipoNorm)) && origemResolvida != null) {
                    // Validar saldo em estoque para materiais não-serializados
                    Optional<Saldo> saldoOpt = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(
                            origemResolvida.getId(), matResolvido.getId(), condicaoEfetiva);
                    BigDecimal saldoDisponivel = saldoOpt.map(Saldo::getQuantidade).orElse(BigDecimal.ZERO);
                    if (saldoDisponivel.compareTo(qtdAjustada) < 0) {
                        erros.add("Quantidade: Saldo insuficiente no estoque de origem (" +
                                origemResolvida.getNomeIdentificador() + "). Saldo disponível: " +
                                saldoDisponivel + ", solicitado: " + qtdAjustada + ".");
                        if (campoErro == null) campoErro = "quantidade";
                    }
                }
            }
        } else {
            // Material não encontrado
            if (qtdAjustada == null || qtdAjustada.compareTo(BigDecimal.ZERO) <= 0) {
                if (!seriais.isEmpty()) {
                    qtdAjustada = BigDecimal.valueOf(seriais.size());
                } else {
                    erros.add("Quantidade: Quantidade deve ser maior que zero.");
                    if (campoErro == null) campoErro = "quantidade";
                }
            }
        }

        String nomeOrigemExibicao = origemResolvida != null ? origemResolvida.getNomeIdentificador() : dto.nomeEstoqueOrigem();
        String nomeDestinoExibicao = destinoResolvido != null ? destinoResolvido.getNomeIdentificador() : dto.nomeEstoqueDestino();
        String codMatExibicao = matResolvido != null ? matResolvido.getCodigo() : dto.codigoMaterial();

        if (!erros.isEmpty()) {
            return dto.withResolved(
                    tipoNorm != null ? tipoNorm : dto.tipo(),
                    nomeOrigemExibicao, idOrigem,
                    nomeDestinoExibicao, idDestino,
                    codMatExibicao, idMaterial, nomeMaterial, detalhaSerie,
                    qtdAjustada, condicaoEfetiva,
                    ImportacaoStatus.COM_ERRO, campoErro, String.join("; ", erros)
            );
        }

        return dto.withResolved(
                tipoNorm,
                nomeOrigemExibicao, idOrigem,
                nomeDestinoExibicao, idDestino,
                codMatExibicao, idMaterial, nomeMaterial, detalhaSerie,
                qtdAjustada, condicaoEfetiva,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null, null
        );
    }

    public byte[] obterModeloCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(String.join(";", CABECALHOS)).append("\r\n");
        sb.append("# Exemplo: ENTRADA;;ALMOX CENTRAL;MAT-001;2;SN-1001, SN-1002;NOVO;Entrada de novos equipamentos\r\n");
        sb.append("# Exemplo: SAIDA;ALMOX CENTRAL;;MAT-002;5;;NOVO;Baixa avulsa para descarte\r\n");
        sb.append("# Exemplo: TRANSFERENCIA;ALMOX CENTRAL;BASE SUL;MAT-001;1;SN-1003;NOVO;Transferencia entre bases\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static List<String> extrairNumerosSerie(String texto) {
        if (texto == null || texto.isBlank()) {
            return Collections.emptyList();
        }
        String normalizado = texto.trim();
        String[] partes;
        if (normalizado.contains(",") || normalizado.contains(";") || normalizado.contains("|")
                || normalizado.contains("\n") || normalizado.contains("\r") || normalizado.contains("\t")
                || normalizado.contains("/") || normalizado.contains("\\")) {
            partes = normalizado.split("[,;\\n\\r\\t|/\\\\]+");
        } else if (normalizado.contains(" ")) {
            partes = normalizado.split("\\s+");
        } else {
            partes = new String[]{ normalizado };
        }
        return Arrays.stream(partes)
                .map(s -> s.replaceAll("^[\"']+|[\"']+$", "").trim())
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String normalizarTipo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpo = Normalizer.normalize(valor.trim().toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");

        if (limpo.startsWith("ENTRADA")) return "ENTRADA";
        if (limpo.startsWith("BAIXA") || limpo.startsWith("SAIDA")) return "SAIDA";
        if (limpo.startsWith("TRANSFERENCIA") || limpo.startsWith("TRANSF")) return "TRANSFERENCIA";
        return null;
    }

    private String gerarChaveGrupo(MovimentacaoImportacaoDTO item) {
        String tipoKey = item.tipo() != null ? item.tipo().toUpperCase(Locale.ROOT) : "TIPO_INDEF";
        String origemKey = item.idEstoqueOrigem() != null ? item.idEstoqueOrigem().toString() : "";
        String destinoKey = item.idEstoqueDestino() != null ? item.idEstoqueDestino().toString() : "";
        return tipoKey + "::" + origemKey + "::" + destinoKey;
    }

    private String extrairMensagemErro(Exception ex) {
        if (ex instanceof PreenchimentoInvalidoException preenchimento) {
            return preenchimento.getErros().entrySet().stream()
                    .map(erro -> erro.getKey() + ": " + erro.getValue())
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof BaseBusinessException || ex instanceof RecursoNaoEncontradoException || ex instanceof InvarianteVioladaException) {
            return ex.getMessage();
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "Não foi possível concluir a operação de estoque.";
    }

    private List<MovimentacaoImportacaoDTO> lerCsv(MultipartFile arquivo) {
        List<MovimentacaoImportacaoDTO> lista = new ArrayList<>();
        try {
            byte[] bytes = arquivo.getBytes();
            String conteudo = decodificarConteudo(bytes);
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
            log.error("Falha ao ler arquivo CSV de movimentações", e);
            throw new IllegalArgumentException("Falha ao ler CSV: " + e.getMessage(), e);
        }
        return lista;
    }

    private String decodificarConteudo(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            log.info("Arquivo não está em UTF-8 estrito; decodificando com Windows-1252 / ANSI");
            return new String(bytes, Charset.forName("Windows-1252"));
        }
    }

    private MovimentacaoImportacaoDTO parsearRegistro(CSVRecord record, Map<String, Integer> headerMap, int numLinha) {
        String tipo = obterColuna(record, headerMap, "tipomovimentacao", "tipo", "operacao");
        String origem = obterColuna(record, headerMap, "estoqueorigem", "origem", "almoxarifadoorigem");
        String destino = obterColuna(record, headerMap, "estoquedestino", "destino", "almoxarifadodestino");
        String codMat = obterColuna(record, headerMap, "codigomaterial", "material", "codigo");
        String qtdStr = obterColuna(record, headerMap, "quantidade", "qtd");
        String serialStr = obterColuna(record, headerMap, "numerosdeserie", "numerodeserie", "numerosserie", "numeroserie", "numserie", "seriais", "serial", "serie", "series");
        String condStr = obterColuna(record, headerMap, "condicao", "condicaomaterial", "estado");
        String obs = obterColuna(record, headerMap, "observacao", "obs");

        BigDecimal quantidade = parseQuantidade(qtdStr);
        CondicaoMaterial condicao = parseCondicao(condStr);

        return new MovimentacaoImportacaoDTO(
                numLinha, tipo, origem, null, destino, null,
                codMat, null, null, null,
                quantidade, serialStr, condicao, obs,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null, null
        );
    }

    private CondicaoMaterial parseCondicao(String str) {
        if (str == null || str.isBlank()) return CondicaoMaterial.NOVO;
        String norm = Normalizer.normalize(str.trim().toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        try {
            return CondicaoMaterial.valueOf(norm);
        } catch (IllegalArgumentException e) {
            return CondicaoMaterial.NOVO;
        }
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
        String linhaReferencia = conteudo.lines()
                .filter(linha -> !linha.isBlank() && !linha.trim().startsWith("#"))
                .findFirst()
                .orElse("");
        long pontoVirgula = linhaReferencia.chars().filter(c -> c == ';').count();
        long virgula = linhaReferencia.chars().filter(c -> c == ',').count();
        long tab = linhaReferencia.chars().filter(c -> c == '\t').count();
        if (tab > pontoVirgula && tab > virgula) return '\t';
        return pontoVirgula >= virgula ? ';' : ',';
    }

    private Map<String, Integer> mapearCabecalhos(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        if (headers == null) return map;
        for (int i = 0; i < headers.size(); i++) {
            String raw = headers.get(i);
            if (raw == null) continue;
            String norm = Normalizer.normalize(raw.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT),
                            Normalizer.Form.NFD)
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

    private Optional<Estoque> buscarEstoquePorTermo(String termo) {
        if (termo == null || termo.isBlank()) return Optional.empty();
        String t = termo.trim();
        Optional<Estoque> est = estoqueRepository.findByNomeIdentificadorIgnoreCase(t);
        if (est.isPresent()) return est;
        if (isUuid(t)) {
            est = estoqueRepository.findById(UUID.fromString(t));
            if (est.isPresent()) return est;
        }
        String normBusca = normalizarTextoParaComparacao(t);
        return estoqueRepository.findAll().stream()
                .filter(Estoque::isAtivo)
                .filter(e -> normalizarTextoParaComparacao(e.getNomeIdentificador()).equalsIgnoreCase(normBusca))
                .findFirst();
    }

    private Optional<Material> buscarMaterialPorTermo(String codTermo) {
        if (codTermo == null || codTermo.isBlank()) return Optional.empty();
        String t = codTermo.trim();
        Optional<Material> mat = materialRepository.findByCodigoIgnoreCase(t);
        if (mat.isPresent()) return mat;
        if (isUuid(t)) {
            mat = materialRepository.findById(UUID.fromString(t));
            if (mat.isPresent()) return mat;
        }
        return Optional.empty();
    }

    private String normalizarTextoParaComparacao(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
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

