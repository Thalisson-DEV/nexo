package com.nexo.app.modules.mencao.application.usecases;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.iam.ResumoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.mencao.ContextoMencao;
import com.nexo.app.modules.mencao.MencaoApi;
import com.nexo.app.modules.mencao.UsuarioMencionavel;
import com.nexo.app.modules.mencao.domain.Mencao;
import com.nexo.app.modules.mencao.domain.OrigemMencao;
import com.nexo.app.modules.mencao.infrastructure.persistence.MencaoRepository;
import com.nexo.app.shared.domain.events.UsuarioMencionadoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MencaoUseCases implements MencaoApi {

    /**
     * O que conta como marcação. As duas âncoras negativas são o que separa
     * {@code @THALISSON} de um e-mail ({@code fulano@empresa}) e de um login mais longo
     * que o limite do cadastro, que sem elas seria truncado nos 9 primeiros caracteres
     * e casaria com a pessoa errada.
     */
    private static final Pattern MARCACAO =
            Pattern.compile("(?<![A-Za-z0-9_@])@([A-Za-z0-9_]{3,9})(?![A-Za-z0-9_])");

    private static final int LIMITE_TRECHO = 200;
    private static final int LIMITE_SUGESTOES = 10;

    /**
     * Teto de pessoas por texto. Uma mensagem que marca a empresa inteira vira uma
     * notificação por pessoa, e o caminho legítimo para falar com todos é o comunicado
     * no mural — não uma marcação em massa dentro de um chamado.
     */
    private static final int LIMITE_MARCACOES = 10;

    private final MencaoRepository repository;
    private final UsuarioApi usuarioApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public List<UUID> registrar(ContextoMencao contexto, String texto) {
        if (contexto == null) {
            throw new InvarianteVioladaException("Não foi possível identificar a origem da marcação.");
        }
        List<String> logins = extrairLogins(texto);
        if (logins.isEmpty()) {
            return List.of();
        }

        Map<String, UUID> porLogin = usuarioApi.buscarIdsAtivosPorLogins(logins);
        Map<String, UUID> marcados = new LinkedHashMap<>();
        for (String login : logins) {
            UUID id = porLogin.get(login);
            // Login inexistente ou inativo é erro de digitação, não de operação: some
            // em silêncio. Marcar a si mesmo também não produz nada.
            if (id == null || id.equals(contexto.idAutor())) {
                continue;
            }
            marcados.putIfAbsent(login, id);
        }
        if (marcados.isEmpty()) {
            return List.of();
        }
        if (marcados.size() > LIMITE_MARCACOES) {
            throw new InvarianteVioladaException(
                    "Marque no máximo " + LIMITE_MARCACOES + " pessoas por mensagem.");
        }
        if (contexto.visibilidadeRestrita()) {
            exigirQuePodemVer(contexto.origem(), marcados);
        }

        registrarNovas(contexto, marcados.values());

        String autor = usuarioApi.buscarNomePorId(contexto.idAutor()).orElse("Alguém");
        eventPublisher.publishEvent(new UsuarioMencionadoEvent(
                contexto.idOrigem(),
                contexto.idRegistro(),
                contexto.origem().name(),
                contexto.rotuloRegistro(),
                autor,
                resumir(texto),
                contexto.link(),
                Set.copyOf(marcados.values())));

        return List.copyOf(marcados.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioMencionavel> sugerir(String termo, OrigemMencao origem, boolean visibilidadeRestrita) {
        String trecho = termo == null ? "" : termo.trim();
        if (trecho.startsWith("@")) {
            trecho = trecho.substring(1);
        }
        if (trecho.isBlank()) {
            return List.of();
        }

        boolean filtrar = visibilidadeRestrita && origem != null
                && origem.getPermissaoVisibilidadeRestrita() != null;
        // Com filtro, busca-se folgado: descartar depois do corte devolveria uma lista
        // curta demais justamente nos casos em que a maioria não tem a permissão.
        List<ResumoUsuario> candidatos = usuarioApi.buscarAtivosPorTrechoDoLogin(
                trecho, filtrar ? LIMITE_SUGESTOES * 4 : LIMITE_SUGESTOES);
        if (!filtrar) {
            return candidatos.stream()
                    .map(usuario -> new UsuarioMencionavel(usuario.id(), usuario.login()))
                    .toList();
        }

        Set<UUID> habilitados = new HashSet<>(
                usuarioApi.buscarIdsAtivosComPermissao(origem.getPermissaoVisibilidadeRestrita()));
        return candidatos.stream()
                .filter(usuario -> habilitados.contains(usuario.id()))
                .limit(LIMITE_SUGESTOES)
                .map(usuario -> new UsuarioMencionavel(usuario.id(), usuario.login()))
                .toList();
    }

    /**
     * Recusa a mensagem inteira, e não só a marcação problemática, porque quem escreveu
     * precisa saber que aquela pessoa não será avisada. Silenciar a marcação daria a
     * impressão de que o recado foi passado.
     */
    private void exigirQuePodemVer(OrigemMencao origem, Map<String, UUID> marcados) {
        String permissao = origem.getPermissaoVisibilidadeRestrita();
        if (permissao == null || permissao.isBlank()) {
            return;
        }
        Set<UUID> habilitados = new HashSet<>(usuarioApi.buscarIdsAtivosComPermissao(permissao));
        List<String> semAcesso = marcados.entrySet().stream()
                .filter(entrada -> !habilitados.contains(entrada.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!semAcesso.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Este texto tem circulação restrita e não pode marcar quem não tem acesso a ele: @"
                            + String.join(", @", semAcesso) + ".");
        }
    }

    /**
     * A guarda de repetição existe porque o registro é o lastro do aviso: gravar duas
     * vezes a mesma marcação criaria duas linhas para um fato só. A notificação em si
     * já é protegida pela idempotência do publicador.
     */
    private void registrarNovas(ContextoMencao contexto, Iterable<UUID> mencionados) {
        Set<UUID> existentes = new HashSet<>();
        repository.findByOrigemAndIdOrigem(contexto.origem(), contexto.idOrigem())
                .forEach(mencao -> existentes.add(mencao.getIdUsuarioMencionado()));

        for (UUID mencionado : mencionados) {
            if (existentes.contains(mencionado)) {
                continue;
            }
            repository.save(new Mencao(contexto.origem(), contexto.idOrigem(),
                    contexto.idRegistro(), contexto.idAutor(), mencionado));
        }
    }

    /** Logins marcados, em maiúsculas e na ordem em que aparecem no texto. */
    private static List<String> extrairLogins(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        List<String> logins = new ArrayList<>();
        Matcher matcher = MARCACAO.matcher(texto);
        while (matcher.find()) {
            logins.add(matcher.group(1).toUpperCase());
        }
        return logins;
    }

    /** O suficiente para a pessoa reconhecer o assunto sem abrir o registro. */
    private static String resumir(String texto) {
        String limpo = texto.replaceAll("\\s+", " ").trim();
        return limpo.length() <= LIMITE_TRECHO ? limpo
                : limpo.substring(0, LIMITE_TRECHO).trim() + "…";
    }
}
