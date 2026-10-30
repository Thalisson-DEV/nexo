package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.EtapaOperacionalSolicitacao;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoSpecification;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeApi.EquipePublica;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Caso de uso responsável pela exportação tabular (CSV / ZIP) de solicitações de material.
 * Suporta layouts dedicados por processo (Comercial e Técnico), layout gerencial canônico
 * consolidado (sem colunas vazias) e geração de pacote ZIP com arquivos separados.
 */
@Service
@RequiredArgsConstructor
public class ExportarSolicitacoesMaterialUseCase {

    private static final int TAMANHO_PAGINA = 500;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Sort ORDENACAO = Sort.by(
            Sort.Order.desc("dataSolicitacao"),
            Sort.Order.desc("id"));

    // 1. LAYOUT RESUMO COMERCIAL (100% específico do fluxo comercial)
    private static final List<String> CABECALHO_RESUMO_COMERCIAL = List.of(
            "ID Solicitação",
            "Contexto",
            "Status",
            "Data Solicitação",
            "Data Aprovação",
            "Data Entrega",
            "Estoque Origem",
            "Estoque Destino",
            "Requisitante",
            "Aprovador",
            "Entregador Almox",
            "Motivo Rejeição",
            "Justificativa Rejeição",
            "Observações",
            "Qtd Itens",
            "Qtd Total Solicitada",
            "Qtd Total Entregue Líquida",
            "Qtd Total Devolvida Líquida",
            "Qtd Total Pendente"
    );

    // 2. LAYOUT DETALHADO COMERCIAL
    private static final List<String> CABECALHO_DETALHADO_COMERCIAL = List.of(
            "ID Solicitação",
            "Contexto",
            "Status Solicitação",
            "Data Solicitação",
            "Data Aprovação",
            "Data Entrega",
            "Estoque Origem",
            "Estoque Destino",
            "Requisitante",
            "Aprovador",
            "Entregador Almox",
            "Motivo Rejeição",
            "Justificativa Rejeição",
            "Observações",
            "ID Item",
            "Código Material",
            "Descrição Material",
            "Unidade Medida",
            "Detalha Série",
            "Número de Série",
            "Qtd Solicitada",
            "Qtd Entregue Bruta",
            "Qtd Entrega Estornada",
            "Qtd Entregue Líquida",
            "Qtd Devolvida Bruta",
            "Qtd Devolução Estornada",
            "Qtd Devolvida Líquida",
            "Qtd Pendente",
            "Status Item"
    );

    // 3. LAYOUT RESUMO TÉCNICO (100% específico do fluxo de obras e campo)
    private static final List<String> CABECALHO_RESUMO_TECNICO = List.of(
            "ID Solicitação",
            "Contexto",
            "Status",
            "Tipo Técnico",
            "Etapa Operacional",
            "Status Separação",
            "Data Solicitação",
            "Data Prevista Retirada",
            "Data Aprovação",
            "Data Entrega",
            "Almoxarifado Atendimento",
            "Número Obra",
            "Título Obra",
            "Tipo Obra",
            "Ocorrência",
            "Código Equipe Saque",
            "Colaborador Saque",
            "Saque Fora da Equipe",
            "Número Reserva",
            "Agente Responsável Reserva",
            "Empresa Agente Reserva",
            "Aprovador",
            "Entregador Almox",
            "Motivo Rejeição",
            "Justificativa Rejeição",
            "Encerrada Em",
            "Motivo Encerramento",
            "Observações",
            "Qtd Itens",
            "Qtd Total Solicitada",
            "Qtd Total Entregue Líquida",
            "Qtd Total Devolvida Líquida",
            "Qtd Total Pendente"
    );

