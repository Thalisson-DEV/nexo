package com.nexo.app.shared.infrastructure.idempotencia;

import com.nexo.app.shared.infrastructure.logging.LogFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Guarda da execução única de requisições de escrita.
 *
 * <p>Todas as operações rodam em transação própria ({@code REQUIRES_NEW}) porque são
 * chamadas de um filtro, de fora do ciclo transacional do handler. A reserva precisa
 * estar <b>commitada antes</b> de o handler começar — senão a segunda requisição não a
 * enxergaria — e a conclusão precisa ser gravada <b>depois</b> de ele terminar, sem se
 * amarrar ao destino da transação de negócio.</p>
 *
 * @author <b>Thalisson</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotenciaService {

    /**
     * Teto para o corpo guardado. Nenhum endpoint de escrita do sistema devolve DTO
     * perto disso; o limite existe para que uma resposta anômala não vire uma linha
     * gigante em uma tabela que recebe uma inserção por escrita do sistema inteiro.
     */
    static final int TAMANHO_MAXIMO_CORPO = 1024 * 1024;

    private final RegistroIdempotenciaRepository repository;

    /**
     * Por quanto tempo a promessa de "não executo de novo" vale. Depois disso, apertar
     * salvar volta a significar "quero outro registro" — que é a intenção legítima de
     * quem lança dois descontos parecidos em dias diferentes. 24h cobrem com folga a
     * janela real: retentativa de usuário e fila offline esperando a rede voltar.
     */
    @Value("${ces.idempotencia.janela-horas:24}")
    private long janelaHoras;

    /**
     * Decide o destino de uma requisição carimbada, reservando a chave quando ela é inédita.
     *
     * <p>A ordem importa: tenta-se <i>primeiro</i> reservar e só depois ler. Ler antes
     * para "checar se já existe" reintroduziria a janela entre leitura e escrita que a
     * reserva atômica existe para fechar.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoReserva reservar(String chave, UUID idUsuario, String metodo, String caminho, String digest) {
        OffsetDateTime expiracao = OffsetDateTime.now().plus(Duration.ofHours(janelaHoras));

        if (repository.reservar(chave, idUsuario, metodo, caminho, expiracao) == 1) {
            return new ResultadoReserva.Prosseguir();
        }

        var registro = repository.findById(chave).orElse(null);
        if (registro == null) {
            // A linha existia no INSERT e sumiu antes do SELECT: outra requisição com a
            // mesma chave terminou em erro e liberou a reserva no intervalo. Recusar seria
            // punir quem não errou; deixar passar apenas repete o cenário sem a proteção.
            // Uma segunda tentativa de reserva resolve o caso quase sempre.
            return repository.reservar(chave, idUsuario, metodo, caminho, expiracao) == 1
                    ? new ResultadoReserva.Prosseguir()
                    : new ResultadoReserva.EmVoo();
        }

        // Chave de outro usuário é tratada como chave que não existe: nem confirma a
        // existência, nem entrega o corpo gravado por terceiro.
        if (!registro.pertenceA(idUsuario) || !registro.correspondeA(metodo, caminho, digest)) {
            log.warn("Chave de idempotência reutilizada para operação diferente: chave={} ({} {}).",
                    LogFingerprint.de(chave), metodo, caminho);
            return new ResultadoReserva.Conflitante();
        }

        return registro.concluida()
                ? new ResultadoReserva.Repetir(registro)
                : new ResultadoReserva.EmVoo();
    }

    /**
     * Fecha a reserva guardando a resposta que será repetida nas retentativas.
     *
     * <p>Falhar aqui não pode derrubar uma requisição que já deu certo: o efeito de
     * negócio está commitado e o usuário precisa receber sua resposta. O preço de
     * engolir o erro é uma reserva que expira sozinha, e o risco que ela representa
     * (uma duplicata em caso de retentativa) é menor que o de transformar um sucesso
     * em erro na cara de quem já salvou.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(String chave, int statusHttp, String contentType, byte[] corpo, String digest) {
        String corpoTexto = null;
        if (corpo != null && corpo.length > 0) {
            if (corpo.length > TAMANHO_MAXIMO_CORPO) {
                log.warn("Resposta de {} bytes excede o teto de replay ({}); a chave {} repetirá "
                                + "apenas o status, sem corpo.", corpo.length, TAMANHO_MAXIMO_CORPO,
                        LogFingerprint.de(chave));
            } else {
                corpoTexto = new String(corpo, StandardCharsets.UTF_8);
            }
        }
        repository.concluir(chave, statusHttp, contentType, corpoTexto, digest);
    }

    /**
     * Devolve a chave ao pool porque a requisição não produziu efeito que valha repetir.
     *
     * <p>Chamado em qualquer desfecho não-2xx. Um 400 é resposta sobre a tentativa, não
     * sobre a intenção: quem ajusta o formulário e reenvia com a mesma chave (o que a
     * fila offline faz por natureza) precisa ser processado, não bloqueado pelo próprio
     * erro anterior.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void liberar(String chave) {
        repository.deleteById(chave);
    }

    /**
     * Impressão digital da requisição, usada para flagrar reuso de chave em outra operação.
     *
     * <p>SHA-256 aqui é escolha de robustez contra colisão acidental, não de segurança:
     * a chave já é escopada por usuário e o digest só compara uma requisição com ela mesma.</p>
     */
    public static String digest(String metodo, String caminho, byte[] corpo) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(metodo.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '|');
            sha.update(caminho.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '|');
            if (corpo != null) {
                sha.update(corpo);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em toda JVM; se faltar, o ambiente está quebrado.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /**
     * Expurgo diário das reservas vencidas.
     *
     * <p>De madrugada porque a varredura toca a tabela inteira e não há pressa: uma
     * reserva vencida já não bloqueia nada — a retomada por {@code ON CONFLICT} cuida
     * disso na hora. O expurgo existe só para a tabela não crescer para sempre.</p>
     */
    @Scheduled(cron = "${ces.idempotencia.cron-expurgo:0 20 3 * * *}")
    @Transactional
    public void expurgarExpirados() {
        int removidos = repository.expurgarExpirados(OffsetDateTime.now());
        if (removidos > 0) {
            log.info("Expurgo de idempotência: {} reservas vencidas removidas.", removidos);
        }
    }
}
