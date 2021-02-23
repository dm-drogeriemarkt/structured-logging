package de.dm.prom.structuredlogging;

import de.dm.infrastructure.logcapture.LogCapture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static ch.qos.logback.classic.Level.DEBUG;
import static ch.qos.logback.classic.Level.WARN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpringMdcTaskDecoratorUnitTest {
    ConcurrentLinkedQueue<Throwable> throwablesFromThread;

    @BeforeEach
    void setUp() {
        throwablesFromThread = new ConcurrentLinkedQueue<>();
    }

    @AfterEach
    void afterEach() {
        MDC.clear();
    }

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    @Test
    void decorateCallsRunnable() throws InterruptedException {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.PREVENT_OVERWRITE);

        Runnable runnable = Mockito.mock(Runnable.class);

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(runnable);

        Thread thread = new Thread(decoratedRunnable);
        thread.start();
        thread.join();

        verify(runnable, times(1)).run();
    }

    @Test
    void decorateFillsAndClearsMDC() throws Throwable {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.PREVENT_OVERWRITE);

        Runnable mockRunnable = Mockito.mock(Runnable.class);

        MDC.put("testKey", "testValue");

        Runnable undecoratedRunnable = collectingThrowables(() -> Assertions.assertThat(MDC.get("testKey")).isNull());

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(
                collectingThrowables(
                        () -> Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue")));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(decoratedRunnable);
        e.execute(undecoratedRunnable);
        e.execute(mockRunnable);
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        throwThrowablesFromThread();

        verify(mockRunnable, times(1)).run();
    }

    @Test
    @DisplayName("decorate does not decorate or reset thread with existing MDC context with PREVENT_OVERWRITE")
    void preventOverwriteWorks() throws Throwable {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.PREVENT_OVERWRITE);

        MDC.put("testKey", "testValue");

        Runnable mdcSettingRunnable = () -> MDC.put("existing_key", "existing_content");

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(
                collectingThrowables(() ->
                        Assertions.assertThat(MDC.get("existing_key")).isEqualTo("existing_content")));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(mdcSettingRunnable);
        e.execute(decoratedRunnable);
        e.execute(decoratedRunnable); //intentionally run twice to assert MDC has not been touched in `finally`
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        throwThrowablesFromThread();

        Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue"); //value should still be set in main thread

        logCapture.assertLogged(WARN, "^MDC context was not set for runnable because it was run in a thread that already had a context. MDC keys present: \\[existing_key\\]$")
                .thenLogged(WARN, "^MDC context was not set for runnable because it was run in a thread that already had a context. MDC keys present: \\[existing_key\\]$");
    }

    private Runnable collectingThrowables(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                throwablesFromThread.add(e);
            }
        };
    }

    private void throwThrowablesFromThread() throws Throwable {
        for (Throwable throwableFromThread : throwablesFromThread) {
            throw throwableFromThread;
        }
    }

    @Test
    @DisplayName("decorate decorates, but logs when encountering thread with existing MDC context with LOG_OVERWRITE")
    void logOverwriteLogs() throws Throwable {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.LOG_OVERWRITE);

        MDC.put("testKey", "testValue");

        Runnable mdcSettingRunnable = () -> MDC.put("existing_key", "existing_content");

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(
                collectingThrowables(() -> {
                    Assertions.assertThat(MDC.get("existing_key")).isNull();
                    Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue");
                }));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(mdcSettingRunnable);
        e.execute(decoratedRunnable);
        e.execute(decoratedRunnable); //intentionally run twice to assert that MDC.clear() has not been called
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        throwThrowablesFromThread();

        Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue"); //value should still be set in main thread

        logCapture.assertLogged(WARN, "^MDC context will be set despite MDC keys being present in target thread. MDC keys present: \\[existing_key\\]$")
                .thenLogged(DEBUG, "^MDC context set for runnable.$")
                .thenLogged(WARN, "^MDC context will be set despite MDC keys being present in target thread. MDC keys present: \\[existing_key\\]$")
                .thenLogged(DEBUG, "^MDC context set for runnable.$");
    }

    @Test
    @DisplayName("only debug is logged with LOG_OVERWRITE when no context is set in decorated thread")
    void logOverwriteLogsNothingWhenEmpty() throws Throwable {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.LOG_OVERWRITE);

        MDC.put("testKey", "testValue");

        Runnable mdcSettingRunnable = () -> MDC.clear();

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(
                collectingThrowables(() -> {
                    Assertions.assertThat(MDC.get("existing_key")).isNull();
                    Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue");
                }));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(mdcSettingRunnable);
        e.execute(decoratedRunnable);
        e.execute(decoratedRunnable); //intentionally run twice to assert that MDC.clear() has not been called
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        throwThrowablesFromThread();

        Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue"); //value should still be set in main thread

        logCapture.assertLogged(DEBUG, "^MDC context set for runnable.$")
                .thenLogged(DEBUG, "^MDC context set for runnable.$");
    }

    @Test
    @DisplayName("decorate decorates and does not log WARN when encountering thread without existing MDC context with JUST_OVERWRITE")
    void logOverweiteDoesNotLog() throws Throwable {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.JUST_OVERWRITE);

        MDC.put("testKey", "testValue");

        Runnable mdcSettingRunnable = () -> MDC.put("existing_key", "existing_content");

        Runnable runnableAssertingMdcReset = collectingThrowables(() -> Assertions.assertThat(MDC.get("existing_key")).isEqualTo("existing_content"));

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(
                collectingThrowables(() -> {
                    Assertions.assertThat(MDC.get("existing_key")).isNull();
                    Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue");
                }));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(mdcSettingRunnable);
        e.execute(decoratedRunnable);
        e.execute(decoratedRunnable); //intentionally run twice to assert that MDC.clear() has not been called
        e.execute(runnableAssertingMdcReset);
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        throwThrowablesFromThread();

        Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue"); //value should still be set in main thread

        logCapture.assertLogged(DEBUG, "^MDC context set for runnable.$")
                .thenLogged(DEBUG, "^MDC context set for runnable.$")
                .assertNothingElseLogged();
    }

}
