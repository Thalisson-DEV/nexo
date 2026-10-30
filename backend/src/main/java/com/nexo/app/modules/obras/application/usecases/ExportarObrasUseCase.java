package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ObraFilter;
import com.nexo.app.modules.obras.application.dto.ObraResumoResponse;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraSpecification;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportarObrasUseCase {

    private final ObraUseCases obraUseCases;
    private final ObraRepository obraRepository;
    private final com.nexo.app.modules.rh.SupervisorApi supervisorApi;
    private final com.nexo.app.modules.rh.CoordenadorApi coordenadorApi;
    private final RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;

    private static final int MAX_REGISTROS = 10000;
    private static final int TAMANHO_PAGINA = 500;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FunctionalInterface
    private interface LinhaPrinter<T> {
        void imprimir(CSVPrinter printer, T item) throws IOException;
    }

    /**
     * Exporta os dados resumidos das obras em formato CSV.
     */
    @Transactional(readOnly = true)
    public void exportarResumo(ObraFilter filter, Writer writer) throws IOException {
        String[] headers = {
                "Número", "Título", "Tipo Obra", "Nota da Obra", "Status", "Processo",
                "Base Operacional", "Município", "Centro de Custo",
                "Supervisor", "Coordenador",
                "Data Início", "Data Previsão Fim", "Data Fim Efetiva",
                "Valor do Projeto", "Agente Responsável",
                "Qtd Projetos Relacionados",
                "Qtd Equipes", "Qtd Anexos", "Ativo"
        };

        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader(headers)
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginasResumo(filter, printer);
        }
    }

    /**
     * Exporta os dados detalhados das obras em formato CSV.
     */
    @Transactional(readOnly = true)
    public void exportarDetalhado(ObraFilter filter, Writer writer) throws IOException {
        String[] headers = {
                "ID Obra", "Número", "Título", "Tipo Obra", "Descrição", "Nota da Obra",
                "Status", "Processo", "Base Operacional", "Município", "Centro de Custo",
                "Supervisor", "Coordenador",
                "Data Início", "Data Previsão Fim", "Data Fim Efetiva",
                "Latitude", "Longitude", "Valor do Projeto",
                "Agente Responsável", "E-mail Agente",
                "Projetos Relacionados", "Qtd Projetos Relacionados",
                "Qtd Equipes", "Qtd Anexos",
                "Data Criação", "Usuário Criação",
                "Data Última Alteração", "Usuário Última Alteração", "Ativo"
        };

        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader(headers)
                .setDelimiter(';')
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            processarPaginasDetalhado(filter, printer);
        }
    }

    private void processarPaginasResumo(ObraFilter filter, CSVPrinter printer) throws IOException {
        int registrosProcessados = 0;
        int numeroPagina = 0;

        while (registrosProcessados < MAX_REGISTROS) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, Sort.by("dataCriacao").descending());
            PageResponse<ObraResumoResponse> page = obraUseCases.listar(filter, pageable);

            List<ObraResumoResponse> content = page.getContent();
            if (content.isEmpty()) {
                break;
            }

            for (ObraResumoResponse obra : content) {
                if (registrosProcessados >= MAX_REGISTROS) {
                    break;
                }
                imprimirLinhaResumo(printer, obra);
                registrosProcessados++;
            }

            if (numeroPagina >= page.getPage().getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private void processarPaginasDetalhado(ObraFilter filter, CSVPrinter printer) throws IOException {
        int registrosProcessados = 0;
        int numeroPagina = 0;

        while (registrosProcessados < MAX_REGISTROS) {
            Pageable pageable = PageRequest.of(numeroPagina, TAMANHO_PAGINA, Sort.unsorted());
            org.springframework.data.domain.Page<Obra> page = obraRepository.findAll(ObraSpecification.filterBy(filter, Sort.by("dataCriacao").descending()), pageable);

            List<Obra> content = page.getContent();
            if (content.isEmpty()) {
                break;
            }

            for (Obra obra : content) {
                if (registrosProcessados >= MAX_REGISTROS) {
                    break;
                }
                imprimirLinhaDetalhada(printer, obra);
                registrosProcessados++;
            }

            if (numeroPagina >= page.getTotalPages() - 1) {
                break;
            }
            numeroPagina++;
        }
    }

    private void imprimirLinhaResumo(CSVPrinter printer, ObraResumoResponse obra) throws IOException {
        printer.printRecord(
                obra.numero(),
                obra.titulo(),
                obra.tipoObra().name(),
                obra.notaObra() != null ? obra.notaObra() : "",
                obra.statusNome() != null ? obra.statusNome() : "",
                obra.processoNome() != null ? obra.processoNome() : "",
                obra.baseOperacionalNome() != null ? obra.baseOperacionalNome() : "",
                obra.municipioNome() != null ? obra.municipioNome() : "",
                obra.centroCustoNome() != null ? obra.centroCustoNome() : "",
                obra.supervisorNome() != null ? obra.supervisorNome() : "",
                obra.coordenadorNome() != null ? obra.coordenadorNome() : "",
                obra.dataInicio() != null ? obra.dataInicio().format(DATE_FORMATTER) : "",
                obra.dataPrevisaoFim() != null ? obra.dataPrevisaoFim().format(DATE_FORMATTER) : "",
                obra.dataFimEfetiva() != null ? obra.dataFimEfetiva().format(DATE_FORMATTER) : "",
                obra.valorProjeto() != null ? obra.valorProjeto().toString() : "",
                obra.agenteResponsavelNome() != null ? obra.agenteResponsavelNome() : "",
                obra.totalProjetosRelacionados(),
                obra.totalEquipes(),
                obra.totalAnexos(),
                obra.ativo() ? "Sim" : "Não"
        );
    }

    private void imprimirLinhaDetalhada(CSVPrinter printer, Obra obra) throws IOException {
        String supervisorNome = obra.getIdSupervisor() != null
                ? supervisorApi.buscarPorIdResponse(obra.getIdSupervisor())
                .map(s -> s.colaborador() != null ? s.colaborador().nomeCompleto() : "")
                .orElse("")
                : "";

        String coordenadorNome = obra.getIdCoordenador() != null
                ? coordenadorApi.buscarPorIdResponse(obra.getIdCoordenador())
                .map(c -> c.colaborador() != null ? c.colaborador().nomeCompleto() : "")
                .orElse("")
                : "";

        printer.printRecord(
                obra.getId() != null ? obra.getId().toString() : "",
                obra.getNumero(),
                obra.getTitulo(),
                obra.getTipoObra().name(),
                obra.getDescricao() != null ? obra.getDescricao() : "",
                obra.getNotaObra() != null ? obra.getNotaObra() : "",
                obra.getStatusObra() != null ? obra.getStatusObra().getNome() : "",
                obra.getIdProcesso() != null ? obra.getIdProcesso().toString() : "",
                obra.getIdBaseOperacional() != null ? obra.getIdBaseOperacional().toString() : "",
                obra.getIdMunicipio() != null ? obra.getIdMunicipio().toString() : "",
                obra.getIdCentroCusto() != null ? obra.getIdCentroCusto().toString() : "",
                supervisorNome,
                coordenadorNome,
                obra.getDataInicio() != null ? obra.getDataInicio().format(DATE_FORMATTER) : "",
                obra.getDataPrevisaoFim() != null ? obra.getDataPrevisaoFim().format(DATE_FORMATTER) : "",
                obra.getDataFimEfetiva() != null ? obra.getDataFimEfetiva().format(DATE_FORMATTER) : "",
                obra.getLatitude() != null ? obra.getLatitude().toString() : "",
                obra.getLongitude() != null ? obra.getLongitude().toString() : "",
                obra.getValorProjeto() != null ? obra.getValorProjeto().toString() : "",
                obra.getAgenteResponsavel() != null ? obra.getAgenteResponsavel().getNome() : "",
                obra.getAgenteResponsavel() != null ? obra.getAgenteResponsavel().getEmail() : "",
                formatarProjetosRelacionados(obra),
                relacionamentoProjetoUseCases.contarAtivos(obra.getId()),
                obra.getEquipes() != null ? obra.getEquipes().size() : 0,
                obra.getAnexos() != null ? obra.getAnexos().size() : 0,
                obra.getDataCriacao() != null ? obra.getDataCriacao().format(TIMESTAMP_FORMATTER) : "",
                obra.getUsuarioCriacao() != null ? obra.getUsuarioCriacao() : "",
                obra.getDataUltimaAlteracao() != null ? obra.getDataUltimaAlteracao().format(TIMESTAMP_FORMATTER) : "",
                obra.getUsuarioUltimaAlteracao() != null ? obra.getUsuarioUltimaAlteracao() : "",
                obra.isAtivo() ? "Sim" : "Não"
        );
    }

    private String formatarProjetosRelacionados(Obra obra) {
        return relacionamentoProjetoUseCases.listarSemValidarExistencia(obra.getId()).stream()
                .map(projeto -> projeto.tipoVinculo().name() + ":" + projeto.numero())
                .collect(java.util.stream.Collectors.joining(","));
    }
}
