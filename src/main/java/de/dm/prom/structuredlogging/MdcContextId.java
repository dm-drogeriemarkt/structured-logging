package de.dm.prom.structuredlogging;

/**
 * use this interface to describe to which field to log a certain type
 *
 * the class that implements this must have a non-parameterized constructor
 *
 * @param <T> type to log
 */
public interface MdcContextId<T> {
    /**
     * MDC field name to log to
     *
     * @return MDC field name
     */
    String getMdcKey();
}
