package com.nexo.app.shared.infrastructure.email;

/**
 * Porta de envio de e-mail. Abstrai os módulos do provedor concreto, exatamente como
 * {@code ObjectStorageService} faz com o armazenamento de arquivos.
 *
 * <p>Existe uma implementação por provedor, e apenas uma é registrada como bean, de
 * acordo com {@code ces.email.provedor}. Quem depende desta interface não tem como
 * saber — nem precisa — se a mensagem saiu pela caixa corporativa da empresa, por um
 * serviço transacional ou por lugar nenhum.</p>
 */
public interface ServicoEmail {

    /**
     * Envia a mensagem. Nunca lança por falha de entrega: o desfecho volta no
     * {@link ResultadoEnvioEmail} para que quem chamou possa registrar e reagendar.
     */
    ResultadoEnvioEmail enviar(MensagemEmail mensagem);

    /** Qual provedor está atendendo, para efeito de registro e diagnóstico. */
    ProvedorEmail provedor();

    /** {@code false} quando o envio está desligado — nada será entregue de fato. */
    default boolean ativo() {
        return provedor() != ProvedorEmail.NENHUM;
    }
}
