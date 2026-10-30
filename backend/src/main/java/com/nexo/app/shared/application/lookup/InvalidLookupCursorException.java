package com.nexo.app.shared.application.lookup;

/** Cursor ausente, corrompido, incompatível ou semanticamente inválido. */
public class InvalidLookupCursorException extends IllegalArgumentException {

    public InvalidLookupCursorException(String message) {
        super(message);
    }

    public InvalidLookupCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
