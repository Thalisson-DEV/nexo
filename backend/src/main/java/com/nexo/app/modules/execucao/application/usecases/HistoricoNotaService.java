package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.execucao.application.dto.EventoHistoricoNotaResponse;
import com.nexo.app.modules.execucao.domain.AlteracaoLancamentos;
import com.nexo.app.modules.execucao.domain.DetalhesEventoNota;
import com.nexo.app.modules.execucao.domain.DetalhesEventoNota.AcaoRegistrada;
import com.nexo.app.modules.execucao.domain.DetalhesEventoNota.MaterialRegistrado;
import com.nexo.app.modules.execucao.domain.EventoHistoricoNota;
import com.nexo.app.modules.execucao.domain.ExecucaoAcao;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.ExecucaoMaterial;
import com.nexo.app.modules.execucao.domain.MaterialLancado;
import com.nexo.app.modules.execucao.domain.OrigemAlteracaoNota;
import com.nexo.app.modules.execucao.domain.TipoEventoNota;
import com.nexo.app.modules.execucao.infrastructure.persistence.EventoHistoricoNotaRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Linha do tempo da nota: grava os eventos com os nomes congelados e devolve a
 * sequência para a tela. Notas lançadas antes do histórico ganham um lançamento
 * reconstruído, desfazendo as alterações a partir do que a nota tem hoje.
 */
@Service
@RequiredArgsConstructor
public class HistoricoNotaService {

    private final EventoHistoricoNotaRepository repository;
    private final ContratoApi contratoApi;
    private final EstoqueApi estoqueApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    public void registrarLancamento(ExecucaoComercial execucao, UUID idAutor, OrigemAlteracaoNota origem) {
        var detalhes = new DetalhesEventoNota(
                execucao.getAcoes().stream().map(a -> acao(a.getIdAcao())).toList(),
                List.of(),
                execucao.getMateriais().stream().map(m -> material(m.comoLancado())).toList(),
                List.of(), null, null);
        OffsetDateTime agora = OffsetDateTime.now();
        repository.save(new EventoHistoricoNota(execucao.getId(), TipoEventoNota.LANCADA, origem, idAutor, autor(idAutor),
                null, null, execucao.getValorTotalFaturado(), detalhes, null, execucao.getDataLancamento(), agora));
    }

    public void registrarAlteracao(ExecucaoComercial execucao, AlteracaoLancamentos alteracao,
                                   List<DetalhesEventoNota.DadoAlterado> dadosAlterados, String motivo,
                                   BigDecimal valorAnterior, UUID idAutor, OrigemAlteracaoNota origem,
                                   UUID idOperacao, OffsetDateTime ocorridoEm) {
        var detalhes = new DetalhesEventoNota(
                alteracao.acoesAdicionadas().stream().map(this::acao).toList(),
                alteracao.acoesRemovidas().stream().map(this::acao).toList(),
                alteracao.materiaisAdicionados().stream().map(this::material).toList(),
                alteracao.materiaisRemovidos().stream().map(this::material).toList(),
                null, null, dadosAlterados);
        repository.save(new EventoHistoricoNota(execucao.getId(), TipoEventoNota.ALTERADA, origem, idAutor, autor(idAutor),
                motivo, valorAnterior, execucao.getValorTotalFaturado(), detalhes, idOperacao, ocorridoEm,
                OffsetDateTime.now()));
    }

    public void registrarNumeroOficial(ExecucaoComercial execucao, String anterior, UUID idAutor) {
        OffsetDateTime agora = OffsetDateTime.now();
        repository.save(new EventoHistoricoNota(execucao.getId(), TipoEventoNota.NUMERO_OFICIAL,
                OrigemAlteracaoNota.ESCRITORIO, idAutor, autor(idAutor), null, null, null,
                DetalhesEventoNota.numeroOficial(anterior, execucao.getReferenciaNota()), null, agora, agora));
    }

    /** A alteração com esse id já chegou antes (reenvio da fila do aparelho). */
    public Optional<UUID> execucaoDaOperacao(UUID idOperacao) {
        return idOperacao == null ? Optional.empty() : repository.findByIdOperacao(idOperacao).map(EventoHistoricoNota::getIdExecucao);
    }

    /** {@code equipe} é o código da equipe, autor do lançamento reconstruído. */
    public List<EventoHistoricoNotaResponse> listar(ExecucaoComercial execucao, String equipe) {
        List<EventoHistoricoNota> eventos = repository.findByIdExecucaoOrderByOcorridoEmAscRegistradoEmAsc(execucao.getId());
        List<EventoHistoricoNotaResponse> linha = new ArrayList<>();
        if (eventos.stream().noneMatch(e -> e.getTipo() == TipoEventoNota.LANCADA)) {
            linha.add(lancamentoReconstruido(execucao, eventos, equipe));
        }
        eventos.forEach(e -> linha.add(new EventoHistoricoNotaResponse(e.getId(), e.getTipo(), e.getOrigem(), e.getAutor(),
                e.getMotivo(), e.getValorAnterior(), e.getValorNovo(), e.getDetalhes(), e.getOcorridoEm(),
                e.getRegistradoEm(), false)));
        return linha;
    }

