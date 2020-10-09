package de.dm.prom.structuredlogging;

import de.dm.infrastructure.logcapture.LogCapture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static ch.qos.logback.classic.Level.WARN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpringMdcTaskDecoratorUnitTest {

    private SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator();

    @AfterEach
    void afterEach() {
        MDC.clear();
    }

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    @Test
    void decorateCallsRunnable() throws InterruptedException {
        Runnable runnable = Mockito.mock(Runnable.class);

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(runnable);

        Thread thread = new Thread(decoratedRunnable);
        thread.start();
        thread.join();

        verify(runnable, times(1)).run();
    }

    @Test
    void decorateFillsAndClearsMDC() throws InterruptedException {
        Runnable mockRunnable = Mockito.mock(Runnable.class);

        MDC.put("testKey", "testValue");

        Runnable undecoratedRunnable = () -> Assertions.assertThat(MDC.get("testKey")).isNull();

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(() -> Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue"));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(decoratedRunnable);
        e.execute(undecoratedRunnable);
        e.execute(mockRunnable);
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        verify(mockRunnable, times(1)).run();
    }

    @Test
    void decorateDoesNotDecorateOrClearThreadWithMdcContextAndLogsWarning() throws InterruptedException {
        MDC.put("testKey", "testValue");

        Runnable mdcSettingRunnable = () -> MDC.put("some", "context");

        Runnable decoratedRunnable = mdcTaskDecorator.decorate(() -> Assertions.assertThat(MDC.get("some")).isEqualTo("context"));

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        e.execute(mdcSettingRunnable);
        e.execute(decoratedRunnable);
        e.execute(decoratedRunnable); //intentionally run twice to assert that MDC.clear() has not been called
        e.shutdown();
        e.awaitTermination(10, SECONDS);

        Assertions.assertThat(MDC.get("testKey")).isEqualTo("testValue"); //value should still be set in main thread

        logCapture.assertLogged(WARN, "^MDC context was not set for runnable because it was run in a thread that already had a context.$")
                .thenLogged(WARN, "^MDC context was not set for runnable because it was run in a thread that already had a context.$");
    }

}
