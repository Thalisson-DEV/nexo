package com.nexo.app.modules.obras.application.usecases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento.KitSugerido;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento.PontoSugerido;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento.VaoSugerido;
import com.nexo.app.modules.obras.domain.TipoPontoPlanejamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte a resposta do modelo em proposta: traz as coordenadas do bloco para a página, une o que apareceu em
 * mais de um bloco e procura os kits no catálogo. Kit nunca vem do modelo: ele só informa o texto lido na folha.
 */
final class InterpretadorPropostaProjeto {

    private static final int MAXIMO_KITS_POR_TEXTO = 3;
    private static final Pattern BITOLA = Pattern.compile("\\d+\\s*[xX×#]\\s*\\d+");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Kit do catálogo do contrato, só com o necessário para a busca. */
    record KitCatalogo(UUID id, String codigo, String descricao, String grupo) {
    }

    private final int pagina;
    private final Set<String> numerosExistentes;
    private final List<KitCatalogo> catalogo;
    private final Map<String, Lido> pontos = new LinkedHashMap<>();
    private final Map<String, VaoLido> vaos = new LinkedHashMap<>();
    private final Set<String> avisos = new LinkedHashSet<>();

    private record Lido(String numero, TipoPontoPlanejamento tipo, double x, double y, String barramento,
                        List<String> textos, double confianca) {
    }

    private record VaoLido(String a, String b, BigDecimal metragem, String condutor, double confianca) {
    }

    InterpretadorPropostaProjeto(int pagina, Collection<String> numerosExistentes, List<KitCatalogo> catalogo) {
        this.pagina = pagina;
        this.numerosExistentes = new LinkedHashSet<>(numerosExistentes);
        this.catalogo = catalogo;
    }

    void adicionarBloco(String resposta, RenderizadorPaginaProjeto.Bloco bloco) {
        JsonNode raiz = lerJson(resposta);
        if (raiz == null) {
            avisos.add("Parte da folha não pôde ser interpretada e ficou de fora.");
            return;
        }
        int semNumero = 0;
        for (JsonNode no : raiz.path("pontos")) {
            String numero = texto(no, "numero");
            if (numero == null || numero.length() > 20) {
                semNumero++;
                continue;
            }
            numero = PontoPlanejamento.normalizarNumero(numero);
            double x = limitar(bloco.inicioX() + no.path("x").asDouble(0.5) * bloco.largura());
            double y = limitar(bloco.inicioY() + no.path("y").asDouble(0.5) * bloco.altura());
            List<String> textos = new ArrayList<>();
            adicionar(textos, texto(no, "poste"));
            for (JsonNode estrutura : no.path("estruturas")) {
                adicionar(textos, estrutura.asText(null));
            }
            Lido novo = new Lido(numero, tipo(texto(no, "tipo")), x, y, texto(no, "barramento"), textos,
                    limitarConfianca(no.path("confianca").asDouble(0.5)));
            pontos.merge(numero, novo, InterpretadorPropostaProjeto::unir);
        }
        if (semNumero > 0) {
            avisos.add("Há pontos na folha sem número legível: confira e marque manualmente.");
        }
        for (JsonNode no : raiz.path("vaos")) {
            String de = texto(no, "de");
            String para = texto(no, "para");
            if (de == null || para == null) {
                continue;
            }
            String a = PontoPlanejamento.normalizarNumero(de);
            String b = PontoPlanejamento.normalizarNumero(para);
            if (a.equals(b)) {
                continue;
            }
            String chave = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
            VaoLido novo = new VaoLido(a, b, metragem(no.path("metragem")), texto(no, "condutor"),
                    limitarConfianca(no.path("confianca").asDouble(0.5)));
            vaos.merge(chave, novo, (atual, outro) -> outro.confianca() > atual.confianca() ? outro : atual);
        }
    }