    /**
     * Parte do que a nota tem hoje e desfaz cada alteração, da mais nova para a mais
     * antiga: o que foi adicionado sai, o que foi removido volta.
     */
    private EventoHistoricoNotaResponse lancamentoReconstruido(ExecucaoComercial execucao, List<EventoHistoricoNota> eventos,
                                                               String equipe) {
        Map<UUID, AcaoRegistrada> acoes = new LinkedHashMap<>();
        execucao.getAcoes().stream().map(ExecucaoAcao::getIdAcao).forEach(id -> acoes.put(id, acao(id)));
        Map<String, MaterialRegistrado> materiais = new LinkedHashMap<>();
        execucao.getMateriais().stream().map(ExecucaoMaterial::comoLancado).map(this::material)
                .forEach(m -> somar(materiais, m, BigDecimal.ONE));

        BigDecimal valorOriginal = execucao.getValorTotalFaturado();
        for (EventoHistoricoNota evento : eventos.reversed()) {
            if (evento.getTipo() != TipoEventoNota.ALTERADA) continue;
            var d = evento.getDetalhes();
            d.acoesAdicionadas().forEach(a -> acoes.remove(a.idAcao()));
            d.acoesRemovidas().forEach(a -> acoes.putIfAbsent(a.idAcao(), a));
            d.materiaisAdicionados().forEach(m -> somar(materiais, m, BigDecimal.ONE.negate()));
            d.materiaisRemovidos().forEach(m -> somar(materiais, m, BigDecimal.ONE));
            if (evento.getValorAnterior() != null) valorOriginal = evento.getValorAnterior();
        }

        var detalhes = new DetalhesEventoNota(List.copyOf(acoes.values()), List.of(),
                materiais.values().stream().filter(m -> m.quantidade().signum() > 0).toList(), List.of(), null, null);
        return new EventoHistoricoNotaResponse(null, TipoEventoNota.LANCADA, OrigemAlteracaoNota.CAMPO,
                equipe == null ? "Equipe de campo" : "Equipe " + equipe, null, null, valorOriginal, detalhes,
                execucao.getDataLancamento(), execucao.getDataCriacao(), true);
    }

    private static void somar(Map<String, MaterialRegistrado> materiais, MaterialRegistrado m, BigDecimal sinal) {
        String chave = m.idMaterial() + "|" + m.operacao() + "|" + m.origem() + "|"
                + (m.numSerie() == null ? "" : m.numSerie().toUpperCase(Locale.ROOT));
        BigDecimal quantidade = m.quantidade().multiply(sinal);
        materiais.merge(chave, comQuantidade(m, quantidade),
                (a, b) -> comQuantidade(a, a.quantidade().add(b.quantidade())));
    }

    private static MaterialRegistrado comQuantidade(MaterialRegistrado m, BigDecimal quantidade) {
        return new MaterialRegistrado(m.idMaterial(), m.codigo(), m.descricao(), m.unidadeMedida(), quantidade,
                m.operacao(), m.numSerie(), m.origem());
    }

    private AcaoRegistrada acao(UUID idAcao) {
        var acao = contratoApi.buscarAcaoPorIdResponse(idAcao);
        return new AcaoRegistrada(idAcao, acao.map(a -> a.codigo()).orElse(null), acao.map(a -> a.nome()).orElse("Serviço removido"));
    }

    private MaterialRegistrado material(MaterialLancado m) {
        Optional<MaterialResponse> material = estoqueApi.buscarMaterialPorIdResponse(m.idMaterial());
        return new MaterialRegistrado(m.idMaterial(),
                material.map(MaterialResponse::codigo).orElse(null),
                material.map(MaterialResponse::descricao).orElse("Material removido"),
                material.map(MaterialResponse::unidadeMedida).orElse(null),
                m.quantidade(), m.operacao(), m.numSerie(), m.origem());
    }

    private String autor(UUID idUsuario) {
        if (idUsuario == null) return "SISTEMA";
        return colaboradorApi.buscarNomeCompletoPorUsuario(idUsuario)
                .filter(n -> !n.isBlank())
                .or(() -> usuarioApi.buscarNomePorId(idUsuario).filter(n -> !n.isBlank()))
                .orElse("Usuário " + idUsuario.toString().substring(0, 8));
    }
}
