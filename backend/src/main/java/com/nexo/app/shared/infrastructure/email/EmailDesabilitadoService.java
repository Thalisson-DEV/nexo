package com.nexo.app.shared.infrastructure.email;

import lombok.extern.slf4j.Slf4j;

/**
 * O provedor que não envia nada.
 *
 * <p>Existe para que a ausência de credencial seja um estado normal do sistema, e não
 * um bean faltando. É o padrão em desenvolvimento — onde disparar e-mail de verdade
 * para a lista real de aprovadores seria o pior desfecho possível — e é também para
 * onde a configuração cai quando o provedor escolhido está incompleto, em vez de
 * derrubar a subida da aplicação por causa do canal secundário de aviso.</p>
 *
 * <p>O envio é registrado como sucesso: do ponto de vista de quem chamou, a mensagem
 * foi tratada conforme a configuração vigente. Marcá-la como falha encheria
 * {@code logs_email} de pendências que a varredura tentaria reenviar para sempre.</p>
 */
@Slf4j
public class EmailDesabilitadoService implements ServicoEmail {

    @Override
    public ProvedorEmail provedor() {
        return ProvedorEmail.NENHUM;
    }

    @Override
    public ResultadoEnvioEmail enviar(MensagemEmail mensagem) {
        log.info("Envio de e-mail desligado (ces.email.provedor=NENHUM). "
                        + "Assunto '{}' seria enviado a {} destinatário(s) com {} anexo(s).",
                mensagem.assunto(), mensagem.destinatarios().size(), mensagem.anexos().size());
        return ResultadoEnvioEmail.entregue(ProvedorEmail.NENHUM, null);
    }
}
