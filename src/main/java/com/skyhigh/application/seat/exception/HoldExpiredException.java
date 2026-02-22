package com.skyhigh.application.seat.exception;

public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException(String message) {
        super(message);
    }
}
