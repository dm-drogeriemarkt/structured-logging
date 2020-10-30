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
    /**
     * use this do decorate a runnable intended to run in a separate thread - it will be decorated so that the main
     * thread's MDC Context will be copied to the other thread - and removed when runnable is done
     * <p>
     * analogous in usage to Spring's TaskDecorator
     * <p>
     * will log a WARNing and not copy the MDC context if the other thread already has content in its MDC context.
     *
     * @param runnable runnable to run
     *
     * @return the decorated runnable
     */
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            boolean contextWasSet = false;
            try {
                if (hasContent(contextMap)) {
                    Map<String, String> threadsContextMap = MDC.getCopyOfContextMap();
                    if (!hasContent(threadsContextMap)) {
                        MDC.setContextMap(contextMap);
                        contextWasSet = true;
                        log.debug("MDC context set for runnable."); //hopefully this helps when reading logs in the future
                    } else {
                        log.warn("MDC context was not set for runnable because it was run in a thread that already had a context.");
                    }
                }
                runnable.run();
            } finally {
                if (contextWasSet) {
                    MDC.clear();
                }
            }
        };
    }

    private boolean hasContent(Map<String, String> contextMap) {
        return contextMap != null && !contextMap.isEmpty();
    }
}
