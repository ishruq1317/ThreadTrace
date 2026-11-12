package com.threadtrace.shared;

public final class DomainExceptions {

    private DomainExceptions() {
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class DuplicateResourceException extends RuntimeException {
        public DuplicateResourceException(String message) {
            super(message);
        }
    }

    public static class MassBalanceViolationException extends RuntimeException {
        public MassBalanceViolationException(String message) {
            super(message);
        }
    }

    public static class BrandAccessDeniedException extends RuntimeException {
        public BrandAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class InvalidLotStateException extends RuntimeException {
        public InvalidLotStateException(String message) {
            super(message);
        }
    }

    public static class DomainValidationException extends RuntimeException {
        public DomainValidationException(String message) {
            super(message);
        }
    }
}
