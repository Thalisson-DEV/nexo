package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Pedido de autorização para adquirir material ou serviço.
 *
 * <p>É um agregado independente: nasce, é autorizado e vale por si. A solicitação
 * de pagamento é que opcionalmente aponta para uma compra aprovada, nunca o
 * contrário — assim as equipes podem comprar sem que exista pagamento previsto e
 * pagar sem que tenha havido compra.</p>
 *
 * <p>Os arquivos não pertencem a este agregado: vivem no repositório documental
 * ({@link DocumentoFinanceiro}) e apontam para cá por {@link OrigemDocumento}. O
 * motivo é de calendário, não de arrumação — orçamento chega antes da decisão,
 * nota fiscal e boleto chegam depois dela, e um anexo preso às regras de edição
 * da solicitação tornaria o segundo caso impossível.</p>
 */
@Entity
@Table(name = "financeiro_solicitacao_compra",
        indexes = {
                @Index(name = "idx_compra_status", columnList = "status"),
                @Index(name = "idx_compra_numero", columnList = "numero", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitacaoCompra extends SolicitacaoBase {

    public SolicitacaoCompra(FormularioSolicitacao formulario, NucleoSolicitacao nucleo, UUID usuarioSolicitanteId) {
        super(formulario, nucleo, usuarioSolicitanteId);
        exigirFormularioDeCompra(formulario);
    }

    @Override
    public TipoSolicitacao getTipoSolicitacao() {
        return TipoSolicitacao.COMPRA;
    }

    /**
     * Substitui por completo o conteúdo preenchido. Editar é sempre "tudo de uma
     * vez" porque o formulário pode ter mudado entre uma edição e outra — aplicar
     * diferenças deixaria resíduos de campos que não existem mais.
     */
    public void atualizarConteudo(NucleoSolicitacao novoNucleo) {
        substituirNucleo(novoNucleo);
    }

    private static void exigirFormularioDeCompra(FormularioSolicitacao formulario) {
        if (formulario.getTipoSolicitacao() != TipoSolicitacao.COMPRA) {
            throw new InvarianteVioladaException(
                    "O formulário '" + formulario.getCodigo() + "' não é um formulário de compra.");
        }
    }
}