    // 4. LAYOUT DETALHADO TÉCNICO
    private static final List<String> CABECALHO_DETALHADO_TECNICO = List.of(
            "ID Solicitação",
            "Contexto",
            "Status Solicitação",
            "Tipo Técnico",
            "Etapa Operacional",
            "Status Separação",
            "Data Solicitação",
            "Data Prevista Retirada",
            "Data Aprovação",
            "Data Entrega",
            "Almoxarifado Atendimento",
            "Número Obra",
            "Título Obra",
            "Tipo Obra",
            "Ocorrência",
            "Código Equipe Saque",
            "Colaborador Saque",
            "Saque Fora da Equipe",
            "Número Reserva",
            "Agente Responsável Reserva",
            "Empresa Agente Reserva",
            "Aprovador",
            "Entregador Almox",
            "Motivo Rejeição",
            "Justificativa Rejeição",
            "Encerrada Em",
            "Motivo Encerramento",
            "Observações",
            "ID Item",
            "Código Material",
            "Descrição Material",
            "Unidade Medida",
            "Detalha Série",
            "Número de Série",
            "Qtd Solicitada",
            "Qtd Entregue Bruta",
            "Qtd Entrega Estornada",
            "Qtd Entregue Líquida",
            "Qtd Devolvida Bruta",
            "Qtd Devolução Estornada",
            "Qtd Devolvida Líquida",
            "Qtd Pendente",
            "Status Item"
    );

    // 5. LAYOUT RESUMO CANÔNICO CONSOLIDADO (Visão Geral sem colunas vazias)
    private static final List<String> CABECALHO_RESUMO_CANONICO = List.of(
            "ID Solicitação",
            "Processo",
            "Status",
            "Tipo Operação",
            "Data Solicitação",
            "Data Atendimento",
            "Origem",
            "Destino / Aplicação",
            "Solicitante / Equipe",
            "Documento / Referência",
            "Observações",
            "Qtd Itens",
            "Qtd Total Solicitada",
            "Qtd Total Entregue Líquida",
            "Qtd Total Devolvida Líquida",
            "Qtd Total Pendente"
    );

    // 6. LAYOUT DETALHADO CANÔNICO CONSOLIDADO
    private static final List<String> CABECALHO_DETALHADO_CANONICO = List.of(
            "ID Solicitação",
            "Processo",
            "Status Solicitação",
            "Tipo Operação",
            "Data Solicitação",
            "Data Atendimento",
            "Origem",
            "Destino / Aplicação",
            "Solicitante / Equipe",
            "Documento / Referência",
            "Observações",
            "ID Item",
            "Código Material",
            "Descrição Material",
            "Unidade Medida",
            "Detalha Série",
            "Número de Série",
            "Qtd Solicitada",
            "Qtd Entregue Líquida",
            "Qtd Devolvida Líquida",
            "Qtd Pendente",
            "Status Item"
    );

    private final SolicitacaoMaterialRepository repository;
    private final Optional<UsuarioApi> usuarioApi;
    private final Optional<ObraApi> obraApi;
    private final Optional<EquipeApi> equipeApi;
    private final Optional<ColaboradorApi> colaboradorApi;

