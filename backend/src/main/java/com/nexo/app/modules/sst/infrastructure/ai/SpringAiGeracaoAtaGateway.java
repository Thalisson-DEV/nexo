package com.nexo.app.modules.sst.infrastructure.ai;

import com.nexo.app.modules.sst.application.port.GeracaoAtaGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Adaptador dedicado à ata; não importa serviços, prompts ou ferramentas do assistente analítico. */
@Component
public class SpringAiGeracaoAtaGateway implements GeracaoAtaGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiGeracaoAtaGateway.class);
    private static final int LIMITE_RESPOSTA = 80_000;

    private final ObjectProvider<ChatClient.Builder> builderProvider;
    private final boolean enabled;
    private final Duration timeout;

    public SpringAiGeracaoAtaGateway(ObjectProvider<ChatClient.Builder> builderProvider,
                                      @Value("${ces.sst.ata-ai.enabled:false}") boolean enabled,
                                      @Value("${ces.sst.ata-ai.timeout-seconds:45}") long timeoutSeconds) {
        this.builderProvider = builderProvider;
        this.enabled = enabled;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    @Override
    public Resultado gerar(Entrada entrada) {
        if (!enabled) return Resultado.falha("PROVEDOR_DESABILITADO");
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        if (builder == null) return Resultado.falha("PROVEDOR_NAO_CONFIGURADO");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            String conteudo = executor.submit(() -> builder.build().prompt()
                            .system(promptSistema(entrada.versaoPrompt()))
                            .user(promptUsuario(entrada))
                            .call().content())
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (conteudo == null || conteudo.isBlank() || conteudo.length() > LIMITE_RESPOSTA) {
                return Resultado.falha("RESPOSTA_INVALIDA");
            }
            return Resultado.sucesso(conteudo.trim());
        } catch (TimeoutException e) {
            LOGGER.warn("Timeout na geração de ata SST");
            return Resultado.falha("TIMEOUT_PROVEDOR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("GERACAO_INTERROMPIDA");
        } catch (Exception e) {
            LOGGER.warn("Falha na geração de ata SST: {}", e.getClass().getSimpleName());
            return Resultado.falha("PROVEDOR_INDISPONIVEL");
        }
    }

    private static String promptSistema(String versao) {
        return """
                Você é o redator oficial de atas da empresa (CIPA ou Brigada). Produza o texto final da ata seguindo ESTRITAMENTE as seguintes regras de formatação:
                1. O texto deve ser um bloco único (um único parágrafo), SEM NENHUMA quebra de linha.
                2. Datas e horários DEVEM ser escritos obrigatoriamente por extenso (ex: "Dia quinze do mês de maio de dois mil e vinte e seis, às quatorze horas e meia").
                3. Liste de forma clara os nomes de todos os membros presentes e todos os ausentes, incluindo o motivo da ausência quando aplicável.
                4. Conclua o texto exatamente com a string "/////////////////////////////////////" no final, sem espaços após, para evitar linhas em branco.
                5. Use exclusivamente os fatos fornecidos (local, pauta, deliberações). Não invente dados, votações, responsáveis ou assinaturas.
                6. Nunca declare que o documento já foi assinado.
                Template: %s.
                """.formatted(versao);
    }

    private static String promptUsuario(Entrada e) {
        StringBuilder participantes = new StringBuilder();
        e.participantes().forEach(p -> participantes.append("- ").append(p.nome())
                .append(" | ").append(p.papel()).append(" | ").append(p.presenca()).append('\n'));
        return """
                TIPO DO CORPO: %s
                TIPO DA REUNIÃO: %s
                DATA/HORA: %s
                LOCAL/MEIO: %s
                PAUTA CONFIRMADA:
                %s

                PRESENÇAS CONFIRMADAS:
                %s
                TRANSCRIÇÃO CONFIRMADA:
                %s

                <orientacoes_adicionais_do_modelo>
                %s
                </orientacoes_adicionais_do_modelo>

                VERSÃO-BASE (pode estar vazia):
                %s

                PEDIDO DE CORREÇÃO (pode estar vazio):
                %s
                """.formatted(e.tipoCorpo(), e.tipoReuniao(), e.dataHora(), e.localOuMeio(),
                e.pauta(), participantes, e.transcricaoConfirmada(), e.snapshotModelo(),
                nuloComoVazio(e.versaoBase()), nuloComoVazio(e.instrucaoCorrecao()));
    }

    private static String nuloComoVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
