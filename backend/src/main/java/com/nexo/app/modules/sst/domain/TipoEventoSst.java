package com.nexo.app.modules.sst.domain;

/**
 * O que aconteceu, na escala inteira da ocorrência — do quase-acidente ao óbito.
 *
 * <p>Os cinco tipos vivem no mesmo agregado de propósito: a série de ocorrências é uma
 * só, do incidente sem lesão ao óbito, e separá-los em tabelas diferentes tornaria
 * "tivemos 30 incidentes e 2 acidentes" uma frase impossível de escrever em uma
 * consulta só.</p>
 *
 * <p>Não existe aqui um tipo para quase-acidente: a base preventiva da pirâmide é o
 * <b>desvio de segurança</b> apontado na inspeção, que já tem cadastro próprio neste
 * módulo. Criar um tipo de evento para a mesma coisa duplicaria o registro e faria a
 * pirâmide contar duas vezes o que a operação apura uma vez só.</p>
 *
 * <p>Cada tipo carrega duas informações que o domínio usa para validar o registro:
 * se ele <b>implica lesão</b> — e portanto exige acidentado e local da lesão, e recusa
 * o contrário — e qual é o <b>prazo legal de comunicação</b>, que é constante da lei e
 * não campo de formulário.</p>
 */
public enum TipoEventoSst {

    /** Acidente durante o trabalho, no local e no horário de trabalho. */
    ACIDENTE_TIPICO("Acidente típico", true, 24),

    /** Acidente no percurso entre a residência e o trabalho. */
    ACIDENTE_TRAJETO("Acidente de trajeto", true, 24),

    /** Ocorrência anormal sem lesão a pessoas. */
    INCIDENTE_SEM_LESAO("Incidente sem lesão", false, 72),

    /** Dano a veículo, equipamento, patrimônio ou meio ambiente, sem lesão. */
    DANO_MATERIAL("Dano material", false, 72),

    /** Doença adquirida ou desencadeada pelo exercício do trabalho. */
    DOENCA_OCUPACIONAL("Doença ocupacional", true, 24);

    private final String rotulo;
    private final boolean implicaLesao;
    private final int prazoComunicacaoHoras;

    TipoEventoSst(String rotulo, boolean implicaLesao, int prazoComunicacaoHoras) {
        this.rotulo = rotulo;
        this.implicaLesao = implicaLesao;
        this.prazoComunicacaoHoras = prazoComunicacaoHoras;
    }

    public String getRotulo() {
        return rotulo;
    }

    /**
     * Indica se o tipo pressupõe alguém lesionado. Quando pressupõe, o registro exige
     * acidentado e local da lesão; quando não, recusa dados de lesão — um dano material
     * com dias perdidos é erro de preenchimento, não um caso de borda.
     */
    public boolean implicaLesao() {
        return implicaLesao;
    }

    /** Prazo legal, em horas, entre a ocorrência e a comunicação ao SESMT. */
    public int getPrazoComunicacaoHoras() {
        return prazoComunicacaoHoras;
    }
}
