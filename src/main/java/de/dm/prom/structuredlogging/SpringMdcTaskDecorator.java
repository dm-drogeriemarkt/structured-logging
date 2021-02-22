package de.dm.prom.structuredlogging;

import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskDecorator;

/**
 * task decorator for Spring - to put MDC information into another task, for example when @Async is used
 * <p>
 * see the documentation on how to use this
 */
@RequiredArgsConstructor
public class SpringMdcTaskDecorator implements TaskDecorator {
    private final OverwriteMode overwriteMode;

    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcTaskDecorator.decorate(runnable, overwriteMode);
    }
}
