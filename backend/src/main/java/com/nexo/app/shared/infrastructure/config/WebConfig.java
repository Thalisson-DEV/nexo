package com.nexo.app.shared.infrastructure.config;

import com.nexo.app.shared.infrastructure.web.StringToUuidConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração web global para o sistema.
 * Registra conversores customizados para aumentar a robustez da API.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StringToUuidConverter stringToUuidConverter;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Registra o conversor de UUID para lidar com "undefined" do frontend
        registry.addConverter(stringToUuidConverter);
    }
}
