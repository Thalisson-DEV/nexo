package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.EventoSstFilter;
import com.nexo.app.modules.sst.domain.EnvolvidoEvento;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exportação da lista de eventos em CSV.
 *
 * <p>Usa exatamente a mesma {@code Specification} da listagem — os filtros aplicados na
 * tela são os filtros da planilha, sem uma segunda implementação que possa divergir.
 * A leitura é paginada para que uma exportação grande não carregue tudo de uma vez.</p>
 *
 * <p>A planilha traz os nomes de gestor e acidentado <b>decifrados</b>: é um relatório
 * de quem já tem permissão de leitura sobre o evento, e uma coluna com o nome é o
 * motivo pelo qual o SESMT exporta. O que não sai daqui é o número da CAT de terceiros
 * nem qualquer dado que o evento não guarde.</p>
 */
@Service
@RequiredArgsConstructor
public class ExportarEventosSstUseCase {

    private static final int MAX_REGISTROS = 10_000;
    private static final int TAMANHO_PAGINA = 500;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] CABECALHO = {
            "Número", "Tipo", "Status", "Data da ocorrência", "Data da comunicação",
            "Horas até comunicar", "Comunicação atrasada", "Atividade", "Gestor", "Gestor substituto",
            "Base operacional", "Equipe", "Veículo", "Classificação", "Local da ocorrência",
            "Com lesão", "Local da lesão", "Dias perdidos", "Acidentados", "Técnicos responsáveis",
            "Possui CAT", "Tipo da CAT", "Número da CAT", "Emissão da CAT", "Descrição", "Tratativa"};

    private final EventoSstRepository repository;
    private final CatalogoSstRepository catalogoRepository;
    private final ColaboradorApi colaboradorApi;

    @Transactional(readOnly = true)
    public void exportar(EventoSstFilter filter, Writer writer) throws IOException {
        CSVFormat formato = CSVFormat.Builder.create().setHeader(CABECALHO).setDelimiter(';').build();

        try (CSVPrinter printer = new CSVPrinter(writer, formato)) {
            List<UUID> idsPorNome = resolverNomeDeColaborador(filter);
            int pagina = 0;
            int exportados = 0;

            while (exportados < MAX_REGISTROS) {
                Page<EventoSst> lote = repository.findAll(
                        EventoSstSpecification.geral(filter, idsPorNome),
                        PageRequest.of(pagina, TAMANHO_PAGINA, Sort.by(Sort.Direction.DESC, "dataOcorrencia")));
                if (lote.isEmpty()) {
                    break;
                }

                Map<UUID, String> catalogos = nomesDeCatalogo(lote.getContent());
                for (EventoSst evento : lote.getContent()) {
                    imprimir(printer, evento, catalogos);
                    exportados++;
                }

                if (!lote.hasNext()) {
                    break;
                }
                pagina++;
            }
        }
    }

    private void imprimir(CSVPrinter printer, EventoSst evento, Map<UUID, String> catalogos) throws IOException {
        printer.printRecord(
                evento.getNumeroRegistro(),
                evento.getTipo().getRotulo(),
                evento.getStatus().getRotulo(),
                evento.getDataOcorrencia().format(DATA_HORA),
                evento.getDataComunicacao().format(DATA_HORA),
                evento.getHorasAteComunicacao(),
                simNao(evento.isComunicacaoAtrasada()),
                evento.getAtividadeNome(),
                evento.getGestorNome(),
                simNao(evento.isGestorSubstituto()),
                evento.getBaseNome(),
                texto(evento.getEquipeCodigo()),
                texto(evento.getVeiculoPlaca()),
                catalogos.getOrDefault(evento.getIdClassificacao(), ""),
                texto(evento.getLocalOcorrencia()),
                simNao(evento.isComLesao()),
                evento.getIdLocalLesao() == null ? "" : catalogos.getOrDefault(evento.getIdLocalLesao(), ""),
                evento.getDiasPerdidos() == null ? "" : evento.getDiasPerdidos(),
                nomesDosEnvolvidos(evento, PapelEnvolvido.ACIDENTADO),
                evento.getTecnicos().stream().map(tecnico -> tecnico.getNome()).collect(Collectors.joining(", ")),
                simNao(evento.isPossuiCat()),
                evento.getTipoCat() == null ? "" : evento.getTipoCat().getRotulo(),
                texto(evento.getNumeroCat()),
                evento.getDataEmissaoCat() == null ? "" : evento.getDataEmissaoCat().format(DATA),
                texto(evento.getDescricao()),
                texto(evento.getTratativa()));
    }

    private static String nomesDosEnvolvidos(EventoSst evento, PapelEnvolvido papel) {
        return evento.getEnvolvidos().stream()
                .filter(envolvido -> envolvido.getPapel() == papel)
                .map(EnvolvidoEvento::getNome)
                .collect(Collectors.joining(", "));
    }

    private List<UUID> resolverNomeDeColaborador(EventoSstFilter filter) {
        if (filter == null || filter.nomeColaborador() == null || filter.nomeColaborador().isBlank()) {
            return null;
        }
        return colaboradorApi.buscarIdsPorTrechoDoNome(filter.nomeColaborador().trim());
    }

    private Map<UUID, String> nomesDeCatalogo(List<EventoSst> eventos) {
        Set<UUID> ids = eventos.stream()
                .flatMap(evento -> java.util.stream.Stream.of(
                        evento.getIdClassificacao(), evento.getIdLocalLesao()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> nomes = new HashMap<>();
        catalogoRepository.findAllById(ids).forEach(entrada -> nomes.put(entrada.getId(), entrada.getNome()));
        return nomes;
    }

    private static String simNao(boolean valor) {
        return valor ? "Sim" : "Não";
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
