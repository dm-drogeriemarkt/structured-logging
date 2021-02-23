package de.dm.prom.structuredlogging;

/**
 * Enum describing the strategy from the Task decorator
 */
public enum OverwriteStrategy {
    /**
     * Prevent overwriting existing MDC content in a thread that is decorated. Will print a warning when MDC content is encountered
     */
    PREVENT_OVERWRITE,

    /**
     * Do not prevent, but log when overwriting MDC content in an thread that is decorated
     */
    LOG_OVERWRITE,

    /**
     * Neither prevent overwriting of MDC content in an thread that is decorated and do not log when this happens
     */
    JUST_OVERWRITE
}
