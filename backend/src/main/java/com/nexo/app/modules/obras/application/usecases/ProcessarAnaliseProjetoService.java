package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.obras.domain.AnalisePlanejamento;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento;
import com.nexo.app.modules.obras.infrastructure.persistence.AnalisePlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitConstrutivoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lê a página do projeto com o modelo de visão, fora de transação, porque cada bloco pode levar dezenas de segundos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarAnaliseProjetoService {

    static final String INSTRUCOES = """
            Você lê projetos de rede de distribuição elétrica. A imagem é uma folha ou parte dela.
            Liste só o que está visível no desenho, sem deduzir nem completar.

            Pontos: postes, transformadores, chaves e outras estruturas. O símbolo costuma ser um quadrado pequeno na
            rede, com um código ao lado (ex.: D381272) ou um balão com número, poste (ex.: 11/400) e estruturas (ex.: N1,
            IM1). Códigos como D381272 são o barramento; só preencha numero se houver um número de ponto escrito.
            Vãos: trechos de rede entre dois pontos, seguindo as linhas coloridas de um símbolo ao outro. Não ligue pontos
            de redes diferentes nem cruze linhas que só passam perto. Quando escrita, informe a metragem (ex.: 34,3m) e o
            condutor (ex.: 2#CAA 4 AWG, 1x25+1x25mm²).

            x e y são o centro do símbolo na imagem, não o do texto: de 0 a 1, a partir do canto superior esquerdo.
            confianca vai de 0 a 1. Ignore carimbo, legenda, marcas d'água, coordenadas X= Y= e áreas verdes do mapa.
            Se o carimbo trouxer a escala (ex.: ESCALA 1:1.000), informe o denominador em escala.

            Responda apenas com JSON, sem comentários:
            {"escala":1000,"pontos":[{"id":null,"numero":null,"tipo":"POSTE|TRANSFORMADOR|CHAVE|ESTRUTURA|OUTRO",
            "x":0.5,"y":0.5,"barramento":"D381272","poste":"9/400","estruturas":["IM1"],"confianca":0.9}],
            "vaos":[{"de":"D381272","para":"D381271","metragem":null,"condutor":"2#CAA 4 AWG","confianca":0.8}]}
            """;

    private static final int LIMITE_TOKENS = 16384;

    private final AnalisePlanejamentoRepository analiseRepository;
    private final AnexoObraUseCases anexoUseCases;
    private final RenderizadorPaginaProjeto renderizador;
    private final LeitorCamadaProjeto leitorCamada;
    private final CopilotoApi copiloto;
    private final ObraRepository obraRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final KitConstrutivoRepository kitRepository;

    @Async
    public void processar(UUID idAnalise) {
        AnalisePlanejamento analise = analiseRepository.findById(idAnalise).orElse(null);
        if (analise == null) {
            return;
        }
        try {
            analise.concluir(ler(analise));
        } catch (Exception e) {
            log.warn("Falha na análise do projeto {}", idAnalise, e);
            analise.falhar("Não foi possível ler esta página do projeto. Tente de novo ou planeje manualmente.");
        }
        analiseRepository.save(analise);
    }

    private PropostaPlanejamento ler(AnalisePlanejamento analise) throws Exception {
        byte[] pdf;
        try (var stream = anexoUseCases.baixar(analise.getIdObra(), analise.getIdAnexo()).stream()) {
            pdf = stream.readAllBytes();
        }
        List<PontoPlanejamento> existentes = pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(analise.getIdObra());
        Map<String, String> barramentos = new HashMap<>();
        existentes.stream()
                .filter(p -> p.getBarramento() != null)
                .forEach(p -> barramentos.put(p.getBarramento().toUpperCase(Locale.ROOT), p.getNumero()));
        InterpretadorPropostaProjeto interpretador = new InterpretadorPropostaProjeto(analise.getPagina(),
                existentes.stream().map(PontoPlanejamento::getNumero).toList(), barramentos, catalogo(analise.getIdObra()));

        LeitorCamadaProjeto.CamadaPagina camada = leitorCamada.ler(pdf, analise.getPagina());
        interpretador.adicionarCamada(camada);
        if (camada.candidatos().isEmpty()) {
            lerPorBlocos(pdf, analise.getPagina(), interpretador);
        } else {
            lerComCandidatos(pdf, analise.getPagina(), interpretador);
        }
        return interpretador.concluir();
    }

    /** Sem texto no PDF: tudo vem do modelo, por blocos, para os balões continuarem legíveis. */
    private void lerPorBlocos(byte[] pdf, int pagina, InterpretadorPropostaProjeto interpretador) {
        for (RenderizadorPaginaProjeto.Bloco bloco : renderizador.renderizar(pdf, pagina)) {
            lerBloco("Liste os pontos e vãos desta parte da folha.", bloco, interpretador);
        }
    }

    /**
     * Com texto no PDF, os pontos já são conhecidos: o modelo vê a folha inteira para achar os símbolos e seguir as
     * ligações. Se ele falhar, o que foi lido do PDF continua valendo.
     */
    private void lerComCandidatos(byte[] pdf, int pagina, InterpretadorPropostaProjeto interpretador) {
        RenderizadorPaginaProjeto.Bloco folha = renderizador.renderizarInteira(pdf, pagina);
        String pergunta = """
                Estes pontos já foram lidos do texto do PDF:
                %s
                Devolva cada um deles em pontos, com o código em id e o x e y do símbolo. Os ids CX são caixas ou balões
                de texto: se estiver ligado por uma linha a um ponto da lista, devolva pertence_a com o id desse ponto; se
                for de um poste sem código, devolva o x e y do símbolo dele. Se o balão tiver o número do ponto, informe em
                numero. Inclua também os pontos visíveis que não estão na lista. Nos vãos, use em de e para o id do ponto.
                """.formatted(interpretador.descreverCandidatos());
        try {
            lerBloco(pergunta, folha, interpretador);
        } catch (Exception e) {
            log.warn("Modelo indisponível na leitura do projeto; seguindo só com o texto do PDF", e);
            interpretador.avisar("O Copiloto não conseguiu analisar o desenho: vieram só os pontos lidos do texto do PDF, sem vãos.");
        }
    }

    /** Tenta primeiro com raciocínio, mais preciso. Se a resposta vier vazia ou fora do formato, repete sem ele. */
    private void lerBloco(String pergunta, RenderizadorPaginaProjeto.Bloco bloco, InterpretadorPropostaProjeto interpretador) {
        String resposta = perguntar(pergunta, bloco, true);
        if (interpretador.tentarBloco(resposta, bloco)) {
            return;
        }
        log.warn("Resposta fora do formato na leitura do projeto ({} caracteres); tentando sem raciocínio", resposta != null ? resposta.length() : 0);
        interpretador.adicionarBloco(perguntar(pergunta + "\nResponda somente com o JSON, sem texto antes ou depois.", bloco, false), bloco);
    }

    private String perguntar(String pergunta, RenderizadorPaginaProjeto.Bloco bloco, boolean comRaciocinio) {
        return copiloto.completarComImagens(INSTRUCOES, pergunta,
                List.of(new CopilotoApi.Imagem("image/jpeg", bloco.base64Jpeg())), 0.0, LIMITE_TOKENS, comRaciocinio);
    }

    private List<InterpretadorPropostaProjeto.KitCatalogo> catalogo(UUID idObra) {
        UUID idContrato = obraRepository.findById(idObra).map(o -> o.getIdContrato()).orElse(null);
        if (idContrato == null) {
            return List.of();
        }
        return kitRepository.findByIdContratoAndAtivoTrue(idContrato).stream()
                .map(k -> new InterpretadorPropostaProjeto.KitCatalogo(k.getId(), k.getCodigo(), k.getDescricao(), k.getGrupo()))
                .toList();
    }
}
