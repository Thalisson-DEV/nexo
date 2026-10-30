package com.nexo.app.modules.status.infrastructure.web;

import com.nexo.app.modules.status.application.observability.ModuleHealthRegistry;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthResult;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Technical, authenticated read-only contract consumed by the observability plan. */
@RestController
@RequestMapping("/internal/observability/modules")
@RequiredArgsConstructor
public class ModuleHealthController {

    private final ModuleHealthRegistry registry;

    @GetMapping("/{module}")
    public ResponseEntity<ModuleHealthResult> check(@PathVariable String module) {
        ModuleHealthResult result = registry.check(module);
        HttpStatus status = result.status() == ModuleHealthStatus.UP
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }
}
