package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.CampoValorResponse;
import com.nexo.app.modules.financeiro.application.dto.DesfechoResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FornecedorSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoResponse;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Exportação tabular das solicitações financeiras, com o mesmo recorte das listagens. */
@Service
@RequiredArgsConstructor
public class ExportarSolicitacoesFinanceirasUseCase {

    static final int MAX_REGISTROS = 10_000;
    static final int TAMANHO_PAGINA = 500;

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Sort ORDENACAO = Sort.by(
            Sort.Order.desc("dataCriacao"),
            Sort.Order.desc("id"));

    private static final List<String> CABECALHO_RESUMO = List.of(
            "ID Solicitação", "Número", "Status", "Formulário", "Usuário Solicitante",
            "Colaborador Solicitante", "Fornecedor", "Valor Solicitado", "Valor Aprovado",
            "Data Criação", "Data Submissão", "Data Decisão", "Ativo");

    private static final List<String> CABECALHO_COMPRA_VINCULADA_RESUMO = List.of(
            "Compra Vinculada ID", "Compra Vinculada Número");

    private static final List<String> CABECALHO_IDENTIFICACAO_DETALHADA = List.of(
            "Formulário ID", "Formulário Código", "Formulário Descrição",
            "Formulário Padrão", "Formulário Ativo", "Fornecedor ID",
            "Fornecedor Razão Social", "Fornecedor Cadastrado");

    private static final List<String> CABECALHO_DESFECHO_DETALHADO = List.of(
            "Usuário Decisão ID", "Usuário Decisão", "Observação Decisão", "Motivo Rejeição",
            "Usuário Cancelamento ID", "Data Cancelamento", "Motivo Cancelamento",
            "Quantidade Documentos", "Documentos", "Data Última Alteração", "Usuário Última Alteração");

    private static final List<String> CABECALHO_DECISOES_DESFEITAS = List.of(
            "Decisões Desfeitas", "Última Decisão Desfeita Em", "Última Decisão Desfeita Por",
            "Motivo Última Decisão Desfeita");

    private static final List<String> CABECALHO_COMPRA_VINCULADA_DETALHADO = List.of(
            "Compra Vinculada Status", "Compra Vinculada Valor Aprovado",
            "Compra Vinculada Fornecedor", "Compra Vinculada Descrição");

    private final SolicitacaoCompraUseCases compraUseCases;
    private final SolicitacaoPagamentoUseCases pagamentoUseCases;
    private final HistoricoSolicitacaoFinanceira historico;

    @Transactional(readOnly = true)
    public void exportarComprasResumo(SolicitacaoCompraFilter filtro, Writer writer) throws IOException {
        exportar(writer, CABECALHO_RESUMO,
                pageable -> compraUseCases.listar(filtro, pageable),
                pagina -> { },
                this::linhaCompraResumo);
    }

    @Transactional(readOnly = true)
    public void exportarComprasDetalhado(SolicitacaoCompraFilter filtro, Writer writer) throws IOException {
        Map<UUID, HistoricoSolicitacaoFinanceira.DecisoesDesfeitas> desfeitas = new HashMap<>();
        exportar(writer, cabecalhoDetalhado(TipoSolicitacao.COMPRA, false),
                pageable -> compraUseCases.listar(filtro, pageable),
                pagina -> carregarDecisoesDesfeitas(pagina, SolicitacaoCompraResponse::id, desfeitas),
                compra -> linhaCompraDetalhada(compra, desfeitas.get(compra.id())));
    }

    @Transactional(readOnly = true)
    public void exportarPagamentosResumo(SolicitacaoPagamentoFilter filtro, Writer writer) throws IOException {
        exportar(writer, cabecalhoPagamentoResumo(),
                pageable -> pagamentoUseCases.listar(filtro, pageable),
                pagina -> { },
                this::linhaPagamentoResumo);
    }

    @Transactional(readOnly = true)
    public void exportarPagamentosDetalhado(SolicitacaoPagamentoFilter filtro, Writer writer) throws IOException {
        Map<UUID, HistoricoSolicitacaoFinanceira.DecisoesDesfeitas> desfeitas = new HashMap<>();
        exportar(writer, cabecalhoDetalhado(TipoSolicitacao.PAGAMENTO, true),
                pageable -> pagamentoUseCases.listar(filtro, pageable),
                pagina -> carregarDecisoesDesfeitas(pagina, SolicitacaoPagamentoResponse::id, desfeitas),
                pagamento -> linhaPagamentoDetalhada(pagamento, desfeitas.get(pagamento.id())));
    }

