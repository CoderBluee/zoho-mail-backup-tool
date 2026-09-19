package com.pstconverter.core.exception;

/**
 * Custom exception representing failures during mailbox parsing or export.
 */
public class ConverterException extends RuntimeException {
    
    public ConverterException(String message) {
        super(message);
    }
    
    public ConverterException(String message, Throwable cause) {
        super(message, cause);
    }
}
