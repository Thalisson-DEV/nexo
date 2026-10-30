package com.nexo.app.modules.notificacao.application.usecases;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.PoliticaObservabilidadeResponse;
import com.nexo.app.modules.notificacao.application.dto.PoliticaObservabilidadeResponse.Condicao;
import com.nexo.app.modules.notificacao.application.dto.PoliticaObservabilidadeResponse.Destinatario;
import com.nexo.app.modules.notificacao.application.dto.PoliticaObservabilidadeResponse.Regra;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.CondicaoRegra;
import com.nexo.app.modules.notificacao.domain.DestinatarioRegra;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import lombok.RequiredArgsConstructor;

/**
 * Publica a política de alerta em um contrato interno autenticado.
 *
 * <p>A leitura usa o mesmo resolvedor e as mesmas preferências do motor normal,
 * mas não publica notificações nem depende de uma fila. O resultado é uma visão
 * completa para o adapter externo, com audiência deduplicada e versão que muda
 * quando uma regra, endereço ou canal elegível muda.</p>
 */
@Service
@RequiredArgsConstructor
public class PoliticaObservabilidadeUseCases {

    public static final String CHAVE_GATILHO = "observability.incidente_aberto";
    public static final String VERSAO_SCHEMA = "1";
    private static final String PERMISSAO_AUDIENCIA = "chamado:manage";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Set<String> CAMPOS_OFFLINE = Set.of(
            "ambiente", "componente", "estadoInicial", "resumo", "linkStatus", "runbook");
    private static final Set<OperadorCondicao> OPERADORES_OFFLINE = Set.of(
            OperadorCondicao.IGUAL, OperadorCondicao.DIFERENTE, OperadorCondicao.CONTEM);
    private static final Comparator<Destinatario> DESTINATARIO_ORDER = Comparator
            .comparing(Destinatario::email, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Destinatario::userId);

    private final RegraNotificacaoRepository regraRepository;
    private final ResolvedorDestinatariosRegra resolvedorDestinatarios;
    private final PreferenciaNotificacaoUseCases preferencias;
    private final UsuarioApi usuarioApi;

    @Transactional(readOnly = true)
    public PoliticaObservabilidadeResponse projetar() {
        List<Regra> regras = regraRepository
                .findAllByChaveGatilhoOrderByIdAsc(CHAVE_GATILHO)
                .stream()
                .map(this::projetarRegra)
                .toList();

        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String version = versaoDe(regras);
        return new PoliticaObservabilidadeResponse(
                VERSAO_SCHEMA,
                version,
                generatedAt,
                generatedAt.plusHours(24),
                CHAVE_GATILHO,
                regras);
    }

    private Regra projetarRegra(RegraNotificacao regra) {
        validarRegraOffline(regra);

        boolean emailEnabled = regra.isAtivo()
                && regra.isEnviarEmail() && regra.getCategoria().permiteEmail();
        List<Destinatario> audience = emailEnabled
                ? resolverAudiencia(regra)
                : List.of();

        return new Regra(
                regra.getId(),
                regra.getNome(),
                regra.getCategoria(),
                regra.getPrioridade(),
                regra.isAtivo(),
                emailEnabled,
                regra.getTituloTemplate(),
                regra.getCorpoTemplate(),
                regra.getDeeplinkTemplate(),
                regra.condicoesAtivas().stream()
                        .map(c -> new Condicao(c.getCampo(), c.getOperador(), c.getValor()))
                        .toList(),
                audience);
    }

    private List<Destinatario> resolverAudiencia(RegraNotificacao regra) {
        Set<UUID> ids = resolvedorDestinatarios.resolver(regra, Map.of(), Map.of());
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> emails = usuarioApi.buscarEmailsPorIds(ids);
        Map<String, Destinatario> deduplicados = new LinkedHashMap<>();
        for (UUID id : ids) {
            String email = emails.get(id);
            if (!emailElegivel(email) || !preferenciaEmailAtiva(id)) {
                continue;
            }
            String normalizado = email.trim().toLowerCase(Locale.ROOT);
            deduplicados.putIfAbsent(normalizado, new Destinatario(id, email.trim()));
        }
        return deduplicados.values().stream().sorted(DESTINATARIO_ORDER).toList();
    }

