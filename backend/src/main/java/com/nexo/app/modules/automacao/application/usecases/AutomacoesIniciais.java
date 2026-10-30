package com.nexo.app.modules.automacao.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexo.app.modules.automacao.application.dto.AutomacaoRequest;
import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.OrigemAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.modules.iam.UsuarioApi;

/**
 * Automações que o Nexo já entrega montadas, em rascunho: falta só dizer quem recebe e
 * ativar. São criadas uma única vez (pelo nome); apagar ou renomear não faz voltar.
 */
@Component
public class AutomacoesIniciais {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomacoesIniciais.class);

    private final ModelosAutomacao modelos;
    private final AutomacaoRepository automacoes;
    private final AutomacaoUseCases useCases;
    private final UsuarioApi usuarios;
    private final TransactionTemplate transacao;

    public AutomacoesIniciais(ModelosAutomacao modelos, AutomacaoRepository automacoes, AutomacaoUseCases useCases,
                              UsuarioApi usuarios, TransactionTemplate transacao) {
        this.modelos = modelos;
        this.automacoes = automacoes;
        this.useCases = useCases;
        this.usuarios = usuarios;
        this.transacao = transacao;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void criarNaInicializacao() {
        try {
            criarRelatorioComercial();
            atualizarRelatorioComercial();
        } catch (RuntimeException e) {
            LOGGER.error("Não foi possível criar as automações iniciais", e);
        }
    }

    void criarRelatorioComercial() {
        if (automacoes.existsByNome(ModelosAutomacao.RELATORIO_COMERCIAL_NOME)) return;
        Optional<UUID> dono = responsavelPadrao();
        if (dono.isEmpty()) {
            LOGGER.warn("Nenhum usuário com 'automacao:manage' para ser responsável pelo relatório comercial; criação adiada.");
            return;
        }
        ModelosAutomacao.Modelo modelo = modelos.relatorioComercialPorBase();
        transacao.executeWithoutResult(s -> useCases.criarInterno(new AutomacaoRequest(modelo.nome(), modelo.descricao(), modelo.tipo(),
                modelo.definicao(), "Criada pelo Nexo: falta indicar quem recebe cada base"), OrigemAutomacao.SISTEMA, dono.get(), null));
        LOGGER.info("Automação '{}' criada em rascunho.", modelo.nome());
    }

    /**
     * A primeira versão do relatório deixava o Copiloto buscar tudo sozinho e estourava o
     * limite de etapas. A atual traz os números em consultas prontas. Automações do relatório
     * ainda na versão antiga ganham a nova definição, mantendo o que a pessoa configurou:
     * quem recebe cada base, a lista de bases, a agenda e o aviso de falha.
     */
    void atualizarRelatorioComercial() {
        for (Automacao automacao : automacoes.findByNomeAndAtivoTrue(ModelosAutomacao.RELATORIO_COMERCIAL_NOME)) {
            if (automacao.getOrigem() != OrigemAutomacao.SISTEMA) continue;
            Map<String, Object> atual = useCases.versao(automacao.getId(), automacao.getNumeroVersao());
            if (Valores.paraJson(atual).contains("\"" + ModelosAutomacao.RELATORIO_COMERCIAL_MARCA + "\"")) continue;
            Map<String, Object> nova = Valores.mapa(modelos.relatorioComercialPorBase().definicao());
            manterConfiguracao(atual, nova);
            transacao.executeWithoutResult(s -> useCases.novaVersaoInterna(automacao.getId(), nova,
                    "Atualizada pelo Nexo: números da semana em consultas prontas (destinatários mantidos)"));
            LOGGER.info("Automação '{}' atualizada para as consultas prontas.", automacao.getNome());
        }
    }

    @SuppressWarnings("unchecked")
    static void manterConfiguracao(Map<String, Object> atual, Map<String, Object> nova) {
        Map<String, Object> rotasAtuais = buscarNo(Valores.lista(atual.get("steps")), "quem_recebe");
        Map<String, Object> rotasNovas = buscarNo(Valores.lista(nova.get("steps")), "quem_recebe");
        if (rotasAtuais != null && rotasNovas != null) {
            Map<String, Object> de = Valores.mapa(rotasAtuais.get("params"));
            Map<String, Object> para = (Map<String, Object>) rotasNovas.get("params");
            for (String chave : List.of("routes", "fallback")) {
                if (de.containsKey(chave)) para.put(chave, de.get(chave));
            }
        }
        if (atual.get("variables") != null) nova.put("variables", atual.get("variables"));
        if (atual.get("trigger") != null) nova.put("trigger", atual.get("trigger"));
        if (atual.get("settings") != null) nova.put("settings", atual.get("settings"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buscarNo(List<Object> passos, String id) {
        for (Object bruto : passos) {
            if (!(bruto instanceof Map<?, ?> no)) continue;
            if (id.equals(no.get("id"))) return (Map<String, Object>) no;
            for (String filhos : List.of("steps", "then", "else", "default")) {
                Map<String, Object> achado = buscarNo(Valores.lista(no.get(filhos)), id);
                if (achado != null) return achado;
            }
        }
        return null;
    }

    private Optional<UUID> responsavelPadrao() {
        for (String permissao : List.of("automacao:manage", "notificacao:regra:manage")) {
            List<UUID> ids = usuarios.buscarIdsAtivosComPermissao(permissao);
            if (!ids.isEmpty()) return Optional.of(ids.get(0));
        }
        return Optional.empty();
    }
}
