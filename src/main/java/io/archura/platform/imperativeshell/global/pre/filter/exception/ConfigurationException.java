package io.archura.platform.imperativeshell.global.pre.filter.exception;

public class ConfigurationException extends RuntimeException {
    public ConfigurationException(final Exception exception) {
        super(exception);
    }
}
