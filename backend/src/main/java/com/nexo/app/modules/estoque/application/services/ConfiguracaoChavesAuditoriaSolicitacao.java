package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "ces.auditoria-solicitacao")
@Getter
@Setter
public class ConfiguracaoChavesAuditoriaSolicitacao implements InitializingBean {

    private String versaoAtiva;
    private Map<String, String> chaves = new LinkedHashMap<>();

    @Override
    public void afterPropertiesSet() {
        chaveAtiva();
    }

    public ChaveAuditoria chaveAtiva() {
        if (versaoAtiva == null || versaoAtiva.isBlank()) {
            throw new InvarianteVioladaException(
                    "A versao ativa da chave de auditoria de solicitacoes nao foi configurada.");
        }
        return chave(versaoAtiva).orElseThrow(() -> new InvarianteVioladaException(
                "A chave ativa de auditoria de solicitacoes nao foi configurada."));
    }

    public Optional<ChaveAuditoria> chave(String versao) {
        String base64 = chaves.get(versao);
        if (base64 == null || base64.isBlank()) {
            return Optional.empty();
        }
        byte[] segredo;
        try {
            segredo = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new InvarianteVioladaException(
                    "A chave de auditoria '" + versao + "' nao esta em Base64 valido.");
        }
        if (segredo.length < 32) {
            throw new InvarianteVioladaException(
                    "A chave de auditoria '" + versao + "' deve possuir ao menos 256 bits.");
        }
        return Optional.of(new ChaveAuditoria(versao, segredo));
    }

    public record ChaveAuditoria(String versao, byte[] segredo) {
        public ChaveAuditoria {
            segredo = segredo.clone();
        }

        @Override
        public byte[] segredo() {
            return segredo.clone();
        }
    }
}