    private <T> void exportar(Writer writer, List<String> cabecalho,
                              Function<Pageable, PageResponse<T>> buscador,
                              Consumer<List<T>> preparador,
                              LinhaCsv<T> montadorLinha) throws IOException {
        CSVFormat formato = CSVFormat.Builder.create()
                .setHeader(cabecalho.toArray(String[]::new))
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, formato)) {
            processarPaginas(buscador, preparador, item -> printer.printRecord(montadorLinha.montar(item)));
        }
    }

    private <T> void processarPaginas(Function<Pageable, PageResponse<T>> buscador,
                                      Consumer<List<T>> preparador,
                                      ConsumidorComIo<T> consumidor) throws IOException {
        int processados = 0;
        int numeroPagina = 0;

        while (processados < MAX_REGISTROS) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, ORDENACAO);
            PageResponse<T> pagina = buscador.apply(pageable);
            List<T> conteudo = pagina == null || pagina.getContent() == null
                    ? List.of()
                    : pagina.getContent();

            if (conteudo.isEmpty()) {
                break;
            }
            preparador.accept(conteudo);

            for (T item : conteudo) {
                if (processados >= MAX_REGISTROS) {
                    break;
                }
                consumidor.aceitar(item);
                processados++;
            }

            if (pagina.getPage() == null || numeroPagina >= pagina.getPage().getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private List<Object> linhaCompraResumo(SolicitacaoCompraResponse compra) {
        return linhaResumo(compra.id(), compra.numero(), compra.status(), compra.formulario(),
                compra.usuarioSolicitante(), compra.nomeColaboradorSolicitante(), compra.fornecedor(),
                compra.valor(), compra.desfecho(), compra.dataCriacao(), compra.dataSubmissao(), compra.ativo());
    }

    private List<Object> linhaPagamentoResumo(SolicitacaoPagamentoResponse pagamento) {
        List<Object> linha = new ArrayList<>(linhaResumo(
                pagamento.id(), pagamento.numero(), pagamento.status(), pagamento.formulario(),
                pagamento.usuarioSolicitante(), pagamento.nomeColaboradorSolicitante(), pagamento.fornecedor(),
                pagamento.valor(), pagamento.desfecho(), pagamento.dataCriacao(),
                pagamento.dataSubmissao(), pagamento.ativo()));
        SolicitacaoCompraResumoResponse compra = pagamento.compraVinculada();
        linha.add(compra == null ? "" : compra.id());
        linha.add(compra == null ? "" : compra.numero());
        return moverCompraVinculadaAntesDasDatas(linha);
    }

    private List<Object> linhaCompraDetalhada(SolicitacaoCompraResponse compra, HistoricoSolicitacaoFinanceira.DecisoesDesfeitas desfeitas) {
        List<Object> linha = linhaCompraResumo(compra);
        adicionarIdentificacaoDetalhada(linha, compra.formulario(), compra.fornecedor());
        adicionarCamposCanonicos(linha, compra.valores(), TipoSolicitacao.COMPRA);
        adicionarDesfechoEDocumentos(linha, compra.desfecho(), compra.documentos(),
                compra.dataUltimaAlteracao(), compra.usuarioUltimaAlteracao());
        adicionarDecisoesDesfeitas(linha, desfeitas);
        return linha;
    }

    private List<Object> linhaPagamentoDetalhada(SolicitacaoPagamentoResponse pagamento, HistoricoSolicitacaoFinanceira.DecisoesDesfeitas desfeitas) {
        List<Object> linha = linhaPagamentoResumo(pagamento);
        adicionarIdentificacaoDetalhada(linha, pagamento.formulario(), pagamento.fornecedor());
        adicionarCamposCanonicos(linha, pagamento.valores(), TipoSolicitacao.PAGAMENTO);
        adicionarDesfechoEDocumentos(linha, pagamento.desfecho(), pagamento.documentos(),
                pagamento.dataUltimaAlteracao(), pagamento.usuarioUltimaAlteracao());
        adicionarDecisoesDesfeitas(linha, desfeitas);

        SolicitacaoCompraResumoResponse compra = pagamento.compraVinculada();
        linha.add(compra == null ? "" : compra.status());
        linha.add(compra == null ? "" : compra.valorAprovado());
        linha.add(compra == null ? "" : texto(compra.fornecedor()));
        linha.add(compra == null ? "" : texto(compra.descricao()));
        return linha;
    }

    private List<Object> linhaResumo(Object id, Object numero, Object status,
                                     FormularioResumoResponse formulario, String usuarioSolicitante,
                                     String colaboradorSolicitante, FornecedorSolicitacaoResponse fornecedor,
                                     Object valor, DesfechoResponse desfecho, OffsetDateTime dataCriacao,
                                     OffsetDateTime dataSubmissao, boolean ativo) {
        List<Object> linha = new ArrayList<>();
        linha.add(valorOuVazio(id));
        linha.add(valorOuVazio(numero));
        linha.add(valorOuVazio(status));
        linha.add(formulario == null ? "" : texto(formulario.titulo()));
        linha.add(texto(usuarioSolicitante));
        linha.add(texto(colaboradorSolicitante));
        linha.add(fornecedor == null ? "" : texto(fornecedor.nome()));
        linha.add(valorOuVazio(valor));
        linha.add(desfecho == null ? "" : valorOuVazio(desfecho.valorAprovado()));
        linha.add(formatar(dataCriacao));
        linha.add(formatar(dataSubmissao));
        linha.add(desfecho == null ? "" : formatar(desfecho.dataDecisao()));
        linha.add(ativo ? "Sim" : "Não");
        return linha;
    }

    /** Insere as duas colunas específicas do pagamento antes das datas do resumo. */
    private List<Object> moverCompraVinculadaAntesDasDatas(List<Object> linha) {
        Object compraId = linha.remove(linha.size() - 2);
        Object compraNumero = linha.remove(linha.size() - 1);
        linha.add(9, compraId);
        linha.add(10, compraNumero);
        return linha;
    }

    private void adicionarIdentificacaoDetalhada(List<Object> linha, FormularioResumoResponse formulario,
                                                 FornecedorSolicitacaoResponse fornecedor) {
        linha.add(formulario == null ? "" : formulario.id());
        linha.add(formulario == null ? "" : texto(formulario.codigo()));
        linha.add(formulario == null ? "" : texto(formulario.descricao()));
        linha.add(formulario == null ? "" : simNao(formulario.padrao()));
        linha.add(formulario == null ? "" : simNao(formulario.ativo()));
        linha.add(fornecedor == null ? "" : fornecedor.id());
        linha.add(fornecedor == null ? "" : texto(fornecedor.razaoSocial()));
        linha.add(fornecedor == null ? "" : simNao(fornecedor.cadastrado()));
    }

    private void adicionarCamposCanonicos(List<Object> linha, Map<String, CampoValorResponse> valores,
                                          TipoSolicitacao tipo) {
        Map<String, CampoValorResponse> valoresSeguros = valores == null ? Map.of() : valores;
        camposAplicaveis(tipo).forEach(campo -> {
            CampoValorResponse valor = valoresSeguros.get(campo.getChave());
            linha.add(valor == null ? "" : texto(valor.exibicao()));
        });
    }

    private void adicionarDesfechoEDocumentos(List<Object> linha, DesfechoResponse desfecho,
                                              List<DocumentoResumoResponse> documentos,
                                              OffsetDateTime dataUltimaAlteracao,
                                              String usuarioUltimaAlteracao) {
        linha.add(desfecho == null ? "" : valorOuVazio(desfecho.usuarioDecisaoId()));
        linha.add(desfecho == null ? "" : texto(desfecho.usuarioDecisao()));
        linha.add(desfecho == null ? "" : texto(desfecho.observacaoDecisao()));
        linha.add(desfecho == null ? "" : texto(desfecho.motivoRejeicao()));
        linha.add(desfecho == null ? "" : valorOuVazio(desfecho.usuarioCancelamentoId()));
        linha.add(desfecho == null ? "" : formatar(desfecho.dataCancelamento()));
        linha.add(desfecho == null ? "" : texto(desfecho.motivoCancelamento()));

        List<DocumentoResumoResponse> documentosSeguros = documentos == null ? List.of() : documentos;
        linha.add(documentosSeguros.size());
        linha.add(texto(resumirDocumentos(documentosSeguros)));
        linha.add(formatar(dataUltimaAlteracao));
        linha.add(texto(usuarioUltimaAlteracao));
    }

    /**
     * O status mostra só a decisão vigente; sem estas colunas, uma aprovação desfeita
     * e refeita seria indistinguível de uma decisão tomada uma vez só.
     */
    private void adicionarDecisoesDesfeitas(List<Object> linha, HistoricoSolicitacaoFinanceira.DecisoesDesfeitas desfeitas) {
        linha.add(desfeitas == null ? 0 : desfeitas.quantidade());
        linha.add(desfeitas == null ? "" : formatar(desfeitas.ultimaEm()));
        linha.add(desfeitas == null ? "" : texto(desfeitas.ultimaPor()));
        linha.add(desfeitas == null ? "" : texto(desfeitas.ultimoMotivo()));
    }

    private <T> void carregarDecisoesDesfeitas(List<T> pagina, Function<T, UUID> id,
                                               Map<UUID, HistoricoSolicitacaoFinanceira.DecisoesDesfeitas> destino) {
        destino.clear();
        destino.putAll(historico.decisoesDesfeitas(pagina.stream().map(id).toList()));
    }

    private String resumirDocumentos(List<DocumentoResumoResponse> documentos) {
        return documentos.stream()
                .sorted(Comparator.comparing(DocumentoResumoResponse::dataCriacao,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DocumentoResumoResponse::id,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::resumirDocumento)
                .reduce((primeiro, seguinte) -> primeiro + " || " + seguinte)
                .orElse("");
    }

    private String resumirDocumento(DocumentoResumoResponse documento) {
        return String.join(" | ",
                atributo("nome", documento.nomeArquivo()),
                atributo("tipo", documento.tipoRotulo() != null ? documento.tipoRotulo() : documento.tipo()),
                atributo("situação", documento.situacaoRotulo() != null
                        ? documento.situacaoRotulo() : documento.situacao()),
                atributo("número", documento.numeroDocumento()),
                atributo("valor", documento.valor()),
                atributo("duplicado", simNao(documento.duplicado())),
                atributo("pendente", simNao(documento.identificacaoPendente())),
                atributo("conferidoPor", documento.usuarioConferenciaId()),
                atributo("dataConferência", formatar(documento.dataConferencia())),
                atributo("observaçãoConferência", documento.observacaoConferencia()),
                atributo("motivoPendência", documento.motivoPendencia()),
                atributo("data", formatar(documento.dataCriacao())));
    }

    private String atributo(String nome, Object valor) {
        return nome + "=" + (valor == null ? "" : valor);
    }

    private List<String> cabecalhoPagamentoResumo() {
        List<String> cabecalho = new ArrayList<>(CABECALHO_RESUMO);
        cabecalho.addAll(9, CABECALHO_COMPRA_VINCULADA_RESUMO);
        return cabecalho;
    }

    private List<String> cabecalhoDetalhado(TipoSolicitacao tipo, boolean pagamento) {
        List<String> cabecalho = pagamento
                ? new ArrayList<>(cabecalhoPagamentoResumo())
                : new ArrayList<>(CABECALHO_RESUMO);
        cabecalho.addAll(CABECALHO_IDENTIFICACAO_DETALHADA);
        camposAplicaveis(tipo).forEach(campo -> cabecalho.add("Campo - " + campo.getRotuloPadrao()));
        cabecalho.addAll(CABECALHO_DESFECHO_DETALHADO);
        cabecalho.addAll(CABECALHO_DECISOES_DESFEITAS);
        if (pagamento) {
            cabecalho.addAll(CABECALHO_COMPRA_VINCULADA_DETALHADO);
        }
        return cabecalho;
    }

    private List<CampoCanonico> camposAplicaveis(TipoSolicitacao tipo) {
        return java.util.Arrays.stream(CampoCanonico.values())
                .filter(campo -> campo.aplicaSeA(tipo))
                .toList();
    }

    private Object valorOuVazio(Object valor) {
        return valor == null ? "" : valor;
    }

    private String formatar(OffsetDateTime valor) {
        return valor == null ? "" : valor.format(DATA_HORA);
    }

    private String simNao(boolean valor) {
        return valor ? "Sim" : "Não";
    }

    /** Neutraliza fórmulas somente em textos; números tipados permanecem números. */
    private String texto(String valor) {
        if (valor == null) {
            return "";
        }
        String semEspacosIniciais = valor.stripLeading();
        if (!semEspacosIniciais.isEmpty() && "=+-@".indexOf(semEspacosIniciais.charAt(0)) >= 0) {
            return "'" + valor;
        }
        return valor;
    }

    @FunctionalInterface
    private interface LinhaCsv<T> {
        List<Object> montar(T item);
    }

    @FunctionalInterface
    private interface ConsumidorComIo<T> {
        void aceitar(T item) throws IOException;
    }
}
