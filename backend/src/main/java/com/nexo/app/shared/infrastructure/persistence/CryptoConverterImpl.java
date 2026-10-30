package com.nexo.app.shared.infrastructure.persistence;

import com.nexo.app.shared.infrastructure.config.ApplicationContextUtils;
import com.nexo.app.shared.infrastructure.security.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Conversor automático de campos sensíveis 🔒 de texto plano para AES-256
 * no momento da escrita no banco, e vice-versa na leitura.
 *
 * Utiliza o EncryptionService centralizado.
 */
@Converter
public class CryptoConverterImpl implements AttributeConverter<String, String> {

    private EncryptionService encryptionService;

    private EncryptionService getService() {
        if (encryptionService == null) {
            this.encryptionService = ApplicationContextUtils.getBean(EncryptionService.class);
        }
        return encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        return getService().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        return getService().decrypt(dbData);
    }
}