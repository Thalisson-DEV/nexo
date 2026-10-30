package com.nexo.app.modules.lookup.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class LookupBootstrapEtag {

    private final ObjectMapper objectMapper;

    public LookupBootstrapEtag(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String generate(LookupBootstrapDto body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(body));
            return '"' + HexFormat.of().formatHex(digest) + '"';
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Nao foi possivel versionar o bootstrap de lookups", exception);
        }
    }
}