    @Transactional(readOnly = true)
    public void exportarResumo(SolicitacaoFilter filter, UUID idUsuarioLogado, Boolean apenasMinhas, Writer writer) throws IOException {
        SolicitacaoFilter filtroEfetivo = aplicarApenasMinhas(filter, idUsuarioLogado, apenasMinhas);
        ContextoLookup contexto = new ContextoLookup();

        ContextoSolicitacaoMaterial ctx = filtroEfetivo.contexto();
        List<String> cabecalho;
        if (ctx == ContextoSolicitacaoMaterial.COMERCIAL) {
            cabecalho = CABECALHO_RESUMO_COMERCIAL;
        } else if (ctx == ContextoSolicitacaoMaterial.TECNICO) {
            cabecalho = CABECALHO_RESUMO_TECNICO;
        } else {
            cabecalho = CABECALHO_RESUMO_CANONICO;
        }

        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader(cabecalho.toArray(String[]::new))
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginas(filtroEfetivo, (printerRef, solicitacao) -> {
                if (ctx == ContextoSolicitacaoMaterial.COMERCIAL) {
                    printerRef.printRecord(montarLinhaResumoComercial(solicitacao, contexto));
                } else if (ctx == ContextoSolicitacaoMaterial.TECNICO) {
                    printerRef.printRecord(montarLinhaResumoTecnico(solicitacao, contexto));
                } else {
                    printerRef.printRecord(montarLinhaResumoCanonica(solicitacao, contexto));
                }
            }, printer);
        }
    }

    @Transactional(readOnly = true)
    public void exportarDetalhado(SolicitacaoFilter filter, UUID idUsuarioLogado, Boolean apenasMinhas, Writer writer) throws IOException {
        SolicitacaoFilter filtroEfetivo = aplicarApenasMinhas(filter, idUsuarioLogado, apenasMinhas);
        ContextoLookup contexto = new ContextoLookup();

        ContextoSolicitacaoMaterial ctx = filtroEfetivo.contexto();
        List<String> cabecalho;
        if (ctx == ContextoSolicitacaoMaterial.COMERCIAL) {
            cabecalho = CABECALHO_DETALHADO_COMERCIAL;
        } else if (ctx == ContextoSolicitacaoMaterial.TECNICO) {
            cabecalho = CABECALHO_DETALHADO_TECNICO;
        } else {
            cabecalho = CABECALHO_DETALHADO_CANONICO;
        }

        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader(cabecalho.toArray(String[]::new))
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginas(filtroEfetivo, (printerRef, solicitacao) -> {
                List<ItemSolicitacao> itens = solicitacao.getItens();
                if (itens == null || itens.isEmpty()) {
                    if (ctx == ContextoSolicitacaoMaterial.COMERCIAL) {
                        printerRef.printRecord(montarLinhaDetalhadaComercialSemItem(solicitacao, contexto));
                    } else if (ctx == ContextoSolicitacaoMaterial.TECNICO) {
                        printerRef.printRecord(montarLinhaDetalhadaTecnicoSemItem(solicitacao, contexto));
                    } else {
                        printerRef.printRecord(montarLinhaDetalhadaCanonicaSemItem(solicitacao, contexto));
                    }
                } else {
                    for (ItemSolicitacao item : itens) {
                        if (ctx == ContextoSolicitacaoMaterial.COMERCIAL) {
                            printerRef.printRecord(montarLinhaDetalhadaComercialComItem(solicitacao, item, contexto));
                        } else if (ctx == ContextoSolicitacaoMaterial.TECNICO) {
                            printerRef.printRecord(montarLinhaDetalhadaTecnicoComItem(solicitacao, item, contexto));
                        } else {
                            printerRef.printRecord(montarLinhaDetalhadaCanonicaComItem(solicitacao, item, contexto));
                        }
                    }
                }
            }, printer);
        }
    }

    private Writer criarWriterNaoFechavel(ZipOutputStream zipOut) {
        OutputStreamWriter osw = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
        return new FilterWriter(osw) {
            @Override
            public void close() throws IOException {
                osw.flush();
            }
        };
    }

    @Transactional(readOnly = true)
    public void exportarZipResumo(SolicitacaoFilter filter, UUID idUsuarioLogado, Boolean apenasMinhas, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            // 1. Entrada Comercial
            ZipEntry entryCom = new ZipEntry("solicitacoes_comerciais_resumo.csv");
            zipOut.putNextEntry(entryCom);
            Writer writerCom = criarWriterNaoFechavel(zipOut);
            writerCom.write('\uFEFF');
            SolicitacaoFilter filtroCom = filter != null ? filter.comContexto(ContextoSolicitacaoMaterial.COMERCIAL) : new SolicitacaoFilter(null, null, null, null, null, null, null, ContextoSolicitacaoMaterial.COMERCIAL, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            exportarResumo(filtroCom, idUsuarioLogado, apenasMinhas, writerCom);
            writerCom.flush();
            zipOut.closeEntry();

            // 2. Entrada Técnica
            ZipEntry entryTec = new ZipEntry("solicitacoes_tecnicas_resumo.csv");
            zipOut.putNextEntry(entryTec);
            Writer writerTec = criarWriterNaoFechavel(zipOut);
            writerTec.write('\uFEFF');
            SolicitacaoFilter filtroTec = filter != null ? filter.comContexto(ContextoSolicitacaoMaterial.TECNICO) : new SolicitacaoFilter(null, null, null, null, null, null, null, ContextoSolicitacaoMaterial.TECNICO, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            exportarResumo(filtroTec, idUsuarioLogado, apenasMinhas, writerTec);
            writerTec.flush();
            zipOut.closeEntry();

            zipOut.finish();
        }
    }

    @Transactional(readOnly = true)
    public void exportarZipDetalhado(SolicitacaoFilter filter, UUID idUsuarioLogado, Boolean apenasMinhas, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            // 1. Entrada Comercial
            ZipEntry entryCom = new ZipEntry("solicitacoes_comerciais_detalhado.csv");
            zipOut.putNextEntry(entryCom);
            Writer writerCom = criarWriterNaoFechavel(zipOut);
            writerCom.write('\uFEFF');
            SolicitacaoFilter filtroCom = filter != null ? filter.comContexto(ContextoSolicitacaoMaterial.COMERCIAL) : new SolicitacaoFilter(null, null, null, null, null, null, null, ContextoSolicitacaoMaterial.COMERCIAL, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            exportarDetalhado(filtroCom, idUsuarioLogado, apenasMinhas, writerCom);
            writerCom.flush();
            zipOut.closeEntry();

            // 2. Entrada Técnica
            ZipEntry entryTec = new ZipEntry("solicitacoes_tecnicas_detalhado.csv");
            zipOut.putNextEntry(entryTec);
            Writer writerTec = criarWriterNaoFechavel(zipOut);
            writerTec.write('\uFEFF');
            SolicitacaoFilter filtroTec = filter != null ? filter.comContexto(ContextoSolicitacaoMaterial.TECNICO) : new SolicitacaoFilter(null, null, null, null, null, null, null, ContextoSolicitacaoMaterial.TECNICO, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            exportarDetalhado(filtroTec, idUsuarioLogado, apenasMinhas, writerTec);
            writerTec.flush();
            zipOut.closeEntry();

            zipOut.finish();
        }
    }

    private void processarPaginas(SolicitacaoFilter filtro, LinhaConsumer consumer, CSVPrinter printer) throws IOException {
        int numeroPagina = 0;
        while (true) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, ORDENACAO);
            Page<SolicitacaoMaterial> pagina = repository.findAll(SolicitacaoSpecification.filterBy(filtro), pageable);
            List<SolicitacaoMaterial> conteudo = pagina.getContent();

            if (conteudo.isEmpty()) {
                break;
            }

            for (SolicitacaoMaterial solicitacao : conteudo) {
                consumer.accept(printer, solicitacao);
            }
            printer.flush();

            if (numeroPagina >= pagina.getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private SolicitacaoFilter aplicarApenasMinhas(SolicitacaoFilter filtro, UUID idUsuarioLogado, Boolean apenasMinhas) {
        if (filtro == null) {
            filtro = new SolicitacaoFilter(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null);
        }
        if (Boolean.TRUE.equals(apenasMinhas) && filtro.idRequisitante() == null && idUsuarioLogado != null) {
            return filtro.comRequisitante(idUsuarioLogado);
        }
        return filtro;
    }

    // --- MONTAGEM DE LINHAS COMERCIAIS ---

    private List<Object> montarLinhaResumoComercial(SolicitacaoMaterial sol, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisComercial(linha, sol, contexto);
        adicionarTotaisItens(linha, sol);
        return linha;
    }

    private List<Object> montarLinhaDetalhadaComercialComItem(SolicitacaoMaterial sol, ItemSolicitacao item, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisComercial(linha, sol, contexto);
        adicionarDadosItem(linha, item);
        return linha;
    }

    private List<Object> montarLinhaDetalhadaComercialSemItem(SolicitacaoMaterial sol, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisComercial(linha, sol, contexto);
        adicionarEspacosVaziosItem(linha);
        return linha;
    }

    private void adicionarDadosGeraisComercial(List<Object> linha, SolicitacaoMaterial sol, ContextoLookup contexto) {
        linha.add(valorOuVazio(sol.getId()));
        linha.add(sol.getContexto() != null ? sol.getContexto().name() : "COMERCIAL");
        linha.add(sol.getStatus() != null ? sol.getStatus().name() : "");
        linha.add(formatarDataHora(sol.getDataSolicitacao()));
        linha.add(formatarDataHora(sol.getDataVerificacao()));
        linha.add(formatarDataHora(sol.getDataEntrega()));
        linha.add(sol.getEstoqueOrigem() != null ? sol.getEstoqueOrigem().getNomeIdentificador() : "");
        linha.add(sol.getEstoqueDestino() != null ? sol.getEstoqueDestino().getNomeIdentificador() : "");
        linha.add(contexto.obterUsuario(sol.getIdRequisitante(), usuarioApi));
        linha.add(contexto.obterUsuario(sol.getIdAprovador(), usuarioApi));
        linha.add(contexto.obterUsuario(sol.getIdEntregadorAlmox(), usuarioApi));
        linha.add(sol.getMotivoRejeicao() != null ? sol.getMotivoRejeicao().getDescricao() : "");
        linha.add(valorOuVazio(sol.getJustificativaRejeicao()));
        linha.add(valorOuVazio(sol.getObservacao()));
    }

    // --- MONTAGEM DE LINHAS TÉCNICAS ---

    private List<Object> montarLinhaResumoTecnico(SolicitacaoMaterial sol, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisTecnico(linha, sol, contexto);
        adicionarTotaisItens(linha, sol);
        return linha;
    }

    private List<Object> montarLinhaDetalhadaTecnicoComItem(SolicitacaoMaterial sol, ItemSolicitacao item, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisTecnico(linha, sol, contexto);
        adicionarDadosItem(linha, item);
        return linha;
    }

    private List<Object> montarLinhaDetalhadaTecnicoSemItem(SolicitacaoMaterial sol, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisTecnico(linha, sol, contexto);
        adicionarEspacosVaziosItem(linha);
        return linha;
    }

    private void adicionarDadosGeraisTecnico(List<Object> linha, SolicitacaoMaterial sol, ContextoLookup contexto) {
        linha.add(valorOuVazio(sol.getId()));
        linha.add(sol.getContexto() != null ? sol.getContexto().name() : "TECNICO");
        linha.add(sol.getStatus() != null ? sol.getStatus().name() : "");
        linha.add(sol.getTipoTecnico() != null ? sol.getTipoTecnico().getCodigo() : "");

        EtapaOperacionalSolicitacao etapa = EtapaOperacionalSolicitacao.calcular(sol);
        linha.add(etapa != null ? etapa.name() : "");
        linha.add(sol.getStatusSeparacao() != null ? sol.getStatusSeparacao().name() : "");

        linha.add(formatarDataHora(sol.getDataSolicitacao()));
        linha.add(formatarData(sol.getDataPrevistaRetirada()));
        linha.add(formatarDataHora(sol.getDataVerificacao()));
        linha.add(formatarDataHora(sol.getDataEntrega()));

        linha.add(sol.getAlmoxarifadoAtendimento() != null ? sol.getAlmoxarifadoAtendimento().getNomeIdentificador() : (sol.getEstoqueOrigem() != null ? sol.getEstoqueOrigem().getNomeIdentificador() : ""));

        // Obra e Ocorrência
        if (sol.getIdObra() != null) {
            ObraPublica obra = contexto.obterObra(sol.getIdObra(), obraApi);
            linha.add(obra != null ? obra.numero() : "");
            linha.add(obra != null ? obra.titulo() : "");
            linha.add(sol.getTipoObraSnapshot() != null ? sol.getTipoObraSnapshot().name() : (obra != null && obra.tipoObra() != null ? obra.tipoObra().name() : ""));
        } else {
            linha.add("");
            linha.add("");
            linha.add(sol.getTipoObraSnapshot() != null ? sol.getTipoObraSnapshot().name() : "");
        }
        linha.add(valorOuVazio(sol.getOcorrencia()));

        // Equipe e Colaborador de Saque
        if (sol.getIdEquipeSaque() != null) {
            EquipePublica equipe = contexto.obterEquipe(sol.getIdEquipeSaque(), equipeApi);
            linha.add(equipe != null ? equipe.codigo() : valorOuVazio(sol.getIdEquipeSaque()));
        } else {
            linha.add("");
        }

        if (sol.getIdColaboradorSaque() != null) {
            linha.add(contexto.obterColaborador(sol.getIdColaboradorSaque(), colaboradorApi));
        } else {
            linha.add("");
        }
        linha.add(formatarBooleano(sol.isSaqueForaEquipe()));

        // Reserva
        ReservaSolicitacaoTecnica reserva = sol.getReserva();
        if (reserva != null) {
            linha.add(valorOuVazio(reserva.getNumeroApi()));
            linha.add(valorOuVazio(reserva.getNomeAgenteSnapshot()));
            linha.add(valorOuVazio(reserva.getEmpresaAgenteSnapshot()));
        } else {
            linha.add("");
            linha.add("");
            linha.add("");
        }

        // Aprovador e Entregador
        linha.add(contexto.obterUsuario(sol.getIdAprovador(), usuarioApi));
        linha.add(contexto.obterUsuario(sol.getIdEntregadorAlmox(), usuarioApi));

        // Rejeição
        linha.add(sol.getMotivoRejeicao() != null ? sol.getMotivoRejeicao().getDescricao() : "");
        linha.add(valorOuVazio(sol.getJustificativaRejeicao()));

        // Encerramento
        linha.add(formatarDataHora(sol.getEncerradaEm()));
        linha.add(valorOuVazio(sol.getMotivoEncerramento()));

        // Observações
        linha.add(valorOuVazio(sol.getObservacao()));
    }

    // --- MONTAGEM DE LINHAS CANÔNICAS (CONSOLIDADO GERAL) ---

    private List<Object> montarLinhaResumoCanonica(SolicitacaoMaterial sol, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisCanonica(linha, sol, contexto);
        adicionarTotaisItens(linha, sol);
        return linha;
    }

    private List<Object> montarLinhaDetalhadaCanonicaComItem(SolicitacaoMaterial sol, ItemSolicitacao item, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisCanonica(linha, sol, contexto);

        Material mat = item.getMaterial();
        linha.add(valorOuVazio(item.getId()));
        linha.add(mat != null ? valorOuVazio(mat.getCodigo()) : "");
        linha.add(mat != null ? valorOuVazio(mat.getDescricao()) : "");
        linha.add(mat != null && mat.getUnidadeMedida() != null ? mat.getUnidadeMedida() : "");
        linha.add(mat != null ? formatarBooleano(mat.isDetalhaSerie()) : "");
        linha.add(valorOuVazio(item.getNumSerie()));
        linha.add(formatarNumero(item.getQuantidade()));
        linha.add(formatarNumero(item.getQuantidadeEntregaLiquida()));
        linha.add(formatarNumero(item.getQuantidadeDevolucaoLiquida()));
        linha.add(formatarNumero(item.getQuantidadePendente()));
        linha.add(determinarStatusItem(item));

        return linha;
    }

    private List<Object> montarLinhaDetalhadaCanonicaSemItem(SolicitacaoMaterial sol, ContextoLookup contexto) {
        List<Object> linha = new ArrayList<>();
        adicionarDadosGeraisCanonica(linha, sol, contexto);
        for (int i = 0; i < 11; i++) {
            linha.add("");
        }
        return linha;
    }

    private void adicionarDadosGeraisCanonica(List<Object> linha, SolicitacaoMaterial sol, ContextoLookup contexto) {
        linha.add(valorOuVazio(sol.getId()));
        linha.add(sol.getContexto() != null ? sol.getContexto().name() : "");
        linha.add(sol.getStatus() != null ? sol.getStatus().name() : "");

        // Tipo Operação
        if (sol.getContexto() == ContextoSolicitacaoMaterial.COMERCIAL) {
            linha.add("Solicitação Direta");
        } else if (sol.getTipoTecnico() != null) {
            linha.add(sol.getTipoTecnico().getCodigo() + " - " + sol.getTipoTecnico().name());
        } else {
            linha.add("Técnico");
        }

        linha.add(formatarDataHora(sol.getDataSolicitacao()));
        linha.add(formatarDataHora(sol.getDataEntrega() != null ? sol.getDataEntrega() : sol.getDataVerificacao()));

        // Origem
        if (sol.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            linha.add(sol.getAlmoxarifadoAtendimento() != null ? sol.getAlmoxarifadoAtendimento().getNomeIdentificador() : (sol.getEstoqueOrigem() != null ? sol.getEstoqueOrigem().getNomeIdentificador() : ""));
        } else {
            linha.add(sol.getEstoqueOrigem() != null ? sol.getEstoqueOrigem().getNomeIdentificador() : "");
        }

        // Destino / Aplicação
        if (sol.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            if (sol.getIdObra() != null) {
                ObraPublica obra = contexto.obterObra(sol.getIdObra(), obraApi);
                linha.add(obra != null ? (obra.numero() + " - " + obra.titulo()) : "Obra " + sol.getIdObra());
            } else {
                linha.add("");
            }
        } else {
            linha.add(sol.getEstoqueDestino() != null ? sol.getEstoqueDestino().getNomeIdentificador() : "");
        }

        // Solicitante / Equipe
        if (sol.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            String eqStr = "";
            if (sol.getIdEquipeSaque() != null) {
                EquipePublica equipe = contexto.obterEquipe(sol.getIdEquipeSaque(), equipeApi);
                eqStr = equipe != null ? equipe.codigo() : sol.getIdEquipeSaque().toString();
            }
            String colabStr = "";
            if (sol.getIdColaboradorSaque() != null) {
                colabStr = contexto.obterColaborador(sol.getIdColaboradorSaque(), colaboradorApi);
            }
            if (!eqStr.isEmpty() && !colabStr.isEmpty()) {
                linha.add(eqStr + " / " + colabStr);
            } else if (!eqStr.isEmpty()) {
                linha.add(eqStr);
            } else {
                linha.add(colabStr);
            }
        } else {
            linha.add(contexto.obterUsuario(sol.getIdRequisitante(), usuarioApi));
        }

        // Documento / Referência
        if (sol.getContexto() == ContextoSolicitacaoMaterial.TECNICO) {
            List<String> refs = new ArrayList<>();
            if (sol.getOcorrencia() != null && !sol.getOcorrencia().isBlank()) {
                refs.add("OC: " + sol.getOcorrencia().trim());
            }
            if (sol.getReserva() != null && sol.getReserva().getNumeroApi() != null && !sol.getReserva().getNumeroApi().isBlank()) {
                refs.add("Reserva: " + sol.getReserva().getNumeroApi().trim());
            }
            linha.add(String.join(" | ", refs));
        } else {
            linha.add(valorOuVazio(sol.getObservacao()));
        }

        linha.add(valorOuVazio(sol.getObservacao()));
    }

    // --- MÉTODOS AUXILIARES ---

    private void adicionarTotaisItens(List<Object> linha, SolicitacaoMaterial sol) {
        List<ItemSolicitacao> itens = sol.getItens() != null ? sol.getItens() : List.of();
        BigDecimal totalSolicitada = BigDecimal.ZERO;
        BigDecimal totalEntregueLiq = BigDecimal.ZERO;
        BigDecimal totalDevolvidaLiq = BigDecimal.ZERO;
        BigDecimal totalPendente = BigDecimal.ZERO;

        for (ItemSolicitacao item : itens) {
            if (item.getQuantidade() != null) totalSolicitada = totalSolicitada.add(item.getQuantidade());
            totalEntregueLiq = totalEntregueLiq.add(item.getQuantidadeEntregaLiquida());
            totalDevolvidaLiq = totalDevolvidaLiq.add(item.getQuantidadeDevolucaoLiquida());
            totalPendente = totalPendente.add(item.getQuantidadePendente());
        }

        linha.add(itens.size());
        linha.add(formatarNumero(totalSolicitada));
        linha.add(formatarNumero(totalEntregueLiq));
        linha.add(formatarNumero(totalDevolvidaLiq));
        linha.add(formatarNumero(totalPendente));
    }

    private void adicionarDadosItem(List<Object> linha, ItemSolicitacao item) {
        Material mat = item.getMaterial();
        linha.add(valorOuVazio(item.getId()));
        linha.add(mat != null ? valorOuVazio(mat.getCodigo()) : "");
        linha.add(mat != null ? valorOuVazio(mat.getDescricao()) : "");
        linha.add(mat != null && mat.getUnidadeMedida() != null ? mat.getUnidadeMedida() : "");
        linha.add(mat != null ? formatarBooleano(mat.isDetalhaSerie()) : "");
        linha.add(valorOuVazio(item.getNumSerie()));
        linha.add(formatarNumero(item.getQuantidade()));
        linha.add(formatarNumero(item.getQuantidadeEntregueBruta()));
        linha.add(formatarNumero(item.getQuantidadeEntregaEstornada()));
        linha.add(formatarNumero(item.getQuantidadeEntregaLiquida()));
        linha.add(formatarNumero(item.getQuantidadeDevolvidaBruta()));
        linha.add(formatarNumero(item.getQuantidadeDevolucaoEstornada()));
        linha.add(formatarNumero(item.getQuantidadeDevolucaoLiquida()));
        linha.add(formatarNumero(item.getQuantidadePendente()));
        linha.add(determinarStatusItem(item));
    }

    private void adicionarEspacosVaziosItem(List<Object> linha) {
        for (int i = 0; i < 15; i++) {
            linha.add("");
        }
    }

    private String determinarStatusItem(ItemSolicitacao item) {
        if (item.isTotalmenteEntregue()) {
            return "TOTALMENTE_ATENDIDO";
        }
        if (item.possuiFatoOperacional()) {
            return "PARCIALMENTE_ATENDIDO";
        }
        return "PENDENTE";
    }

    private String formatarDataHora(OffsetDateTime dt) {
        return dt != null ? dt.format(DATA_HORA) : "";
    }

    private String formatarData(LocalDate d) {
        return d != null ? d.format(DATA) : "";
    }

    private String formatarNumero(BigDecimal num) {
        return num != null ? num.stripTrailingZeros().toPlainString() : "0";
    }

    private String formatarBooleano(Boolean b) {
        return Boolean.TRUE.equals(b) ? "Sim" : "Não";
    }

    private String valorOuVazio(Object obj) {
        return obj != null ? obj.toString().trim() : "";
    }

    @FunctionalInterface
    private interface LinhaConsumer {
        void accept(CSVPrinter printer, SolicitacaoMaterial solicitacao) throws IOException;
    }

    private static class ContextoLookup {
        private final Map<UUID, String> usuarios = new HashMap<>();
        private final Map<UUID, ObraPublica> obras = new HashMap<>();
        private final Map<UUID, EquipePublica> equipes = new HashMap<>();
        private final Map<UUID, String> colaboradores = new HashMap<>();

        String obterUsuario(UUID id, Optional<UsuarioApi> api) {
            if (id == null) return "";
            return usuarios.computeIfAbsent(id, uid ->
                    api.flatMap(a -> a.buscarNomePorId(uid)).orElse(uid.toString()));
        }

        ObraPublica obterObra(UUID id, Optional<ObraApi> api) {
            if (id == null) return null;
            return obras.computeIfAbsent(id, oid ->
                    api.flatMap(a -> a.buscarPorId(oid)).orElse(null));
        }

        EquipePublica obterEquipe(UUID id, Optional<EquipeApi> api) {
            if (id == null) return null;
            return equipes.computeIfAbsent(id, eid ->
                    api.flatMap(a -> a.buscarPublicaPorId(eid)).orElse(null));
        }

        String obterColaborador(UUID id, Optional<ColaboradorApi> api) {
            if (id == null) return "";
            return colaboradores.computeIfAbsent(id, cid ->
                    api.flatMap(a -> a.buscarPorIdResponse(cid)).map(ColaboradorResponse::nomeCompleto).orElse(cid.toString()));
        }
    }
}
