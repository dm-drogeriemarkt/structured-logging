package de.dm.prom.structuredlogging;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MdcTaskDecoratorUnitTest {

    private MdcTaskDecorator mdcTaskDecorator = new MdcTaskDecorator();

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

        MDC.clear();
    }

}
