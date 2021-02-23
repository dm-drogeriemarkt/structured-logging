package de.dm.prom.structuredlogging;

import org.springframework.core.task.TaskDecorator;

/**
 * task decorator for Spring - to put MDC information into another task, for example when @Async is used
 * <p>
 * see the documentation on how to use this
 */
public class SpringMdcTaskDecorator implements TaskDecorator {
    private final OverwriteStrategy overwriteStrategy;

    /**
     * Creates a new SpringMdcTaskDecorator defining the stragety to use
     *
     * @param overwriteStrategy strategy to use when encountering MDC content in decorated threads
     */
    public SpringMdcTaskDecorator(OverwriteStrategy overwriteStrategy) {
        this.overwriteStrategy = overwriteStrategy;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcTaskDecorator.decorate(runnable, overwriteStrategy);
    }
}