    private boolean preferenciaEmailAtiva(UUID idUsuario) {
        return preferencias.listar(idUsuario).stream()
                .filter(preferencia -> preferencia.categoria() == CategoriaNotificacao.SISTEMA)
                .anyMatch(preferencia -> preferencia.permiteEmail() && preferencia.email());
    }

    private void validarRegraOffline(RegraNotificacao regra) {
        if (!CHAVE_GATILHO.equals(regra.getChaveGatilho())
                || regra.exigirGatilho() != GatilhoNotificacao.OBSERVABILITY_INCIDENTE_ABERTO) {
            throw new InvarianteVioladaException(
                    "A política de observabilidade aponta para um gatilho incompatível.");
        }
        if (regra.getModo() != com.nexo.app.modules.notificacao.domain.ModoGatilho.POR_OCORRENCIA) {
            throw new InvarianteVioladaException(
                    "A política de observabilidade precisa usar um gatilho por ocorrência.");
        }
        if (regra.getCategoria() != CategoriaNotificacao.SISTEMA) {
            throw new InvarianteVioladaException(
                    "A política de observabilidade precisa usar a categoria SISTEMA.");
        }

        List<DestinatarioRegra> destinatarios = regra.destinatariosAtivos();
        if (destinatarios.size() != 1
                || destinatarios.getFirst().getTipo() != TipoDestinatarioRegra.PERMISSAO
                || !PERMISSAO_AUDIENCIA.equalsIgnoreCase(destinatarios.getFirst().getValorAlvo())) {
            throw new InvarianteVioladaException(
                    "A política de observabilidade deve endereçar somente a permissão chamado:manage.");
        }

        for (CondicaoRegra condicao : regra.condicoesAtivas()) {
            if (!CAMPOS_OFFLINE.contains(condicao.getCampo())
                    || !OPERADORES_OFFLINE.contains(condicao.getOperador())) {
                throw new InvarianteVioladaException(
                        "A condição '" + condicao.getCampo() + "'/'" + condicao.getOperador()
                                + "' não é suportada pela avaliação offline de observabilidade.");
            }
        }
        validarTemplates(regra);
    }

    private void validarTemplates(RegraNotificacao regra) {
        GatilhoNotificacao gatilho = regra.exigirGatilho();
        List<String> templates = new ArrayList<>(List.of(
                regra.getTituloTemplate(), regra.getCorpoTemplate()));
        if (regra.getDeeplinkTemplate() != null) {
            templates.add(regra.getDeeplinkTemplate());
        }
        for (String template : templates) {
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                if (!gatilho.declaraVariavel(matcher.group(1))) {
                    throw new InvarianteVioladaException(
                            "O template da política usa a variável offline não declarada '"
                                    + matcher.group(1) + "'.");
                }
            }
        }
    }

    private static boolean emailElegivel(String email) {
        return StringUtils.hasText(email)
                && !email.contains("\r")
                && !email.contains("\n")
                && email.contains("@");
    }

    private static String versaoDe(List<Regra> regras) {
        StringBuilder material = new StringBuilder(VERSAO_SCHEMA).append('|').append(CHAVE_GATILHO);
        for (Regra regra : regras) {
            material.append('|').append(regra.id()).append('|').append(regra.name())
                    .append('|').append(regra.category()).append('|').append(regra.priority())
                    .append('|').append(regra.enabled()).append('|').append(regra.emailEnabled())
                    .append('|').append(regra.titleTemplate()).append('|').append(regra.bodyTemplate())
                    .append('|').append(regra.deeplinkTemplate());
            for (Condicao condicao : regra.conditions()) {
                material.append('|').append(condicao.field()).append('|').append(condicao.operator())
                        .append('|').append(condicao.value());
            }
            for (Destinatario destinatario : regra.audience()) {
                material.append('|').append(destinatario.userId()).append('|').append(destinatario.email());
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não está disponível na JVM.", e);
        }
    }
}
