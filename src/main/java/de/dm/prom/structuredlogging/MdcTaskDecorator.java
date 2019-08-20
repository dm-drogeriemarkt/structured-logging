package de.dm.prom.structuredlogging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * task decorator for Spring - to put MDC information into another task, for example when @Async is used
 * <p>
 * see the documentation on how to use this
 */
@Slf4j
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null && !contextMap.isEmpty()) {
                    MDC.setContextMap(contextMap);
                    log.debug("MDC context set for @Async method."); //hopefully this helps when reading logs in the future
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
