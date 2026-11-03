package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.PlanejamentoObra;
import com.nexo.app.modules.obras.domain.TipoExecucaoPonto;
import com.nexo.app.modules.obras.infrastructure.persistence.KitAplicadoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PlanejamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.VaoPlanejamentoRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pré-fechamento do levantamento: por obra em aberto, quantos pontos e vãos foram lançados, o que ainda está sem kit
 * ou sem vão, como cada ponto será executado e quem levantou. A obra só fecha o levantamento sem ponto ou vão sem kit.
 */
@Service
@RequiredArgsConstructor
public class PreFechamentoObraUseCases {

    private final ObraRepository obraRepository;
    private final PlanejamentoObraRepository planejamentoRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final VaoPlanejamentoRepository vaoRepository;
    private final KitAplicadoRepository kitRepository;
    private final ContextoObras contextos;
    private final AuditorAware<String> auditor;

    /** Contagens do levantamento de uma obra. */
    private static final class Contagem {
        int pontos;
        int vaos;
        int pontosSemVao;
        int pontosSemKit;
        int vaosSemKit;
        int semDesligamento;
        int comDesligamento;
        int naoDefinido;
        final Set<String> levantadores = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Obras em aberto do recorte. Os indicadores contam o recorte inteiro; "apenas pendentes" só filtra as linhas.
     * Ordena pelo que mais falta: pendentes primeiro, depois mais pontos sem kit, depois mais pontos.
     */
    @Transactional(readOnly = true)
    public MedicaoResponses.PreFechamento listar(String q, UUID idBase, boolean apenasPendentes) {
        List<Obra> obras = obraRepository.listarEmAberto().stream()
                .filter(o -> idBase == null || idBase.equals(o.getIdBaseOperacional()))
                .filter(o -> corresponde(o, q))
                .toList();
        List<UUID> ids = obras.stream().map(Obra::getId).toList();
        Map<UUID, Contagem> contagens = contar(ids);
        Map<UUID, PlanejamentoObra> planejamentos = ids.isEmpty() ? Map.of() : planejamentoRepository.findByIdObraInAndAtivoTrue(ids).stream()
                .collect(Collectors.toMap(PlanejamentoObra::getIdObra, Function.identity(), (a, b) -> a));
        Map<UUID, MedicaoResponses.ContextoObra> contexto = contextos.de(obras);

        List<MedicaoResponses.LinhaPreFechamento> linhas = obras.stream()
                .map(o -> linha(o, contagens.getOrDefault(o.getId(), new Contagem()), planejamentos.get(o.getId()), contexto.get(o.getId())))
                .toList();
        int concluidas = (int) linhas.stream().filter(MedicaoResponses.LinhaPreFechamento::concluido).count();
        int prontas = (int) linhas.stream().filter(MedicaoResponses.LinhaPreFechamento::prontoParaConcluir).count();
        int semKit = linhas.stream().filter(l -> !l.concluido()).mapToInt(MedicaoResponses.LinhaPreFechamento::pontosSemKit).sum();
        int vaosSemKit = linhas.stream().filter(l -> !l.concluido()).mapToInt(MedicaoResponses.LinhaPreFechamento::vaosSemKit).sum();
        List<MedicaoResponses.LinhaPreFechamento> visiveis = linhas.stream()
                .filter(l -> !apenasPendentes || !l.concluido())
                .sorted(Comparator.comparing(MedicaoResponses.LinhaPreFechamento::concluido)
                        .thenComparing(MedicaoResponses.LinhaPreFechamento::pontosSemKit, Comparator.reverseOrder())
                        .thenComparing(MedicaoResponses.LinhaPreFechamento::pontos, Comparator.reverseOrder())
                        .thenComparing(MedicaoResponses.LinhaPreFechamento::numero))
                .toList();
        return new MedicaoResponses.PreFechamento(visiveis, linhas.size(), linhas.size() - concluidas, prontas, concluidas, semKit, vaosSemKit);
    }

