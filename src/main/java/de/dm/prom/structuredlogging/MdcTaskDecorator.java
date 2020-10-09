package de.dm.prom.structuredlogging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;

/**
 * generic task decorator inspired by Spring's task decorator - to be used in other frameworks
 * or if threads are created manually and should receive MDC context data
 *
 * <p>
 * see the documentation on how to use this
 */
@Slf4j
public class MdcTaskDecorator {
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
