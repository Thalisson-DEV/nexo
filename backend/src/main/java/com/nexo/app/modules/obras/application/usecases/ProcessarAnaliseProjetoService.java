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

import java.util.List;
import java.util.UUID;

/**
 * Lê a página do projeto com o modelo de visão, fora de transação, porque cada bloco pode levar dezenas de segundos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarAnaliseProjetoService {

    static final String INSTRUCOES = """
            Você lê projetos de rede de distribuição elétrica desenhados em CAD. A imagem é parte de uma folha.
            Liste só o que está legível no desenho, sem deduzir nem completar.

            Pontos: postes, transformadores, chaves e outras estruturas numeradas. Em geral há um balão circular
            ligado ao símbolo por uma linha, com o número do ponto, o poste (ex.: 11/400, 9/400) e as estruturas
            (ex.: N1, IM1, IMFR). Identificadores como K051159 ou B053427 perto do símbolo são o barramento.
            Vãos: trechos de rede entre dois pontos numerados, com a metragem escrita ao lado (ex.: 34,3m) e o
            condutor (ex.: 1x25+1x25mm², 3#4CAA).

            x e y são a posição do símbolo do ponto na imagem, não a do balão: de 0 a 1, a partir do canto superior
            esquerdo. confianca vai de 0 a 1. Ignore carimbo, legenda, marcas d'água e coordenadas X= Y=.

            Responda apenas com JSON, sem comentários:
            {"pontos":[{"numero":"4","tipo":"POSTE|TRANSFORMADOR|CHAVE|ESTRUTURA|OUTRO","x":0.5,"y":0.5,
            "barramento":null,"poste":"9/400","estruturas":["IM1"],"confianca":0.9}],
            "vaos":[{"de":"2","para":"4","metragem":34.3,"condutor":"1x25+1x25mm²","confianca":0.8}]}
            """;

    private final AnalisePlanejamentoRepository analiseRepository;
    private final AnexoObraUseCases anexoUseCases;
    private final RenderizadorPaginaProjeto renderizador;
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
        List<RenderizadorPaginaProjeto.Bloco> blocos = renderizador.renderizar(pdf, analise.getPagina());

        InterpretadorPropostaProjeto interpretador = new InterpretadorPropostaProjeto(analise.getPagina(),
                pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(analise.getIdObra()).stream()
                        .map(PontoPlanejamento::getNumero).toList(),
                catalogo(analise.getIdObra()));
        for (RenderizadorPaginaProjeto.Bloco bloco : blocos) {
            String resposta = copiloto.completarComImagens(INSTRUCOES, "Liste os pontos e vãos desta parte da folha.",
                    List.of(new CopilotoApi.Imagem("image/jpeg", bloco.base64Jpeg())), 0.0, 4096);
            interpretador.adicionarBloco(resposta, bloco);
        }
        return interpretador.concluir();
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
