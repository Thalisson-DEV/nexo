package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento;
import com.nexo.app.modules.obras.domain.TipoExecucaoPonto;
import com.nexo.app.modules.obras.domain.TipoPontoPlanejamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comandos do planejamento. O campo {@code versao} é a versão que o usuário estava vendo: se outra pessoa
 * alterou o registro depois, a gravação é recusada com 409.
 */
public final class PlanejamentoObraRequests {

    private PlanejamentoObraRequests() {
    }

    public record DefinirProjetoMestre(@NotNull(message = "Informe o anexo do projeto") UUID idAnexo) {
    }

    public record GirarPagina(@NotNull(message = "Informe a rotação") Integer graus) {
    }

    public record NovoPonto(
            @NotBlank(message = "Informe o número do ponto") @Size(max = 20) String numero,
            @NotNull @Min(1) Integer pagina,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal x,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal y,
            @Size(max = 60) String barramento,
            TipoPontoPlanejamento tipo) {
    }

    public record EditarPonto(
            @NotBlank(message = "Informe o número do ponto") @Size(max = 20) String numero,
            @Size(max = 60) String barramento,
            TipoPontoPlanejamento tipo,
            TipoExecucaoPonto tipoExecucao,
            boolean temPoda,
            boolean temAterramento,
            Long versao) {
    }

    public record MoverPonto(
            @NotNull @Min(1) Integer pagina,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal x,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal y,
            Long versao) {
    }

    public record NovoVao(@NotNull UUID idPontoA, @NotNull UUID idPontoB,
                          @DecimalMin(value = "0.01", message = "A metragem deve ser maior que zero") BigDecimal metragem) {
    }

    public record EditarVao(@DecimalMin(value = "0.01", message = "A metragem deve ser maior que zero") BigDecimal metragem,
                            Long versao) {
    }

    /**
     * Aplica um kit a vários locais. Sem {@code multiplicador}, o ponto recebe 1 kit e o vão usa a própria metragem.
     */
    public record AplicarKit(@NotNull(message = "Informe o kit") UUID idKit,
                             List<UUID> idsPontos,
                             List<UUID> idsVaos,
                             OperacaoItemKit operacao,
                             @DecimalMin(value = "0.0001", message = "A quantidade deve ser maior que zero") BigDecimal multiplicador) {
    }

    public record AlterarMultiplicador(
            @NotNull @DecimalMin(value = "0.0001", message = "A quantidade deve ser maior que zero") BigDecimal multiplicador,
            Long versao) {
    }

    public record MarcarExecutadoAntes(boolean executado, Long versao) {
    }

    public record AlterarItem(
            @NotNull @DecimalMin(value = "0.0001", message = "A quantidade deve ser maior que zero") BigDecimal quantidade,
            Long versao) {
    }

    public record NovaAnotacao(@NotNull @Min(1) Integer pagina,
                               @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal x,
                               @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal y,
                               @NotBlank(message = "Escreva o texto da anotação") @Size(max = 1000) String texto) {
    }

    public record EditarAnotacao(@NotBlank(message = "Escreva o texto da anotação") @Size(max = 1000) String texto,
                                 Long versao) {
    }

    public record IniciarAnalise(@NotNull @Min(1) Integer pagina) {
    }

    /** Só o que o usuário manteve na revisão da proposta do Copiloto, já com os ajustes dele. */
    public record AceitarProposta(@Valid List<PontoAceito> pontos, @Valid List<VaoAceito> vaos) {
    }

    public record PontoAceito(@NotBlank String referencia,
                              @NotBlank @Size(max = 20) String numero,
                              TipoPontoPlanejamento tipo,
                              @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal x,
                              @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal y,
                              @Size(max = 60) String barramento,
                              List<UUID> idsKits) {
    }

    public record VaoAceito(@NotBlank String referenciaA, @NotBlank String referenciaB,
                            @DecimalMin("0.01") BigDecimal metragem, List<UUID> idsKits) {
    }

    public record ResultadoAplicacaoKit(PlanejamentoObraResponse planejamento, List<String> jaTinham) {
    }

    public record AnaliseResponse(UUID id, int pagina, String status, String mensagem,
                                  PropostaPlanejamento proposta) {
    }

    public record DisponibilidadeAnalise(boolean disponivel, AnaliseResponse ultima) {
    }
}
