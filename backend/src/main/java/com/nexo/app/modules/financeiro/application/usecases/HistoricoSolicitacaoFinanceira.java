package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.EventoSolicitacaoResponse;
import com.nexo.app.modules.financeiro.domain.EventoSolicitacaoFinanceira;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.EventoSolicitacaoFinanceiraRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registra e lê a linha do tempo das solicitações. Compra e pagamento chamam o
 * mesmo registro, na mesma transação da mudança de status: ou os dois ficam, ou
 * nenhum.
 */
@Component
@RequiredArgsConstructor
class HistoricoSolicitacaoFinanceira {

    private final EventoSolicitacaoFinanceiraRepository repository;
    private final UsuarioApi usuarioApi;
    private final ColaboradorApi colaboradorApi;

    void registrar(SolicitacaoBase solicitacao, TipoEventoSolicitacao tipo, StatusSolicitacao statusAnterior,
                   UUID usuarioId, BigDecimal valor, String texto) {
        repository.save(new EventoSolicitacaoFinanceira(solicitacao, tipo, statusAnterior, usuarioId, valor, texto));
    }

    List<EventoSolicitacaoResponse> listar(UUID idSolicitacao) {
        Map<UUID, String> nomes = new HashMap<>();
        return repository.findByIdSolicitacaoOrderByOcorridoEmAsc(idSolicitacao).stream()
                .map(evento -> new EventoSolicitacaoResponse(
                        evento.getId(),
                        evento.getTipo(),
                        evento.getTipo().getRotulo(),
                        evento.getStatusAnterior(),
                        evento.getStatusNovo(),
                        evento.getIdUsuario(),
                        evento.getIdUsuario() == null ? null
                                : nomes.computeIfAbsent(evento.getIdUsuario(), this::nomeDe),
                        evento.getValor(),
                        evento.getTexto(),
                        evento.getOcorridoEm()))
                .toList();
    }

    /**
     * Quantas vezes a decisão de cada solicitação foi desfeita, e a última vez. Uma
     * consulta para a página inteira: a exportação passa por milhares de linhas.
     */
    Map<UUID, DecisoesDesfeitas> decisoesDesfeitas(Collection<UUID> idsSolicitacao) {
        if (idsSolicitacao.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> nomes = new HashMap<>();
        Map<UUID, DecisoesDesfeitas> resultado = new HashMap<>();
        for (var evento : repository.findByIdSolicitacaoInAndTipoOrderByOcorridoEmAsc(
                idsSolicitacao, TipoEventoSolicitacao.DECISAO_DESFEITA)) {
            var anterior = resultado.get(evento.getIdSolicitacao());
            String usuario = evento.getIdUsuario() == null ? null
                    : nomes.computeIfAbsent(evento.getIdUsuario(), this::nomeDe);
            resultado.put(evento.getIdSolicitacao(), new DecisoesDesfeitas(
                    anterior == null ? 1 : anterior.quantidade() + 1,
                    evento.getOcorridoEm(), usuario, evento.getTexto()));
        }
        return resultado;
    }

    /** A contagem e a última ocorrência, que é a que explica o estado atual. */
    record DecisoesDesfeitas(int quantidade, OffsetDateTime ultimaEm, String ultimaPor, String ultimoMotivo) {
    }

    /** Nome do colaborador quando há vínculo; senão, o login. */
    private String nomeDe(UUID idUsuario) {
        return colaboradorApi.buscarNomeCompletoPorUsuario(idUsuario)
                .or(() -> usuarioApi.buscarNomePorId(idUsuario))
                .orElse(null);
    }
}
