package com.top50.exception;

public class WeekNotFoundException extends RuntimeException {
    public WeekNotFoundException(String weekIso) {
        super("Week not found: " + weekIso);
    }
}
