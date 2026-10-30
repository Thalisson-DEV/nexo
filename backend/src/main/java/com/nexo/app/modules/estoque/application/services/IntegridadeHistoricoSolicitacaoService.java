package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegridadeHistoricoSolicitacaoService {

    private static final String ALGORITMO = "HmacSHA256";
    private final ConfiguracaoChavesAuditoriaSolicitacao configuracao;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;

    public void selar(EventoSolicitacaoMaterial evento, String hashAnterior) {
        var chave = configuracao.chaveAtiva();
        String hash = calcular(evento, hashAnterior, chave.segredo(), instanteCanonico(evento));
        evento.selar(hashAnterior, hash, chave.versao());
    }

    public ResultadoVerificacaoIntegridade verificar(List<EventoSolicitacaoMaterial> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return observar(
                    new ResultadoVerificacaoIntegridade(StatusIntegridadeHistorico.INTEGRO, null, null),
                    List.of());
        }
        List<EventoSolicitacaoMaterial> ordenados = eventos.stream()
                .sorted(Comparator.comparingLong(EventoSolicitacaoMaterial::getSequencia))
                .toList();
        if (ordenados.getFirst().getSequencia() != 1L
                || ordenados.getFirst().getHashAnterior() != null) {
            return observar(resultado(StatusIntegridadeHistorico.VIOLADO, ordenados), ordenados);
        }
        String anterior = null;
        long sequenciaEsperada = 1L;
        boolean possuiFormatoLegado = false;
        for (EventoSolicitacaoMaterial evento : ordenados) {
            if (evento.getSequencia() != sequenciaEsperada++) {
                return observar(resultado(StatusIntegridadeHistorico.VIOLADO, ordenados), ordenados);
            }
            if (evento.getHashAtual() == null || evento.getVersaoChave() == null) {
                return observar(resultado(StatusIntegridadeHistorico.NAO_VERIFICADO, ordenados), ordenados);
            }
            var chave = configuracao.chave(evento.getVersaoChave());
            if (chave.isEmpty()) {
                return observar(resultado(StatusIntegridadeHistorico.NAO_VERIFICADO, ordenados), ordenados);
            }
            if (!Objects.equals(anterior, evento.getHashAnterior())) {
                return observar(resultado(StatusIntegridadeHistorico.VIOLADO, ordenados), ordenados);
            }
            if (evento.getVersaoFormatoHash() < EventoSolicitacaoMaterial.FORMATO_ASSINATURA_ATUAL) {
                possuiFormatoLegado = true;
                anterior = evento.getHashAtual();
                continue;
            }
            if (evento.getVersaoFormatoHash() != EventoSolicitacaoMaterial.FORMATO_ASSINATURA_ATUAL) {
                return observar(resultado(StatusIntegridadeHistorico.NAO_VERIFICADO, ordenados), ordenados);
            }
            String esperado;
            try {
                esperado = calcular(evento, anterior, chave.get().segredo(), instanteCanonico(evento));
            } catch (InvarianteVioladaException ex) {
                return observar(resultado(StatusIntegridadeHistorico.VIOLADO, ordenados), ordenados);
            }
            if (!MessageDigest.isEqual(
                    esperado.getBytes(StandardCharsets.US_ASCII),
                    evento.getHashAtual().getBytes(StandardCharsets.US_ASCII))) {
                return observar(resultado(StatusIntegridadeHistorico.VIOLADO, ordenados), ordenados);
            }
            anterior = evento.getHashAtual();
        }
        return observar(resultado(
                possuiFormatoLegado ? StatusIntegridadeHistorico.NAO_VERIFICADO : StatusIntegridadeHistorico.INTEGRO,
                ordenados), ordenados);
    }

    private ResultadoVerificacaoIntegridade observar(
            ResultadoVerificacaoIntegridade resultado,
            List<EventoSolicitacaoMaterial> eventos
    ) {
        observabilidade.registrarIntegridade(resultado.status(), eventos);
        return resultado;
    }

    private ResultadoVerificacaoIntegridade resultado(
            StatusIntegridadeHistorico status,
            List<EventoSolicitacaoMaterial> eventos
    ) {
        return new ResultadoVerificacaoIntegridade(
                status, eventos.getFirst().getSequencia(), eventos.getLast().getSequencia());
    }

    private String calcular(
            EventoSolicitacaoMaterial evento,
            String hashAnterior,
            byte[] segredo,
            String instante
    ) {
        UUID idCadeia = evento.getIdSolicitacao();
        if (idCadeia == null) {
            throw new InvarianteVioladaException("O evento de historico deve referenciar uma solicitacao.");
        }
        List<String> campos = new ArrayList<>();
        campos.add("FORMATO=" + evento.getVersaoFormatoHash());
        campos.add(evento.getEscopo().name());
        campos.add(idCadeia.toString());
        campos.add(Long.toString(evento.getSequencia()));
        campos.add(evento.getTipoEvento().name());
        campos.add(texto(evento.getTipoTecnico()));
        campos.add(evento.getTipoObraSnapshot() == null ? "" : evento.getTipoObraSnapshot().name());
        campos.add(evento.getIdOcorrencia() == null ? "" : evento.getIdOcorrencia().toString());
        campos.add(texto(evento.getOcorrencia()));
        campos.add(evento.getIdAutor() == null ? "" : evento.getIdAutor().toString());
        campos.add(texto(evento.getAutorSnapshot()));
        campos.add(evento.getOrigem().name());
        campos.add(texto(evento.getJustificativa()));
        campos.add(Long.toString(evento.getVersaoSolicitacao()));
        campos.add(instante);
        campos.add(SerializadorPayloadEventoSolicitacao.canonicalizarJson(evento.getPayloadCanonico()));
        campos.add(hashAnterior == null ? "" : hashAnterior);
        String conteudo = String.join("\n", campos);
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo, ALGORITMO));
            return HexFormat.of().formatHex(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new InvarianteVioladaException("Nao foi possivel calcular a integridade do historico.");
        }
    }

    private String instanteCanonico(EventoSolicitacaoMaterial evento) {
        if (evento.getOcorridoEm() == null) {
            throw new InvarianteVioladaException("O instante do evento de historico e obrigatorio.");
        }
        return evento.getOcorridoEm().toInstant()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                .toString();
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    public record ResultadoVerificacaoIntegridade(
            StatusIntegridadeHistorico status,
            Long sequenciaInicial,
            Long sequenciaFinal
    ) {
    }
}
