package de.dm.prom.structuredlogging;

import de.dm.infrastructure.logcapture.LogCapture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static de.dm.infrastructure.logcapture.ExpectedMdcEntry.mdc;
import static de.dm.infrastructure.logcapture.LogExpectation.info;
import static de.dm.infrastructure.logcapture.LogExpectation.warn;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
class TaskDecoratorIntegrationTest {
    @RegisterExtension
    LogCapture logCapture = LogCapture.forCurrentPackage();

    @Test
    void assertMdcWithDecoratedThreadsOverwriting() throws InterruptedException {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.LOG_OVERWRITE);

        Runnable mdcSettingRunnable = () -> MDC.put("existing_key", "existing_content");

        try (MdcContext c = MdcContext.of(ExampleBean.getExample())) {
            log.info("message 1");

            Runnable decoratedRunnable = mdcTaskDecorator.decorate(() -> log.info("message from decorated runnable"));

            Runnable undecoratedRunnable = () -> log.info("message from un-decorated runnable");

            ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            e.execute(mdcSettingRunnable);
            e.execute(decoratedRunnable);
            e.execute(undecoratedRunnable);
            e.shutdown();
            e.awaitTermination(10, SECONDS);
        }


        logCapture.assertLoggedInOrder(info("^message 1$", mdc("ExampleBean", "John Doe")),
                warn("MDC context will be set despite MDC keys being present in target thread. MDC keys present: \\[existing_key\\]"),
                info("^message from decorated runnable", mdc("ExampleBean", "John Doe")),
                info("^message from un-decorated runnable", mdc("existing_key", "existing_content")));
    }

    @Test
    void assertMdcWithDecoratedThreadsEmptyContext() throws InterruptedException {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.LOG_OVERWRITE);

        Runnable mdcSettingRunnable = MDC::clear;

        try (MdcContext c = MdcContext.of(ExampleBean.getExample())) {
            log.info("message 1");

            Runnable decoratedRunnable = mdcTaskDecorator.decorate(() -> log.info("message from decorated runnable"));

            Runnable undecoratedRunnable = () -> log.info("message from un-decorated runnable");

            ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            e.execute(mdcSettingRunnable);
            e.execute(decoratedRunnable);
            e.shutdown();
            e.awaitTermination(10, SECONDS);
        }


        logCapture.assertLoggedInOrder(
                info("^message 1$", mdc("ExampleBean", "John Doe")),
                info("^message from decorated runnable", mdc("ExampleBean", "John Doe")));
    }

    @Test
    void assertMdcWithDecoratedThreadsNotOverwriting() throws InterruptedException {
        SpringMdcTaskDecorator mdcTaskDecorator = new SpringMdcTaskDecorator(OverwriteStrategy.PREVENT_OVERWRITE);

        Runnable mdcSettingRunnable = () -> MDC.put("existing_key", "existing_content");

        try (MdcContext c = MdcContext.of(ExampleBean.getExample())) {
            log.info("message 1");

            Runnable decoratedRunnable = mdcTaskDecorator.decorate(() -> log.info("message from runnable"));
            ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            e.execute(mdcSettingRunnable);
            e.execute(decoratedRunnable);
            e.shutdown();
            e.awaitTermination(10, SECONDS);
        }

        logCapture.assertLoggedInOrder(info("^message 1$", mdc("ExampleBean", "John Doe")),
                warn("MDC context was not set for runnable because it was run in a thread that already had a context. MDC keys present: \\[existing_key\\]"),
                info("^message from runnable", mdc("existing_key", "existing_content")));
    }
}
