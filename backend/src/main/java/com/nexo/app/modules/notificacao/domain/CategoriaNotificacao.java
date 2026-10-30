package com.nexo.app.modules.notificacao.domain;

/**
 * Assunto da notificação, do ponto de vista de quem recebe.
 *
 * <p>É por esta enumeração que o usuário liga e desliga o que quer receber, então
 * ela é deliberadamente curta: uma categoria só se justifica quando faz sentido
 * alguém querer silenciá-la sem silenciar as demais. Categorias novas exigem
 * migration (a coluna tem CHECK), o que é intencional — impede que o catálogo
 * cresça sem revisão e deixe as preferências existentes sem significado.</p>
 */
public enum CategoriaNotificacao {

    APROVACAO("Aprovações pendentes"),
    SEGURANCA("Segurança do trabalho"),
    OPERACIONAL("Operação"),
    CHAMADO("Chamados"),
    /**
     * Alguém marcou você em um texto. Categoria própria porque é o único assunto
     * endereçado à pessoa <em>pelo nome</em>: cair sob {@link #CHAMADO} faria quem
     * silenciasse a movimentação da central perder também as marcações dirigidas a
     * si, que é justamente o que ninguém quer silenciar.
     */
    MENCAO("Marcações"),
    COMUNICADO("Comunicados"),
    SISTEMA("Sistema");

    private final String rotulo;

    CategoriaNotificacao(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto exibido ao usuário na tela de preferências e no agrupamento da caixa. */
    public String getRotulo() {
        return rotulo;
    }

    /**
     * Se a categoria pode sair por e-mail.
     *
     * <p>É um veto do domínio, não um padrão: {@link #MENCAO} responde {@code false} e
     * nem uma regra marcada para enviar nem uma preferência ligada pelo usuário
     * conseguem furá-lo. Marcação é conversa dentro da aplicação — quem escreve
     * {@code @LOGIN} no meio de uma mensagem de chamado está chamando alguém para a
     * tela ao lado, não pedindo que a pessoa seja avisada por e-mail. Deixar isso como
     * simples padrão desligado significaria que um clique errado na tela de
     * preferências transforma cada menção em um e-mail.</p>
     */
    public boolean permiteEmail() {
        return this != MENCAO;
    }
}
