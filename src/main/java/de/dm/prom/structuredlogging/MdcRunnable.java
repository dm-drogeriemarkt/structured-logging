package de.dm.prom.structuredlogging;

/**
 * Runnable that throws, to be able to handle MDC Context that throw one checked Exception
 *
 * @param <E> thrown checked Exception
 */
public interface MdcRunnable<E extends Throwable> {
    /**
     * callback to run with context
     *
     * @throws E thrown checked Exception
     */
    void run() throws E;
}