    PropostaPlanejamento concluir() {
        List<PontoSugerido> sugeridos = pontos.values().stream()
                .map(p -> new PontoSugerido(p.numero(), p.numero(), p.tipo(), pagina, coordenada(p.x()), coordenada(p.y()),
                        p.barramento(), p.textos(), p.confianca(), numerosExistentes.contains(p.numero()),
                        kits(p.textos(), false)))
                .toList();

        List<VaoSugerido> vaosSugeridos = new ArrayList<>();
        for (VaoLido vao : vaos.values()) {
            boolean conhecido = conhecido(vao.a()) && conhecido(vao.b());
            if (!conhecido) {
                avisos.add("O vão " + vao.a() + " — " + vao.b() + " cita um ponto que não foi encontrado e ficou de fora.");
                continue;
            }
            List<String> textos = new ArrayList<>();
            adicionar(textos, vao.condutor());
            vaosSugeridos.add(new VaoSugerido(vao.a(), vao.b(), vao.metragem(), textos, vao.confianca(),
                    kits(textos, true)));
        }
        return new PropostaPlanejamento(sugeridos, vaosSugeridos, List.copyOf(avisos));
    }

    private boolean conhecido(String numero) {
        return pontos.containsKey(numero) || numerosExistentes.contains(numero);
    }

    /** O kit só vem marcado quando o texto lido aponta para um único kit do catálogo. */
    private List<KitSugerido> kits(List<String> textos, boolean paraVao) {
        Map<UUID, KitSugerido> achados = new LinkedHashMap<>();
        for (String texto : textos) {
            for (String termo : termos(texto, paraVao)) {
                List<KitCatalogo> candidatos = catalogo.stream()
                        .filter(k -> paraVao == kitDeVao(k))
                        .filter(k -> normalizar(k.codigo() + " " + k.descricao()).contains(termo))
                        .sorted(Comparator.comparing(KitCatalogo::codigo))
                        .toList();
                boolean unico = candidatos.size() == 1;
                candidatos.stream().limit(MAXIMO_KITS_POR_TEXTO).forEach(k -> achados.putIfAbsent(k.id(),
                        new KitSugerido(k.id(), k.codigo(), k.descricao(), texto, unico)));
            }
        }
        return List.copyOf(achados.values());
    }

    private static boolean kitDeVao(KitCatalogo kit) {
        String grupo = normalizar(kit.grupo() != null ? kit.grupo() : "");
        return grupo.startsWith("VAO") || normalizar(kit.descricao()).contains("CABO");
    }

    private static List<String> termos(String texto, boolean paraVao) {
        if (paraVao) {
            Matcher bitola = BITOLA.matcher(texto);
            Set<String> bitolas = new LinkedHashSet<>();
            while (bitola.find()) {
                bitolas.add(normalizar(bitola.group()));
            }
            if (!bitolas.isEmpty()) {
                return List.of(bitolas.iterator().next());
            }
        }
        String termo = normalizar(texto);
        return termo.length() >= 2 ? List.of(termo) : List.of();
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replace('×', 'X')
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static Lido unir(Lido atual, Lido outro) {
        Lido base = outro.confianca() > atual.confianca() ? outro : atual;
        Set<String> textos = new LinkedHashSet<>(atual.textos());
        textos.addAll(outro.textos());
        String barramento = base.barramento() != null ? base.barramento()
                : atual.barramento() != null ? atual.barramento() : outro.barramento();
        return new Lido(base.numero(), base.tipo(), base.x(), base.y(), barramento, List.copyOf(textos), base.confianca());
    }

    private static JsonNode lerJson(String resposta) {
        if (resposta == null) {
            return null;
        }
        int inicio = resposta.indexOf('{');
        int fim = resposta.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) {
            return null;
        }
        try {
            return JSON.readTree(resposta.substring(inicio, fim + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText("").trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void adicionar(List<String> textos, String texto) {
        if (texto != null && !texto.isBlank() && !textos.contains(texto.trim())) {
            textos.add(texto.trim());
        }
    }

    private static TipoPontoPlanejamento tipo(String texto) {
        if (texto == null) {
            return TipoPontoPlanejamento.POSTE;
        }
        try {
            return TipoPontoPlanejamento.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TipoPontoPlanejamento.OUTRO;
        }
    }

    private static BigDecimal metragem(JsonNode no) {
        if (no.isNumber() && no.asDouble() > 0) {
            return no.decimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        if (no.isTextual()) {
            try {
                BigDecimal valor = new BigDecimal(no.asText().replace(",", ".").replaceAll("[^0-9.]", ""));
                return valor.signum() > 0 ? valor.setScale(2, RoundingMode.HALF_UP) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal coordenada(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP);
    }

    private static double limitar(double valor) {
        return Math.max(0d, Math.min(1d, valor));
    }

    private static double limitarConfianca(double valor) {
        return limitar(valor);
    }
}
