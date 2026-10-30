package com.nexo.app.modules.notificacao.application.usecases;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.AvisoAutomacao;
import com.nexo.app.modules.notificacao.GatilhoCatalogado;
import com.nexo.app.modules.notificacao.NotificacaoApi;
import com.nexo.app.modules.notificacao.RegraLegada;
import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.CondicaoRegra;
import com.nexo.app.modules.notificacao.domain.CondicaoRotaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.DestinatarioRegra;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.ModoGatilho;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.domain.RotaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PoliticaRoteamentoNotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;

/** Implementa a API pública do módulo sobre o publicador e as regras existentes. */
@Service
@RequiredArgsConstructor
public class NotificacaoApiAdapter implements NotificacaoApi {

    private final PublicadorNotificacoes publicador;
    private final RegraNotificacaoRepository regras;
    private final PoliticaRoteamentoNotificacaoRepository politicas;

    @Override
    @Transactional
    public int publicarAviso(AvisoAutomacao aviso) {
        CategoriaNotificacao categoria = enumOu(CategoriaNotificacao.class, aviso.categoria(), CategoriaNotificacao.OPERACIONAL);
        PrioridadeNotificacao prioridade = enumOu(PrioridadeNotificacao.class, aviso.prioridade(), PrioridadeNotificacao.NORMAL);
        if (aviso.titulo() == null || aviso.titulo().isBlank()) {
            throw new InvarianteVioladaException("O título do aviso é obrigatório.");
        }
        Map<String, Object> contexto = new LinkedHashMap<>();
        if (aviso.contexto() != null) contexto.putAll(aviso.contexto());
        if (aviso.idAutomacao() != null) contexto.put("idAutomacao", aviso.idAutomacao().toString());
        return publicador.publicar(NovaNotificacao.builder()
                .destinatarios(aviso.destinatarios())
                .origem(OrigemNotificacao.AUTOMACAO)
                .idReferencia(aviso.idReferencia())
                .categoria(categoria)
                .prioridade(prioridade)
                .titulo(cortar(aviso.titulo(), 150))
                .corpo(cortar(aviso.corpo() == null || aviso.corpo().isBlank() ? aviso.titulo() : aviso.corpo(), 1000))
                .deeplink(cortar(aviso.deeplink(), 500))
                .contexto(contexto)
                .chaveIdempotencia(cortar(aviso.chaveIdempotencia(), 200))
                .email(aviso.email())
                .chaveGatilho(aviso.chaveGatilho())
                .build());
    }

    @Override
    public List<GatilhoCatalogado> catalogoGatilhos() {
        List<GatilhoCatalogado> lista = new ArrayList<>();
        for (GatilhoNotificacao g : GatilhoNotificacao.values()) {
            lista.add(new GatilhoCatalogado(g.getChave(), g.getNome(), g.getDescricao(),
                    g.getModo() == ModoGatilho.POR_LIMIAR ? "LIMIAR" : "OCORRENCIA",
                    g.getCategoriaPadrao().name(), g.getVariaveis(), g.getPapeis()));
        }
        return lista;
    }

    @Override
    public Map<String, String> categorias() {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (CategoriaNotificacao c : CategoriaNotificacao.values()) {
            if (c != CategoriaNotificacao.COMUNICADO) mapa.put(c.name(), c.getRotulo());
        }
        return mapa;
    }

    @Override
    public Map<String, String> prioridades() {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (PrioridadeNotificacao p : PrioridadeNotificacao.values()) mapa.put(p.name(), p.getRotulo());
        return mapa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegraLegada> regrasNaoConvertidas() {
        List<RegraLegada> lista = new ArrayList<>();
        for (RegraNotificacao regra : regras.findAllByIdAutomacaoIsNullOrderByDataCriacaoAsc()) {
            List<RegraLegada.Condicao> condicoes = new ArrayList<>();
            for (CondicaoRegra c : regra.condicoesAtivas()) {
                condicoes.add(new RegraLegada.Condicao(c.getCampo(), c.getOperador().name(), c.getValor()));
            }
            List<RegraLegada.Destinatario> destinatarios = new ArrayList<>();
            for (DestinatarioRegra d : regra.destinatariosAtivos()) {
                RegraLegada.Politica politica = null;
                if (d.getTipo() == TipoDestinatarioRegra.POLITICA && d.getIdAlvo() != null) {
                    politica = politicas.findById(d.getIdAlvo()).map(this::politica).orElse(null);
                }
                destinatarios.add(new RegraLegada.Destinatario(d.getTipo().name(), d.getIdAlvo(), d.getValorAlvo(), politica));
            }
            lista.add(new RegraLegada(regra.getId(), regra.getChaveGatilho(), regra.getNome(), regra.getDescricao(),
                    regra.isAtivo(), regra.isSistema(), regra.getCategoria().name(), regra.getPrioridade().name(),
                    regra.getTituloTemplate(), regra.getCorpoTemplate(), regra.getDeeplinkTemplate(), regra.getLimiar(),
                    regra.getCooldownMinutos(), regra.isEnviarEmail(), condicoes, destinatarios));
        }
        return lista;
    }

    private RegraLegada.Politica politica(PoliticaRoteamentoNotificacao p) {
        List<RegraLegada.Rota> rotas = new ArrayList<>();
        for (RotaRoteamentoNotificacao r : p.rotasAtivasOrdenadas()) {
            List<RegraLegada.Condicao> condicoes = new ArrayList<>();
            for (CondicaoRotaRoteamentoNotificacao c : r.condicoesAtivas()) {
                condicoes.add(new RegraLegada.Condicao(c.getCampo(), c.getOperador().name(), c.getValor()));
            }
            rotas.add(new RegraLegada.Rota(r.getOrdem(), r.isPadrao(), r.getUsuarioId(), condicoes));
        }
        return new RegraLegada.Politica(p.getId(), p.getNome(), rotas);
    }

    @Override
    @Transactional
    public void marcarRegraConvertida(UUID idRegra, UUID idAutomacao) {
        RegraNotificacao regra = regras.findById(idRegra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra de notificação não encontrada."));
        regra.converterEmAutomacao(idAutomacao);
        regras.save(regra);
    }

    private static <E extends Enum<E>> E enumOu(Class<E> tipo, String nome, E padrao) {
        if (nome == null || nome.isBlank()) return padrao;
        try {
            return Enum.valueOf(tipo, nome.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }

    private static String cortar(String texto, int limite) {
        if (texto == null) return null;
        String t = texto.strip();
        return t.length() > limite ? t.substring(0, limite - 1) + "…" : t;
    }
}
