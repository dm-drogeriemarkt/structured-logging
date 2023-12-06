package de.dm.prom.structuredlogging;

/**
 * Supplier that throws, to be able to handle MDC Context that throw one checked Exception
 *
 * @param <T> return value of context
 * @param <E> thrown checked Exception
 */
public interface MdcSupplier<T, E extends Throwable> {
    /**
     * callback to run with context
     *
     * @return specified return value
     *
     * @throws E thrown checked Exception
     */
    T get() throws E;
}
