package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FornecedorRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.VeiculoApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Ponte entre os campos de referência do formulário e os cadastros que os
 * alimentam, sejam eles de outros módulos ou do próprio financeiro.
 *
 * <p>Existe por dois motivos que andam juntos. Na entrada, confirma que o UUID
 * recebido aponta mesmo para um registro vivo — sem isso, o formulário moldável
 * seria uma porta aberta para gravar referências inventadas. Na saída, traduz o
 * UUID no texto que a pessoa reconhece, para que listagens e detalhes cheguem
 * prontos ao frontend sem uma consulta por linha.</p>
 *
 * <p>Concentrar o {@code switch} aqui é deliberado: quando uma fonte nova é
 * adicionada em {@link FonteReferencia}, este é o único lugar a mexer, e o
 * compilador aponta o caso faltante.</p>
 */
@Component
@RequiredArgsConstructor
public class ResolvedorReferencias {

    private final BaseOperacionalApi baseOperacionalApi;
    private final ProcessoNegocioApi processoNegocioApi;
    private final ColaboradorApi colaboradorApi;
    private final VeiculoApi veiculoApi;
    private final ContratoApi contratoApi;
    private final FornecedorRepository fornecedorRepository;
    private final SolicitacaoCompraRepository solicitacaoCompraRepository;

    /**
     * Confirma que a referência existe e está ativa no cadastro de origem.
     */
    public boolean existe(FonteReferencia fonte, UUID id) {
        if (fonte == null || id == null) {
            return false;
        }
        return switch (fonte) {
            case BASE_OPERACIONAL -> baseOperacionalApi.existeBase(id);
            case PROCESSO -> processoNegocioApi.existeProcesso(id);
            case COLABORADOR -> colaboradorApi.existeColaborador(id);
            case VEICULO -> veiculoApi.existeVeiculo(id);
            case OBRA -> contratoApi.existeContrato(id);
            case FORNECEDOR -> fornecedorRepository.findById(id).filter(Fornecedor::isAtivo).isPresent();
            case SOLICITACAO_COMPRA -> solicitacaoCompraRepository.findById(id)
                    .filter(SolicitacaoCompra::isAtivo)
                    .isPresent();
        };
    }

    /**
     * Texto legível da referência, ou {@code null} quando o registro não é
     * encontrado — caso em que o frontend exibe o valor bruto em vez de quebrar.
     */
    public String descrever(FonteReferencia fonte, UUID id) {
        if (fonte == null || id == null) {
            return null;
        }
        return switch (fonte) {
            case BASE_OPERACIONAL -> baseOperacionalApi.buscarPorIdResponse(id)
                    .map(base -> base.nome())
                    .orElse(null);
            case PROCESSO -> processoNegocioApi.buscarPorIdResponse(id)
                    .map(ProcessoNegocioResponse::nome)
                    .orElse(null);
            case COLABORADOR -> colaboradorApi.buscarPorIdResponse(id)
                    .map(colaborador -> colaborador.nomeCompleto())
                    .orElse(null);
            case VEICULO -> veiculoApi.buscarPorIdResponse(id)
                    .map(this::descreverVeiculo)
                    .orElse(null);
            case OBRA -> contratoApi.buscarContratoResumo(id)
                    .map(obra -> obra.numeroContrato() + " — " + obra.cliente())
                    .orElse(null);
            case FORNECEDOR -> fornecedorRepository.findById(id)
                    .map(Fornecedor::getNomeFantasia)
                    .orElse(null);
            case SOLICITACAO_COMPRA -> solicitacaoCompraRepository.findById(id)
                    .map(compra -> "Compra nº " + compra.getNumero())
                    .orElse(null);
        };
    }

    /** Nome amigável da fonte, usado nas mensagens de erro de preenchimento. */
    public String descreverFonte(FonteReferencia fonte) {
        return fonte == null ? "cadastro" : fonte.getDescricao();
    }

    /**
     * Carrega o fornecedor para as validações que dependem do estado dele
     * (bloqueio, curadoria), sem obrigar o chamador a conhecer o repositório.
     */
    public Optional<Fornecedor> buscarFornecedor(UUID id) {
        return id == null ? Optional.empty() : fornecedorRepository.findById(id);
    }

    /**
     * Carrega a compra referenciada por um pagamento, para as validações que
     * dependem do estado dela (aprovação) e para o pré-preenchimento.
     */
    public Optional<SolicitacaoCompra> buscarCompra(UUID id) {
        return id == null ? Optional.empty() : solicitacaoCompraRepository.findById(id);
    }

    private String descreverVeiculo(com.nexo.app.modules.rh.VeiculoResponse veiculo) {
        if (veiculo.modelo() == null || veiculo.modelo().isBlank()) {
            return veiculo.placa();
        }
        return veiculo.placa() + " — " + veiculo.modelo();
    }
}
