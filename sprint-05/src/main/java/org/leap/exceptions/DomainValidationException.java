package org.leap.exceptions;

public class DomainValidationException extends DomainException {

    private final String fieldName;
    private final Object rejectedValue;

    public DomainValidationException(
            String fieldName,
            Object rejectedValue,
            String message) {

        super("VAL-422", message);

        this.fieldName = fieldName;
        this.rejectedValue = rejectedValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }
}