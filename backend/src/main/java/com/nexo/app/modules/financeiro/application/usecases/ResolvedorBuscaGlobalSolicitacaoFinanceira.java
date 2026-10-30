package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.financeiro.application.dto.IdsBuscaSolicitacaoFinanceira;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FornecedorRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Expande um termo da busca global nos cadastros que uma solicitação referencia.
 *
 * <p>Essa expansão acontece antes da especificação da solicitação porque os nomes
 * de placa, colaborador, base, processo, contrato e usuário não são colunas do
 * módulo financeiro. O resultado contém somente IDs e pode ser usado em
 * predicados {@code IN}, sem acoplamento da persistência financeira às entidades
 * de outros módulos.</p>
 */
@Component
@RequiredArgsConstructor
public class ResolvedorBuscaGlobalSolicitacaoFinanceira {

    private final FornecedorRepository fornecedorRepository;
    private final SolicitacaoCompraRepository solicitacaoCompraRepository;
    private final ColaboradorApi colaboradorApi;
    private final VeiculoApi veiculoApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final ProcessoNegocioApi processoNegocioApi;
    private final ContratoApi contratoApi;
    private final UsuarioApi usuarioApi;

    public IdsBuscaSolicitacaoFinanceira resolver(String termo) {
        String normalizado = SpecificationUtils.normalizarTermo(termo);
        if (normalizado == null || normalizado.isBlank()) {
            return IdsBuscaSolicitacaoFinanceira.vazio();
        }

        String digitos = normalizado.replaceAll("\\D", "");
        String digitosParaConsulta = digitos.length() >= 3 ? digitos : "__nenhum_documento__";
        Set<UUID> compras = new HashSet<>();
        if (somenteNumero(normalizado)) {
            try {
                solicitacaoCompraRepository.findByNumero(Long.valueOf(normalizado))
                        .map(solicitacao -> solicitacao.getId())
                        .ifPresent(compras::add);
            } catch (NumberFormatException ignored) {
                // Um termo numérico muito grande continua sendo uma busca textual válida.
            }
        }

        return new IdsBuscaSolicitacaoFinanceira(
                new HashSet<>(fornecedorRepository.buscarIdsPorTrecho(normalizado, digitosParaConsulta)),
                new HashSet<>(colaboradorApi.buscarIdsPorTrechoDoNome(normalizado)),
                new HashSet<>(veiculoApi.buscarIdsPorTrecho(normalizado)),
                new HashSet<>(baseOperacionalApi.buscarIdsPorTrecho(normalizado)),
                new HashSet<>(processoNegocioApi.buscarIdsPorTrecho(normalizado)),
                new HashSet<>(contratoApi.buscarIdsPorTrecho(normalizado)),
                new HashSet<>(usuarioApi.buscarIdsPorTrechoDoLogin(normalizado)),
                compras);
    }

    private static boolean somenteNumero(String termo) {
        return termo.chars().allMatch(Character::isDigit);
    }
}
