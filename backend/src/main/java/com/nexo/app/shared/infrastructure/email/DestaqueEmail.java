package com.nexo.app.shared.infrastructure.email;

/**
 * Tom da linha de identificação no alto do e-mail — a única variação visual permitida.
 *
 * <p>A cor entra em dois lugares discretos: a palavra da prioridade e o filete sob a
 * linha de identificação. Não há fundo colorido em lugar nenhum, e isso é deliberado —
 * etiqueta em pílula com fundo âmbar é vocabulário de aplicativo de consumo, e um
 * sistema de gestão que escreve assim é lido como propaganda. A urgência aparece na
 * palavra escrita, não no realce.</p>
 *
 * <p>Os tons são versões dessaturadas da escala de severidade do frontend, para que
 * "crítico" signifique a mesma coisa na tela e na caixa de entrada.</p>
 */
public enum DestaqueEmail {

    NEUTRO("#6b7280", "#d8dce3"),
    INFORMATIVO("#1e3a8a", "#d8dce3"),
    ATENCAO("#92400e", "#c2822c"),
    CRITICO("#991b1b", "#b91c1c"),
    POSITIVO("#065f46", "#d8dce3");

    private final String texto;
    private final String regua;

    DestaqueEmail(String texto, String regua) {
        this.texto = texto;
        this.regua = regua;
    }

    /** Cor da palavra da prioridade na linha de identificação. */
    public String getTexto() {
        return texto;
    }

    /** Cor do filete sob a linha de identificação. */
    public String getRegua() {
        return regua;
    }
}
