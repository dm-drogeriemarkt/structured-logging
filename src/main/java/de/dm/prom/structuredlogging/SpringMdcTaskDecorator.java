package de.dm.prom.structuredlogging;

import org.springframework.core.task.TaskDecorator;

/**
 * task decorator for Spring - to put MDC information into another task, for example when @Async is used
 * <p>
 * see the documentation on how to use this
 */
public class SpringMdcTaskDecorator extends MdcTaskDecorator implements TaskDecorator {
}
