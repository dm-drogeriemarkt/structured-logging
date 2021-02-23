package de.dm.prom.structuredlogging;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

/**
 * generic task decorator inspired by Spring's task decorator - to be used in other frameworks
 * or if threads are created manually and should receive MDC context data
 *
 * <p>
 * see the documentation on how to use this
 */
@Slf4j
@NoArgsConstructor(access = PRIVATE)
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
     * @param overwriteStrategy strategy to use when encountering MDC content in decorated threads
     *
     * @return the decorated runnable
     */
    public static Runnable decorate(Runnable runnable, OverwriteStrategy overwriteStrategy) {
        Optional<Map<String, String>> parentContext = Optional.ofNullable(MDC.getCopyOfContextMap());
        return () -> {
            boolean contextWasSet = false;
            Optional<Map<String, String>> childContext = Optional.ofNullable(MDC.getCopyOfContextMap());
            try {
                if (parentContext.isPresent()) {
                    Set<String> childKeys = getKeys(childContext);
                    if (overwriteStrategy != OverwriteStrategy.PREVENT_OVERWRITE || childKeys.isEmpty()) {
                        setContextInThread(overwriteStrategy, parentContext.get(), childKeys);
                        contextWasSet = true;
                    } else {
                        log.warn("MDC context was not set for runnable because it was run in a thread that already had a context. MDC keys present: {}", childKeys);
                    }
                }
                runnable.run();
            } finally {
                if (contextWasSet) {
                    if (childContext.isPresent()) {
                        MDC.setContextMap(childContext.get());
                    } else {
                        MDC.clear();
                    }
                }
            }
        };
    }

    private static void setContextInThread(OverwriteStrategy overwriteStrategy, Map<String, String> contextMap, Set<String> presentKeys) {
        if (overwriteStrategy == OverwriteStrategy.LOG_OVERWRITE && !presentKeys.isEmpty()) {
            log.warn("MDC context will be set despite MDC keys being present in target thread. MDC keys present: {}", presentKeys);
        }

        MDC.setContextMap(contextMap);
        log.debug("MDC context set for runnable."); //hopefully this helps when reading logs in the future
    }

    private static Set<String> getKeys(Optional<Map<String, String>> contextMap) {
        if (contextMap.isPresent() && !contextMap.get().isEmpty()) {
            return contextMap.get().keySet();
        }
        return Collections.emptySet();
    }
}
