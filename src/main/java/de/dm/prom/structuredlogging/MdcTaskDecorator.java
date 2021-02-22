package de.dm.prom.structuredlogging;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

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
     * @param overwriteMode
     *
     * @return the decorated runnable
     */
    public static Runnable decorate(Runnable runnable, OverwriteMode overwriteMode) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            boolean contextWasSet = false;
            Map<String, String> threadsContextMap = MDC.getCopyOfContextMap();
            try {
                if (getKeys(contextMap).isPresent()) {
                    Optional<Set<String>> presentKeys = getKeys(threadsContextMap);
                    if (overwriteMode != OverwriteMode.PREVENT_OVERWRITE || !presentKeys.isPresent()) {
                        MDC.setContextMap(contextMap);
                        contextWasSet = true;

                        if (overwriteMode == OverwriteMode.LOG_OVERWRITE) {
                            log.warn("MDC context was set despite MDC keys being present in target thread. MDC keys present: {}", presentKeys.get()); //TODO: output the line that causes this
                        }

                        log.debug("MDC context set for runnable."); //hopefully this helps when reading logs in the future
                    } else {
                        log.warn("MDC context was not set for runnable because it was run in a thread that already had a context. MDC keys present: {}", presentKeys.get()); //TODO: output the line that causes this
                    }
                }
                runnable.run();
            } finally {
                if (contextWasSet) {
                    MDC.setContextMap(threadsContextMap); //TODO: test that this is properly reset
                }
            }
        };
    }

    private static Optional<Set<String>> getKeys(Map<String, String> contextMap) {
        if (contextMap != null && !contextMap.isEmpty()) {
            return Optional.of(contextMap.keySet());
        }
        return Optional.empty();
    }
}
