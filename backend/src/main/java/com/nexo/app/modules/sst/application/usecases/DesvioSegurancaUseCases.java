package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.application.dto.DesvioDetalhadoResponse;
import com.nexo.app.modules.sst.application.dto.DesvioSegurancaFilter;
import com.nexo.app.modules.sst.domain.DesvioColaborador;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.domain.OpcaoResposta;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Listagem geral de desvios de segurança: a visão do SESMT sobre toda a empresa.
 *
 * <p>Complementa {@link MeuDesvioUseCases}, que existe para a caixa pessoal do inspetor
 * e por isso restringe tudo ao usuário autenticado. Aqui não há esse recorte — quem tem
 * a permissão enxerga a fila inteira e a fatia com filtros — e também não há tratamento:
 * esta é uma visão de leitura, e a escrita continua nos dois caminhos que já existiam
 * (a caixa do inspetor e o agregado da inspeção), onde a posse é conferida.</p>
 *
 * <p>Deliberadamente <b>sem cache</b>: o desvio muda de estado a cada tratamento, feito
 * por outros caminhos que não invalidariam esta entrada, e uma fila de cobrança que
 * mostra pendência já resolvida é pior do que uma consulta a mais.</p>
 */
@Service
@RequiredArgsConstructor
public class DesvioSegurancaUseCases {

