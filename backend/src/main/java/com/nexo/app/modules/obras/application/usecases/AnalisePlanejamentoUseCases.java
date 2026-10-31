package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.AceitarProposta;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.AnaliseResponse;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.DisponibilidadeAnalise;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.NovoPonto;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.PontoAceito;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraRequests.VaoAceito;
import com.nexo.app.modules.obras.application.dto.PlanejamentoObraResponse;
import com.nexo.app.modules.obras.domain.AnalisePlanejamento;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.OrigemPlanejamento;
import com.nexo.app.modules.obras.domain.PlanejamentoObra;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.StatusAnalisePlanejamento;
import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import com.nexo.app.modules.obras.infrastructure.persistence.AnalisePlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Planejamento assistido: pedir a leitura de uma página e gravar só as sugestões que o usuário aceitou, pelos
 * mesmos comandos e invariantes do planejamento manual. O aceite é tudo ou nada.
 */
@Service
@RequiredArgsConstructor
public class AnalisePlanejamentoUseCases {

    private final AnalisePlanejamentoRepository analiseRepository;
    private final PontoPlanejamentoRepository pontoRepository;
    private final PlanejamentoObraUseCases planejamento;
    private final ProcessarAnaliseProjetoService processador;
    private final CopilotoApi copiloto;

    @Transactional(readOnly = true)
    public DisponibilidadeAnalise disponibilidade(UUID idObra, int pagina) {
        AnaliseResponse ultima = analiseRepository
                .findFirstByIdObraAndPaginaAndAtivoTrueOrderByDataCriacaoDesc(idObra, pagina)
                .map(AnalisePlanejamentoUseCases::paraResponse)
                .orElse(null);
        return new DisponibilidadeAnalise(copiloto.visaoDisponivel(), ultima);
    }

    @Transactional
    public AnaliseResponse iniciar(UUID idObra, int pagina) {
        if (!copiloto.visaoDisponivel()) {
            throw new InvarianteVioladaException("A leitura de projetos pelo Copiloto não está configurada neste ambiente.");
        }
        planejamento.buscarObra(idObra);
        PlanejamentoObra dados = planejamento.obterPlanejamento(idObra);
        if (dados.getIdAnexoMestre() == null) {
            throw new InvarianteVioladaException("Anexe o projeto mestre antes de pedir a leitura.");
        }
        AnalisePlanejamento analise = analiseRepository.save(
                new AnalisePlanejamento(idObra, dados.getIdAnexoMestre(), pagina, copiloto.modeloVisao()));
        UUID id = analise.getId();
        // Só processa depois do commit: o processamento roda em outra thread e precisa enxergar o registro.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processador.processar(id);
            }
        });
        return paraResponse(analise);
    }

    @Transactional(readOnly = true)
    public AnaliseResponse buscar(UUID idObra, UUID idAnalise) {
        return paraResponse(buscarAnalise(idObra, idAnalise));
    }

    @Transactional
    public PlanejamentoObraResponse aceitar(UUID idObra, UUID idAnalise, AceitarProposta request) {
        AnalisePlanejamento analise = buscarAnalise(idObra, idAnalise);
        if (analise.getStatus() != StatusAnalisePlanejamento.CONCLUIDA) {
            throw new InvarianteVioladaException("Esta leitura do projeto ainda não terminou.");
        }
        Obra obra = planejamento.buscarObra(idObra);

        Map<String, UUID> pontos = new HashMap<>();
        pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(idObra)
                .forEach(p -> pontos.put(p.getNumero(), p.getId()));
        List<PontoAceito> pontosAceitos = request.pontos() != null ? request.pontos() : List.of();
        for (PontoAceito aceito : pontosAceitos) {
            PontoPlanejamento ponto = planejamento.criarPonto(idObra,
                    new NovoPonto(aceito.numero(), analise.getPagina(), aceito.x(), aceito.y(), aceito.barramento(), aceito.tipo()),
                    OrigemPlanejamento.COPILOTO);
            pontos.put(PontoPlanejamento.normalizarNumero(aceito.referencia()), ponto.getId());
            pontos.put(ponto.getNumero(), ponto.getId());
            aplicarKits(obra, aceito.idsKits(), List.of(ponto.getId()), List.of());
        }

        for (VaoAceito aceito : request.vaos() != null ? request.vaos() : List.<VaoAceito>of()) {
            UUID a = pontos.get(PontoPlanejamento.normalizarNumero(aceito.referenciaA()));
            UUID b = pontos.get(PontoPlanejamento.normalizarNumero(aceito.referenciaB()));
            if (a == null || b == null) {
                throw new InvarianteVioladaException("O vão " + aceito.referenciaA() + " — " + aceito.referenciaB()
                        + " depende de um ponto que não foi aceito.");
            }
            VaoPlanejamento vao = planejamento.criarVao(idObra, a, b, aceito.metragem(), OrigemPlanejamento.COPILOTO);
            aplicarKits(obra, aceito.idsKits(), List.of(), List.of(vao.getId()));
        }
        return planejamento.buscar(idObra);
    }

    private void aplicarKits(Obra obra, List<UUID> idsKits, List<UUID> pontos, List<UUID> vaos) {
        if (idsKits != null) {
            idsKits.forEach(idKit -> planejamento.aplicar(obra, idKit, pontos, vaos, OperacaoItemKit.INSTALAR, null));
        }
    }

    private AnalisePlanejamento buscarAnalise(UUID idObra, UUID idAnalise) {
        return analiseRepository.findByIdAndIdObraAndAtivoTrue(idAnalise, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Leitura do projeto não encontrada nesta obra."));
    }

    private static AnaliseResponse paraResponse(AnalisePlanejamento a) {
        return new AnaliseResponse(a.getId(), a.getPagina(), a.getStatus().name(), a.getMensagem(), a.getProposta());
    }
}