    @Transactional
    public MedicaoResponses.LinhaPreFechamento concluir(UUID idObra) {
        Obra obra = buscarObra(idObra);
        PlanejamentoObra planejamento = planejamentoRepository.findByIdObraAndAtivoTrue(idObra)
                .orElseGet(() -> planejamentoRepository.save(new PlanejamentoObra(idObra)));
        Contagem contagem = contar(List.of(idObra)).getOrDefault(idObra, new Contagem());
        planejamento.concluirLevantamento(contagem.pontos, pontoRepository.numerosSemKit(idObra), contagem.vaosSemKit,
                auditor.getCurrentAuditor().orElse("SYSTEM"), OffsetDateTime.now(MedicaoObraUseCases.FUSO));
        planejamentoRepository.saveAndFlush(planejamento);
        return linha(obra, contagem, planejamento, contextos.de(List.of(obra)).get(idObra));
    }

    @Transactional
    public MedicaoResponses.LinhaPreFechamento reabrir(UUID idObra) {
        Obra obra = buscarObra(idObra);
        PlanejamentoObra planejamento = planejamentoRepository.findByIdObraAndAtivoTrue(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("A obra não tem planejamento."));
        planejamento.reabrirLevantamento();
        planejamentoRepository.saveAndFlush(planejamento);
        return linha(obra, contar(List.of(idObra)).getOrDefault(idObra, new Contagem()), planejamento, contextos.de(List.of(obra)).get(idObra));
    }

    private Map<UUID, Contagem> contar(Collection<UUID> ids) {
        Map<UUID, Contagem> contagens = new HashMap<>();
        if (ids.isEmpty()) {
            return contagens;
        }
        for (Object[] linha : pontoRepository.contarPorObra(ids)) {
            Contagem c = contagens.computeIfAbsent((UUID) linha[0], id -> new Contagem());
            int pontos = ((Number) linha[2]).intValue();
            c.pontos += pontos;
            c.pontosSemKit += ((Number) linha[3]).intValue();
            c.pontosSemVao += ((Number) linha[4]).intValue();
            TipoExecucaoPonto tipo = (TipoExecucaoPonto) linha[1];
            if (tipo == TipoExecucaoPonto.SEM_DESLIGAMENTO) {
                c.semDesligamento += pontos;
            } else if (tipo == TipoExecucaoPonto.COM_DESLIGAMENTO) {
                c.comDesligamento += pontos;
            } else {
                c.naoDefinido += pontos;
            }
        }
        for (Object[] linha : vaoRepository.contarPorObra(ids)) {
            Contagem c = contagens.computeIfAbsent((UUID) linha[0], id -> new Contagem());
            c.vaos = ((Number) linha[1]).intValue();
            c.vaosSemKit = ((Number) linha[2]).intValue();
        }
        for (List<Object[]> levantadores : List.of(pontoRepository.levantadores(ids), vaoRepository.levantadores(ids), kitRepository.levantadores(ids))) {
            for (Object[] linha : levantadores) {
                String usuario = (String) linha[1];
                if (!"SYSTEM".equalsIgnoreCase(usuario)) {
                    contagens.computeIfAbsent((UUID) linha[0], id -> new Contagem()).levantadores.add(usuario);
                }
            }
        }
        return contagens;
    }

    private static MedicaoResponses.LinhaPreFechamento linha(Obra obra, Contagem c, PlanejamentoObra planejamento,
                                                             MedicaoResponses.ContextoObra contexto) {
        boolean concluido = planejamento != null && planejamento.levantamentoConcluido();
        return new MedicaoResponses.LinhaPreFechamento(obra.getId(), obra.getNumero(), obra.getTitulo(),
                contexto, obra.getTipoObra() != null ? obra.getTipoObra().name() : null,
                c.pontos, c.vaos, c.pontosSemVao, c.pontosSemKit, c.vaosSemKit, c.semDesligamento, c.comDesligamento, c.naoDefinido,
                List.copyOf(c.levantadores), concluido,
                concluido ? planejamento.getLevantamentoConcluidoEm() : null,
                concluido ? planejamento.getLevantamentoConcluidoPor() : null,
                !concluido && c.pontos > 0 && c.pontosSemKit == 0 && c.vaosSemKit == 0);
    }

    private static boolean corresponde(Obra obra, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String termo = q.trim().toLowerCase(Locale.ROOT);
        return (obra.getNumero() != null && obra.getNumero().toLowerCase(Locale.ROOT).contains(termo))
                || (obra.getTitulo() != null && obra.getTitulo().toLowerCase(Locale.ROOT).contains(termo));
    }

    private Obra buscarObra(UUID idObra) {
        return obraRepository.findById(idObra).filter(Obra::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));
    }
}
