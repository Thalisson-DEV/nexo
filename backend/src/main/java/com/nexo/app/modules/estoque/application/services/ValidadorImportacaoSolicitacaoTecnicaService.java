package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica.Item;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica.Mensagem;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica.Reserva;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica.Severidade;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica.Subgrupo;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.NormalizadorOcorrenciaTecnica;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.obras.AgenteResponsavelApi;
import com.nexo.app.modules.obras.AgenteResponsavelApi.AgenteResponsavelPublico;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeApi.EquipePublica;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ValidadorImportacaoSolicitacaoTecnicaService {

    private final ObraApi obraApi;
    private final EquipeApi equipeApi;
    private final AgenteResponsavelApi agenteResponsavelApi;
    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public PreviaImportacaoSolicitacaoTecnica validar(
            List<LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo> linhas
    ) {
        Map<String, List<LinhaNormalizada>> porChave = new LinkedHashMap<>();
        for (var linha : linhas) {
            LinhaNormalizada normalizada = normalizar(linha);
            String obra = normalizada.numeroObra == null || normalizada.numeroObra.isBlank()
                    ? "SEM_OBRA"
                    : normalizada.numeroObra.trim().toUpperCase(Locale.ROOT);
            String tipo = normalizada.tipoMovimentacao == null
                    ? "SEM_TIPO"
                    : normalizada.tipoMovimentacao.getCodigo();
            String almox = normalizada.almoxarifadoOriginal == null || normalizada.almoxarifadoOriginal.isBlank()
                    ? ""
                    : normalizada.almoxarifadoOriginal.trim().toUpperCase(Locale.ROOT);
            String equipe = normalizada.codigoEquipe == null || normalizada.codigoEquipe.isBlank()
                    ? ""
                    : normalizada.codigoEquipe.trim().toUpperCase(Locale.ROOT);
            String reserva = normalizada.numeroReserva == null || normalizada.numeroReserva.isBlank()
                    ? ""
                    : normalizada.numeroReserva.trim().toUpperCase(Locale.ROOT);
            String data = normalizada.dataPrevista == null ? "" : normalizada.dataPrevista.toString();

            StringBuilder chaveBuilder = new StringBuilder(obra);
            if (!almox.isBlank()) {
                chaveBuilder.append(" - ").append(almox);
            }
            if (!equipe.isBlank()) {
                chaveBuilder.append(" - ").append(equipe);
            }
            if (!reserva.isBlank()) {
                chaveBuilder.append(" - ").append(reserva);
            }
            chaveBuilder.append(" - ").append(tipo);
            if (!data.isBlank()) {
                chaveBuilder.append(" - ").append(data);
            }
            String chaveGrupo = chaveBuilder.toString();
            normalizada.chave = chaveGrupo;
            porChave.computeIfAbsent(chaveGrupo, ignorado -> new ArrayList<>()).add(normalizada);
        }
        List<SolicitacaoPrevista> solicitacoes = porChave.entrySet().stream()
                .map(entry -> agrupar(entry.getKey(), entry.getValue()))
                .toList();
        return new PreviaImportacaoSolicitacaoTecnica(solicitacoes);
    }

    private LinhaNormalizada normalizar(LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo linha) {
        LinhaNormalizada resultado = new LinhaNormalizada(linha.numero());
        linha.camposFormula().forEach(campo -> resultado.erro(
                campo, "FORMULA_NAO_PERMITIDA", linha.valor(campo),
                "Formulas nao sao permitidas no arquivo de importacao."));

        resultado.numeroObra = obrigatorio(linha, "Numero Obra", 30, resultado);
        if (resultado.numeroObra != null) {
            resultado.obra = obraApi.buscarPorNumero(resultado.numeroObra).orElse(null);
            if (resultado.obra == null) {
                resultado.erro("Numero Obra", "OBRA_NAO_ENCONTRADA", resultado.numeroObra,
                        "A obra informada nao foi encontrada.");
            } else if (!resultado.obra.ativa()) {
                resultado.erro("Numero Obra", "OBRA_INATIVA", resultado.numeroObra,
                        "A obra informada esta inativa.");
            } else if (!resultado.obra.processoTecnico()) {
                resultado.erro("Numero Obra", "OBRA_NAO_TECNICA", resultado.numeroObra,
                        "A obra nao pertence a um processo tecnico elegivel.");
            }
        }

        resultado.ocorrenciaOriginal = limpar(linha.valor("Ocorrencia OC OS"));
        try {
            resultado.ocorrencia = NormalizadorOcorrenciaTecnica.normalizar(resultado.ocorrenciaOriginal);
        } catch (IllegalArgumentException ex) {
            resultado.erro("Ocorrencia OC OS", "OCORRENCIA_INVALIDA", resultado.ocorrenciaOriginal,
                    ex.getMessage());
        }
        validarAplicabilidadeOcorrencia(resultado);

        resultado.almoxarifadoOriginal = obrigatorio(
                linha, "Almoxarifado Atendimento", 255, resultado);
        resultado.almoxarifado = resolverAlmoxarifado(resultado.almoxarifadoOriginal);
        if (resultado.almoxarifadoOriginal != null && resultado.almoxarifado == null) {
            resultado.erro("Almoxarifado Atendimento", "ALMOXARIFADO_INVALIDO",
                    resultado.almoxarifadoOriginal,
                    "O almoxarifado deve existir, estar ativo e ser do tipo FISICO.");
        }

        resultado.codigoEquipe = limpar(linha.valor("Codigo Equipe"));
        if (resultado.codigoEquipe != null) {
            UUID idEquipe = equipeApi.buscarIdPorCodigo(resultado.codigoEquipe).orElse(null);
            resultado.equipe = idEquipe == null ? null : equipeApi.buscarAtivaPorId(idEquipe).orElse(null);
            if (resultado.equipe == null) {
                resultado.erro("Codigo Equipe", "EQUIPE_INVALIDA", resultado.codigoEquipe,
                        "A equipe deve existir e estar ativa.");
            }
        }

        resultado.dataPrevista = dataOpcional(
                linha, "Data Prevista", "DATA_PREVISTA_INVALIDA", resultado);
        resultado.observacao = opcional(linha, "Observacao", 1000, resultado);
        resultado.tipoMovimentacao = tipoMovimentacao(linha.valor("Tipo Movimentacao"), resultado);

        resultado.codigoMaterial = obrigatorio(linha, "Codigo Material", 100, resultado);
        if (resultado.codigoMaterial != null) {
            resultado.material = materialRepository.findByCodigoIgnoreCase(resultado.codigoMaterial).orElse(null);
            if (resultado.material == null || !resultado.material.isAtivo()) {
                resultado.erro("Codigo Material", "MATERIAL_INVALIDO", resultado.codigoMaterial,
                        "O material deve existir e estar ativo.");
                resultado.material = null;
            }
        }
        resultado.quantidade = quantidade(linha.valor("Quantidade"), resultado);

        resultado.numeroReserva = limpar(linha.valor("Numero Reserva"));
        resultado.dataReserva = dataOpcional(
                linha, "Data Criacao Reserva", "DATA_RESERVA_INVALIDA", resultado);
        resultado.agenteOriginal = limpar(linha.valor("Agente Responsavel"));
        validarReserva(resultado);
        return resultado;
    }

    private SolicitacaoPrevista agrupar(String chave, List<LinhaNormalizada> linhas) {
        LinhaNormalizada primeira = linhas.getFirst();
        List<Mensagem> mensagens = new ArrayList<>();
        linhas.forEach(linha -> mensagens.addAll(linha.mensagens));
        validarConsistenciaCabecalho(chave, linhas, mensagens);

        Map<TipoSolicitacaoTecnica, List<LinhaNormalizada>> porTipo = new LinkedHashMap<>();
        linhas.stream()
                .filter(linha -> linha.material != null && linha.tipoMovimentacao != null && linha.quantidade != null)
                .forEach(linha -> {
                    TipoSolicitacaoTecnica tipo = linha.tipoMovimentacao;
                    porTipo.computeIfAbsent(tipo, ignorado -> new ArrayList<>()).add(linha);
                });

        List<Subgrupo> subgrupos = new ArrayList<>();
        porTipo.forEach((tipo, linhasTipo) -> {
            Reserva reserva = validarConsistenciaReserva(chave, tipo, linhasTipo, mensagens);
            List<Item> itens = consolidarItens(chave, tipo, linhasTipo, mensagens);
            List<Integer> linhasOrigem = linhasTipo.stream().map(linha -> linha.numero).distinct().sorted().toList();
            subgrupos.add(new Subgrupo(tipo.getCodigo(), itens, reserva, linhasOrigem));
        });
        subgrupos.sort(Comparator.comparing(Subgrupo::tipo));

        if (subgrupos.isEmpty()) {
            mensagens.add(mensagem(null, "itens", "GRUPO_SEM_ITENS_VALIDOS", Severidade.ERRO, chave,
                    "A solicitacao nao possui itens tecnicos validos."));
        }
        return new SolicitacaoPrevista(
                chave,
                primeira.obra == null ? null : primeira.obra.id(),
                primeira.obra == null ? primeira.numeroObra : primeira.obra.numero(),
                primeira.obra == null ? null : TipoObraSnapshot.valueOf(primeira.obra.tipoObra().name()),
                primeira.ocorrencia,
                primeira.almoxarifado == null ? null : primeira.almoxarifado.getId(),
                primeira.almoxarifado == null ? primeira.almoxarifadoOriginal
                        : primeira.almoxarifado.getNomeIdentificador(),
                primeira.equipe == null ? null : primeira.equipe.id(),
                primeira.equipe == null ? primeira.codigoEquipe : primeira.equipe.codigo(),
                primeira.dataPrevista,
                primeira.observacao,
                subgrupos,
                mensagens,
                null);
    }

    private void validarConsistenciaCabecalho(
            String chave,
            List<LinhaNormalizada> linhas,
            List<Mensagem> mensagens
    ) {
        LinhaNormalizada base = linhas.getFirst();
        compararGrupo(chave, "Numero Obra", linhas, linha -> id(linha.obra), mensagens);
        compararGrupo(chave, "Ocorrencia OC OS", linhas, linha -> linha.ocorrencia, mensagens);
        compararGrupo(chave, "Almoxarifado Atendimento", linhas, linha -> id(linha.almoxarifado), mensagens);
        compararGrupo(chave, "Codigo Equipe", linhas, linha -> id(linha.equipe), mensagens);
        compararGrupo(chave, "Data Prevista", linhas, linha -> linha.dataPrevista, mensagens);
        compararGrupo(chave, "Observacao", linhas, linha -> linha.observacao, mensagens);
        if (base.obra == null || base.almoxarifado == null) {
            return;
        }
    }

    private void compararGrupo(
            String chave,
            String campo,
            List<LinhaNormalizada> linhas,
            java.util.function.Function<LinhaNormalizada, Object> extrator,
            List<Mensagem> mensagens
    ) {
        Set<Object> valores = new LinkedHashSet<>();
        linhas.forEach(linha -> valores.add(extrator.apply(linha)));
        if (valores.size() > 1) {
            mensagens.add(mensagem(null, campo, "CABECALHO_DIVERGENTE", Severidade.ERRO, chave,
                    "Todas as linhas do mesmo agrupamento devem informar o mesmo valor para " + campo + "."));
        }
    }

    private Reserva validarConsistenciaReserva(
            String chave,
            TipoSolicitacaoTecnica tipo,
            List<LinhaNormalizada> linhas,
            List<Mensagem> mensagens
    ) {
        Set<ChaveReserva> reservas = linhas.stream()
                .map(linha -> new ChaveReserva(linha.numeroReserva, linha.dataReserva,
                        linha.agente == null ? null : linha.agente.id()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (reservas.size() > 1) {
            mensagens.add(mensagem(null, "reserva", "RESERVA_DIVERGENTE", Severidade.ERRO,
                    chave + ":" + tipo.getCodigo(),
                    "Os dados da reserva devem ser iguais dentro do mesmo tipo derivado."));
            return null;
        }
        LinhaNormalizada primeira = linhas.getFirst();
        if (primeira.numeroReserva == null && primeira.dataReserva == null && primeira.agente == null) {
            return null;
        }
        if (primeira.numeroReserva == null || primeira.dataReserva == null) {
            return null;
        }
        return new Reserva(primeira.numeroReserva, primeira.dataReserva,
                primeira.agente == null ? null : primeira.agente.id(),
                primeira.agente == null ? null : primeira.agente.nome());
    }

    private List<Item> consolidarItens(
            String chave,
            TipoSolicitacaoTecnica tipo,
            List<LinhaNormalizada> linhas,
            List<Mensagem> mensagens
    ) {
        List<Item> resultado = new ArrayList<>();
        Map<UUID, List<LinhaNormalizada>> naoSeriados = linhas.stream()
                .filter(linha -> !linha.material.isDetalhaSerie())
                .collect(java.util.stream.Collectors.groupingBy(
                        linha -> linha.material.getId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        naoSeriados.forEach((idMaterial, duplicados) -> {
            LinhaNormalizada primeira = duplicados.getFirst();
            BigDecimal total = duplicados.stream().map(linha -> linha.quantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Integer> origens = duplicados.stream().map(linha -> linha.numero).sorted().toList();
            resultado.add(item(primeira, total, origens));
            if (duplicados.size() > 1) {
                mensagens.add(mensagem(null, "Codigo Material", "MATERIAL_DUPLICADO_CONSOLIDADO",
                        Severidade.AVISO, primeira.codigoMaterial,
                        "O material repetido no tipo " + tipo.getCodigo()
                                + " foi consolidado nas linhas " + origens + "."));
            }
        });
        linhas.stream().filter(linha -> linha.material.isDetalhaSerie())
                .forEach(linha -> resultado.add(item(linha, linha.quantidade, List.of(linha.numero))));
        resultado.sort(Comparator.comparing(Item::codigoMaterial).thenComparing(item -> item.linhasOrigem().getFirst()));
        return resultado;
    }

    private Item item(LinhaNormalizada linha, BigDecimal quantidade, List<Integer> linhasOrigem) {
        return new Item(linha.material.getId(), linha.material.getCodigo(), linha.material.getDescricao(),
                quantidade.setScale(Math.max(0, Math.min(3, quantidade.scale())), RoundingMode.UNNECESSARY),
                linha.material.isDetalhaSerie(), linhasOrigem);
    }

    private void validarAplicabilidadeOcorrencia(LinhaNormalizada linha) {
        if (linha.obra == null) {
            return;
        }
        boolean obrigatoria = linha.obra.tipoObra() == TipoObraPublico.CORRETIVA
                || linha.obra.tipoObra() == TipoObraPublico.PREVENTIVA;
        if (obrigatoria && linha.ocorrencia == null) {
            linha.erro("Ocorrencia OC OS", "OCORRENCIA_OBRIGATORIA", linha.ocorrenciaOriginal,
                    "A ocorrencia e obrigatoria para obra corretiva ou preventiva.");
        } else if (!obrigatoria && linha.ocorrencia != null) {
            linha.erro("Ocorrencia OC OS", "OCORRENCIA_NAO_APLICAVEL", linha.ocorrenciaOriginal,
                    "A ocorrencia deve ficar vazia para obra OBRA ou LPT.");
        }
    }

    private Estoque resolverAlmoxarifado(String identificador) {
        if (identificador == null) {
            return null;
        }
        Estoque estoque = uuid(identificador) == null
                ? estoqueRepository.findByNomeIdentificadorIgnoreCase(identificador).orElse(null)
                : estoqueRepository.findById(uuid(identificador)).orElse(null);
        if (estoque == null || !estoque.isAtivo() || estoque.getTipoEstoque() == null
                || !"FISICO".equalsIgnoreCase(estoque.getTipoEstoque().getNome())) {
            return null;
        }
        return estoque;
    }

    private void validarReserva(LinhaNormalizada linha) {
        boolean algum = linha.numeroReserva != null || linha.dataReserva != null || linha.agenteOriginal != null;
        if (!algum) {
            return;
        }
        if (linha.numeroReserva == null || !linha.numeroReserva.matches("[0-9]{1,20}")) {
            linha.erro("Numero Reserva", "NUMERO_RESERVA_INVALIDO", linha.numeroReserva,
                    "O numero da reserva deve conter de 1 a 20 digitos.");
        }
        if (linha.dataReserva == null) {
            linha.erro("Data Criacao Reserva", "RESERVA_INCOMPLETA", null,
                    "A data real de criacao e obrigatoria quando a reserva e informada.");
        }
        if (linha.agenteOriginal != null) {
            UUID idAgente = uuid(linha.agenteOriginal);
            if (idAgente == null) {
                linha.erro("Agente Responsavel", "AGENTE_INVALIDO", linha.agenteOriginal,
                        "Informe o UUID de um agente responsavel ativo.");
                return;
            }
            linha.agente = agenteResponsavelApi.buscarAtivoPorId(idAgente).orElse(null);
            if (linha.agente == null) {
                linha.erro("Agente Responsavel", "AGENTE_INVALIDO", linha.agenteOriginal,
                        "O agente responsavel deve existir e estar ativo.");
            }
        }
    }

    private TipoSolicitacaoTecnica tipoMovimentacao(String valor, LinhaNormalizada linha) {
        String texto = limpar(valor);
        if (texto == null) {
            linha.erro("Tipo Movimentacao", "TIPO_MOVIMENTACAO_OBRIGATORIO", valor,
                    "O tipo deve ser 221, 222, 921 ou 922.");
            return null;
        }
        try {
            return TipoSolicitacaoTecnica.porCodigo(texto);
        } catch (IllegalArgumentException ex) {
            linha.erro("Tipo Movimentacao", "TIPO_MOVIMENTACAO_INVALIDO", valor,
                    "O tipo deve ser 221, 222, 921 ou 922.");
            return null;
        }
    }

    private BigDecimal quantidade(String valor, LinhaNormalizada linha) {
        String texto = limpar(valor);
        if (texto == null) {
            linha.erro("Quantidade", "QUANTIDADE_OBRIGATORIA", valor, "A quantidade e obrigatoria.");
            return null;
        }
        try {
            String normalizado = texto.contains(",") && !texto.contains(".")
                    ? texto.replace(',', '.') : texto;
            BigDecimal quantidade = new BigDecimal(normalizado).stripTrailingZeros();
            int escala = Math.max(0, quantidade.scale());
            if (quantidade.signum() <= 0 || escala > 3 || quantidade.precision() > 12) {
                throw new NumberFormatException();
            }
            return quantidade;
        } catch (NumberFormatException ex) {
            linha.erro("Quantidade", "QUANTIDADE_INVALIDA", valor,
                    "A quantidade deve ser positiva, ter ate 12 digitos e no maximo 3 casas decimais.");
            return null;
        }
    }

    private LocalDate dataOpcional(
            LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo linha,
            String campo,
            String codigo,
            LinhaNormalizada resultado
    ) {
        String valor = limpar(linha.valor(campo));
        if (valor == null) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ex) {
            resultado.erro(campo, codigo, valor, "A data deve usar o formato AAAA-MM-DD.");
            return null;
        }
    }

    private String obrigatorio(
            LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo linha,
            String campo,
            int limite,
            LinhaNormalizada resultado
    ) {
        String valor = opcional(linha, campo, limite, resultado);
        if (valor == null) {
            resultado.erro(campo, "CAMPO_OBRIGATORIO", linha.valor(campo), "O campo e obrigatorio.");
        }
        return valor;
    }

    private String opcional(
            LeitorArquivoImportacaoSolicitacaoTecnica.LinhaArquivo linha,
            String campo,
            int limite,
            LinhaNormalizada resultado
    ) {
        String valor = limpar(linha.valor(campo));
        if (valor != null && valor.length() > limite) {
            resultado.erro(campo, "LIMITE_EXCEDIDO", valor,
                    "O campo excede o limite de " + limite + " caracteres.");
            return valor.substring(0, limite);
        }
        return valor;
    }

    private Object id(Object objeto) {
        if (objeto instanceof ObraPublica obra) return obra.id();
        if (objeto instanceof Estoque estoque) return estoque.getId();
        if (objeto instanceof EquipePublica equipe) return equipe.id();
        return null;
    }

    private UUID uuid(String valor) {
        try {
            return valor == null ? null : UUID.fromString(valor.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Mensagem mensagem(
            Integer linha,
            String campo,
            String codigo,
            Severidade severidade,
            String valor,
            String texto
    ) {
        String seguro = valor == null ? null : valor.substring(0, Math.min(200, valor.length()));
        return new Mensagem(linha, campo, codigo, severidade, seguro, texto);
    }

    private final class LinhaNormalizada {
        private final int numero;
        private final List<Mensagem> mensagens = new ArrayList<>();
        private String chave;
        private String numeroObra;
        private ObraPublica obra;
        private String ocorrenciaOriginal;
        private String ocorrencia;
        private String almoxarifadoOriginal;
        private Estoque almoxarifado;
        private String codigoEquipe;
        private EquipePublica equipe;
        private LocalDate dataPrevista;
        private String observacao;
        private TipoSolicitacaoTecnica tipoMovimentacao;
        private String codigoMaterial;
        private Material material;
        private BigDecimal quantidade;
        private String numeroReserva;
        private LocalDate dataReserva;
        private String agenteOriginal;
        private AgenteResponsavelPublico agente;

        private LinhaNormalizada(int numero) {
            this.numero = numero;
        }

        private void erro(String campo, String codigo, String valor, String texto) {
            mensagens.add(mensagem(numero, campo, codigo, Severidade.ERRO, valor, texto));
        }
    }

    private record ChaveReserva(String numero, LocalDate data, UUID idAgente) {
    }
}
