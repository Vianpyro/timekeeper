package io.github.vianpyro.timekeeper.config;

/** Raised when {@code config/timekeeper.properties} cannot be read, parsed, or written. */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
