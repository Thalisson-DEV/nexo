package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.CampoSchemaResponse;
import com.nexo.app.modules.financeiro.application.dto.CondicaoSchema;
import com.nexo.app.modules.financeiro.application.dto.FormularioSchemaResponse;
import com.nexo.app.modules.financeiro.application.dto.OpcaoSchema;
import com.nexo.app.modules.financeiro.application.dto.ReferenciaSchema;
import com.nexo.app.modules.financeiro.application.dto.RestricoesCampoSchema;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.CampoFormulario;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.Rotulavel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Traduz a variante configurada no contrato que o frontend consome para renderizar
 * a tela.
 *
 * <p>O schema entrega rótulo, tipo, ordem, obrigatoriedade, limites, opções
 * (derivadas da enumeração que apoia o campo), o endpoint de cada campo de
 * referência e a condição de visibilidade. Com isso a tela é genérica de verdade e
 * — o que mais importa — não guarda cópia de nenhuma regra: tudo que ela sabe veio
 * daqui, que por sua vez leu do {@link CampoCanonico}.</p>
 */
@Component
public class GeradorSchemaFormulario {

    public FormularioSchemaResponse gerar(FormularioSolicitacao formulario) {
        // Ordem de apresentação: os blocos na sequência declarada no catálogo e, dentro
        // de cada um, a ordem configurada. Assim a tela apenas percorre a lista e vê os
        // campos de um mesmo assunto já juntos, sem precisar agrupar por conta própria.
        List<CampoSchemaResponse> campos = formulario.camposOrdenados().stream()
                .sorted(Comparator.comparingInt((CampoFormulario campo) -> campo.getGrupo().getOrdem())
                        .thenComparingInt(CampoFormulario::getOrdem))
                .map(this::gerarCampo)
                .toList();

        return new FormularioSchemaResponse(
                formulario.getId(),
                formulario.getTipoSolicitacao(),
                formulario.getCodigo(),
                formulario.getTitulo(),
                formulario.getDescricao(),
                formulario.isPadrao(),
                formulario.isAtivo(),
                campos);
    }

    private CampoSchemaResponse gerarCampo(CampoFormulario campo) {
        return new CampoSchemaResponse(
                campo.getId(),
                campo.getChave(),
                campo.getRotulo(),
                campo.getTipoCampo(),
                campo.getCampoCanonico(),
                campo.isObrigatorio(),
                campo.getOrdem(),
                campo.getGrupo(),
                campo.getGrupo().getRotulo(),
                campo.getAjuda(),
                campo.getPlaceholder(),
                RestricoesCampoSchema.de(campo.getRestricoes()),
                gerarOpcoes(campo),
                gerarReferencia(campo),
                gerarCondicao(campo));
    }

    /** Opções de um campo de escolha, publicadas já com o rótulo pronto. */
    private List<OpcaoSchema> gerarOpcoes(CampoFormulario campo) {
        Class<? extends Enum<?>> enumeracao = campo.getCampoCanonico().getEnumeracao();
        if (enumeracao == null) {
            return List.of();
        }
        return Arrays.stream(enumeracao.getEnumConstants())
                .map(constante -> new OpcaoSchema(constante.name(), rotuloDe(constante)))
                .toList();
    }

    private ReferenciaSchema gerarReferencia(CampoFormulario campo) {
        if (campo.getFonteReferencia() == null) {
            return null;
        }
        return new ReferenciaSchema(
                campo.getFonteReferencia(),
                campo.getFonteReferencia().getEndpointOpcoes(),
                campo.getFonteReferencia().getDescricao());
    }

    private CondicaoSchema gerarCondicao(CampoFormulario campo) {
        return campo.getCondicao()
                .map(condicao -> new CondicaoSchema(
                        condicao.governante().getChave(),
                        List.copyOf(condicao.valores())))
                .orElse(null);
    }

    private static String rotuloDe(Enum<?> constante) {
        return constante instanceof Rotulavel rotulavel ? rotulavel.getRotulo() : constante.name();
    }
}
