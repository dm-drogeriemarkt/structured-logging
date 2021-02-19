package de.dm.prom.structuredlogging;

/**
 * use this interface to describe to which field to log a certain type
 * <p>
 * the class that implements this must have a non-parameterized constructor
 *
 * @param <T> type to log
 */
@SuppressWarnings("squid:S2326")
//T ist not used in the interface, but still needed to find the right MdcKeySupplier for a type
public interface MdcKeySupplier<T> {
    /**
     * MDC field name to log to
     *
     * @return MDC field name
     */
    String getMdcKey();
}