    private final DesvioSegurancaRepository repository;
    private final FormularioInspecaoRepository formularioRepository;
    private final ProcessoNegocioApi processoApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final EquipeApi equipeApi;
    private final VeiculoApi veiculoApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    /** Ordem padrão da fila: o que foi registrado por último aparece primeiro. */
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Direction.DESC, "dataCriacao");

    @Transactional(readOnly = true)
    public PageResponse<DesvioDetalhadoResponse> listar(DesvioSegurancaFilter filter, Pageable pageable) {
        Page<DesvioSeguranca> pagina = repository.findAll(
                DesvioSegurancaSpecification.geral(filter), ordenar(pageable));
        Contexto contexto = new Contexto(pagina.getContent());
        return PageResponse.fromPage(pagina.map(contexto::montar));
    }

    /**
     * Sem ordenação explícita, o banco pode devolver a mesma linha em duas páginas e
     * esconder outra — por isso a listagem nunca pagina sem uma ordem definida.
     */
    private Pageable ordenar(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), MAIS_RECENTES);
    }

    @Transactional(readOnly = true)
    public DesvioDetalhadoResponse buscarPorId(UUID idDesvio) {
        DesvioSeguranca desvio = repository.findOne(DesvioSegurancaSpecification.porId(idDesvio))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Desvio de segurança não encontrado com ID: " + idDesvio));
        return new Contexto(List.of(desvio)).montar(desvio);
    }

    // --------------------------------------------------------------------- //
    //  Hidratação
    // --------------------------------------------------------------------- //

    /**
     * Resolve, para uma página inteira, tudo o que não está no agregado do desvio:
     * o formulário de origem, as pessoas e os recortes organizacionais.
     *
     * <p>As buscas em lote e a memória por id existem para que uma página custe um
     * punhado de consultas em vez de uma por linha — desvios de uma mesma inspeção, ou
     * de uma mesma base, repetem os mesmos identificadores o tempo todo.</p>
     */
    private final class Contexto {

        private final LocalDate hoje = LocalDate.now();

        private final Map<UUID, String> titulosFormulario = new HashMap<>();
        private final Map<UUID, DadosPergunta> perguntas = new HashMap<>();
        private final Map<UUID, ResumoColaborador> pessoas;
        private final Map<UUID, String> inspetores;

        private final Map<UUID, ProcessoNegocioResponse> processosInspetores = new HashMap<>();
        private final Map<UUID, ProcessoNegocioResponse> processos = new HashMap<>();
        private final Map<UUID, BaseOperacionalResponse> bases = new HashMap<>();
        private final Map<UUID, EquipeResponse> equipes = new HashMap<>();
        private final Map<UUID, VeiculoResponse> veiculos = new HashMap<>();

        private Contexto(List<DesvioSeguranca> desvios) {
            indexarFormularios(idsDistintos(desvios, d -> d.getInspecao().getIdFormulario()));
            this.pessoas = colaboradorApi.buscarResumosPorIds(desvios.stream()
                    .flatMap(d -> d.getColaboradores().stream())
                    .map(DesvioColaborador::getIdColaborador)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            this.inspetores = resolverInspetores(idsDistintos(desvios, d -> d.getInspecao().getIdInspetor()));
        }

        private DesvioDetalhadoResponse montar(DesvioSeguranca desvio) {
            InspecaoSeguranca inspecao = desvio.getInspecao();
            DadosPergunta pergunta = perguntas.get(desvio.getIdPergunta());

            boolean vencido = desvio.getPrazo() != null
                    && desvio.getPrazo().isBefore(hoje)
                    && desvio.getStatus() != StatusDesvio.RESOLVIDO;

            return new DesvioDetalhadoResponse(
                    desvio.getId(),
                    inspecao.getId(), inspecao.getDataRealizacao(), inspecao.getStatus(),
                    inspecao.getIdFormulario(), titulosFormulario.get(inspecao.getIdFormulario()),
                    inspecao.getIdInspetor(), inspetores.get(inspecao.getIdInspetor()),
                    resolver(processos, inspecao.getIdProcesso(), processoApi::buscarPorIdResponse),
                    resolverProcessoInspetor(inspecao),
                    resolver(bases, inspecao.getIdBaseOperacional(), baseOperacionalApi::buscarPorIdResponse),
                    resolver(equipes, inspecao.getIdEquipe(), equipeApi::buscarPorIdResponse),
                    resolver(veiculos, inspecao.getIdVeiculo(), veiculoApi::buscarPorIdResponse),
                    desvio.getIdPergunta(),
                    pergunta == null ? null : pergunta.grupo(),
                    pergunta == null ? null : pergunta.enunciado(),
                    desvio.getIdOpcaoResposta(),
                    textoDaOpcao(pergunta, desvio.getIdOpcaoResposta()),
                    desvio.getGravidade(), desvio.getDescricao(), desvio.getAcaoCorretiva(),
                    desvio.getPrazo(), desvio.getStatus(), desvio.getDataResolucao(),
                    vencido,
                    vencido ? (int) ChronoUnit.DAYS.between(desvio.getPrazo(), hoje) : null,
                    desvio.getColaboradores().stream()
                            .map(c -> pessoas.get(c.getIdColaborador()))
                            .filter(Objects::nonNull)
                            .toList(),
                    desvio.getAnexos().stream()
                            .map(a -> new AnexoDesvioResponse(
                                    a.getId(), a.getContexto(), a.getTipo(), a.getNomeArquivo(),
                                    a.getContentType(), a.getTamanhoBytes(), a.getDataCriacao()))
                            .toList(),
                    desvio.getDataCriacao(), desvio.getDataUltimaAlteracao()
            );
        }

        /**
         * Achata os formulários da página em dois índices: o título por formulário e,
         * por pergunta, o enunciado com o grupo e as opções. Os ids de pergunta são
         * globalmente únicos, então um índice só atende a página inteira.
         */
        private void indexarFormularios(Set<UUID> idsFormulario) {
            for (FormularioInspecao formulario : formularioRepository.findAllById(idsFormulario)) {
                titulosFormulario.put(formulario.getId(), formulario.getTitulo());
                formulario.getGrupos().forEach(grupo -> grupo.getPerguntas().forEach(p ->
                        perguntas.put(p.getId(), new DadosPergunta(
                                grupo.getTitulo(), p.getEnunciado(),
                                p.getOpcoes().stream().collect(Collectors.toMap(
                                        OpcaoResposta::getId, OpcaoResposta::getTexto))))));
            }
        }

        /**
         * O inspetor é um usuário; quem lê a fila reconhece a pessoa. Por isso o nome do
         * colaborador vem primeiro e o login só entra como reserva — para o usuário de
         * sistema ou o acesso ainda não vinculado a um cadastro de RH.
         */
        private Map<UUID, String> resolverInspetores(Set<UUID> ids) {
            Map<UUID, String> logins = usuarioApi.buscarLoginsPorIds(ids);
            Map<UUID, String> nomes = new HashMap<>(ids.size());
            for (UUID id : ids) {
                nomes.put(id, colaboradorApi.buscarNomeCompletoPorUsuario(id).orElseGet(() -> logins.get(id)));
            }
            return nomes;
        }

        private ProcessoNegocioResponse resolverProcessoInspetor(InspecaoSeguranca inspecao) {
            UUID idInspetor = inspecao.getIdInspetor();
            if (idInspetor == null) {
                return null;
            }
            if (processosInspetores.containsKey(idInspetor)) {
                return processosInspetores.get(idInspetor);
            }
            ProcessoNegocioResponse proc = colaboradorApi.buscarPorUsuarioResponse(idInspetor)
                    .or(() -> colaboradorApi.buscarPorIdResponse(idInspetor))
                    .map(ColaboradorResponse::processo)
                    .orElse(null);
            processosInspetores.put(idInspetor, proc);
            return proc;
        }

        /** Consulta memorizada por id — inclusive quando o cadastro não devolve nada. */
        private <T> T resolver(Map<UUID, T> memoria, UUID id, Function<UUID, Optional<T>> busca) {
            if (id == null) {
                return null;
            }
            if (memoria.containsKey(id)) {
                return memoria.get(id);
            }
            T valor = busca.apply(id).orElse(null);
            memoria.put(id, valor);
            return valor;
        }

        private String textoDaOpcao(DadosPergunta pergunta, UUID idOpcao) {
            if (pergunta == null || idOpcao == null) {
                return null;
            }
            return pergunta.opcoes().get(idOpcao);
        }

        private Set<UUID> idsDistintos(List<DesvioSeguranca> desvios, Function<DesvioSeguranca, UUID> extrator) {
            return desvios.stream()
                    .map(extrator)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /** Enunciado de uma pergunta com o grupo a que pertence e os textos de suas {@link OpcaoResposta}. */
    private record DadosPergunta(String grupo, String enunciado, Map<UUID, String> opcoes) {
    }
}
