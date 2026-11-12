package com.cloudresilience.modules.identity.application;

public class InvalidRegistrationException extends RuntimeException {
    public InvalidRegistrationException(String message) { super(message); }
}
