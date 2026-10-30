package com.nexo.app.shared.infrastructure.email;

/**
 * O desfecho de uma tentativa de envio.
 *
 * <p>Falha de entrega volta como valor, e não como exceção, porque não é excepcional:
 * servidor fora do ar e credencial vencida são estados normais de uma integração
 * externa, e quem chama precisa registrá-los na trilha e reagendar — não desviar o
 * fluxo. A exceção continua reservada para erro de programação.</p>
 *
 * @param idExterno identificador do provedor, quando ele devolve um (o Resend devolve;
 *                  SMTP não). É o que permite conferir a entrega no painel do provedor
 *                  a partir do registro no banco.
 */
public record ResultadoEnvioEmail(boolean sucesso, ProvedorEmail provedor,
                                  String idExterno, String erro) {

    public static ResultadoEnvioEmail entregue(ProvedorEmail provedor, String idExterno) {
        return new ResultadoEnvioEmail(true, provedor, idExterno, null);
    }

    public static ResultadoEnvioEmail falhou(ProvedorEmail provedor, String erro) {
        return new ResultadoEnvioEmail(false, provedor, null, resumir(erro));
    }

    /** A coluna de erro tem teto e a causa útil está sempre no começo da mensagem. */
    private static String resumir(String erro) {
        if (erro == null || erro.isBlank()) {
            return "Falha sem detalhe informado pelo provedor.";
        }
        String texto = erro.trim();
        return texto.length() <= 1000 ? texto : texto.substring(0, 1000);
    }
}
