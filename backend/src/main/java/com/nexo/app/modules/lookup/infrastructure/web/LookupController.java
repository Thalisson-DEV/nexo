package com.nexo.app.modules.lookup.infrastructure.web;

import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import com.nexo.app.modules.lookup.application.usecases.LookupBootstrapUseCase;
import com.nexo.app.modules.lookup.doc.LookupControllerDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/lookups")
@RequiredArgsConstructor
public class LookupController implements LookupControllerDoc {

    public static final String CONTRACT_VERSION_HEADER = "X-Lookup-Contract-Version";
    private static final String CONTRACT_VERSION = "1";
    private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();

    private final LookupBootstrapUseCase useCase;
    private final LookupBootstrapEtag etagGenerator;

    @Override
    @GetMapping("/bootstrap")
    @PreAuthorize("hasAuthority('rh:base-operacional:read') "
            + "and hasAuthority('rh:processo-negocio:read') "
            + "and hasAuthority('estoque:movimentacao:read') "
            + "and hasAuthority('estoque:solicitacao:read')")
    public ResponseEntity<LookupBootstrapDto> bootstrap(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        LookupBootstrapDto body = useCase.execute();
        String etag = etagGenerator.generate(body);
        if (matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CACHE_CONTROL)
                    .eTag(etag)
                    .header(CONTRACT_VERSION_HEADER, CONTRACT_VERSION)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(CACHE_CONTROL)
                .eTag(etag)
                .header(CONTRACT_VERSION_HEADER, CONTRACT_VERSION)
                .body(body);
    }

    static boolean matches(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String comparableCurrent = weakValue(currentEtag);
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .anyMatch(candidate -> "*".equals(candidate)
                        || comparableCurrent.equals(weakValue(candidate)));
    }

    private static String weakValue(String etag) {
        return etag != null && etag.startsWith("W/") ? etag.substring(2) : etag;
    }
}
