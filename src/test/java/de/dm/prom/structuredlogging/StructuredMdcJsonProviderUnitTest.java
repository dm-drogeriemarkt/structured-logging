package de.dm.prom.structuredlogging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.io.IOException;

@ExtendWith(OutputCaptureExtension.class)
// LogCapture is not applicable here because the actual output format of the log is relevant
@Slf4j
class StructuredMdcJsonProviderUnitTest {
    private static String SAMPLE_LOGSTASH_JSON_LOG = "{\"@version\":\"1\"," +
            "\"message\":\"something in which the ExampleBean context is relevant\"," +
            "\"logger_name\":\"de.dm.prom.structuredlogging.StructuredMdcJsonProviderUnitTest\"," +
            "\"thread_name\":\"main\"," +
            "\"level\":\"INFO\"," +
            "\"level_value\":20000," +
            "\"an_unmanaged_mdc_field\":\"some value\"," +
            "\"example_bean\":" +
            "{\"name\":\"John Doe\"," +
            "\"age\":35," +
            "\"importantTime\":\"2019-01-01T13:37\"," +
            "\"importantOffsetTime\":\"2019-01-01T13:37+01:00\"," +
            "\"instant\":\"1970-01-01T00:00:01Z\"," +
            "\"localDate\":\"2020-01-01\"," +
            "\"offsetTime\":\"13:37+01:00\"," +
            "\"period\":\"P42D\"," +
            "\"localTime\":\"13:37\"," +
            "\"duration\":\"PT42M\"," +
            "\"zonedDateTime\":\"2019-01-01T13:37Z[UTC]\"}}";

    private Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);


    @BeforeEach
    void setupLogback() throws FileNotFoundException, JoranException {
        rootLogger.iteratorForAppenders().forEachRemaining(Appender::stop);
        new ContextInitializer(rootLogger.getLoggerContext())
                .configureByResource(ResourceUtils.getURL("src/test/resources/logback-stdout-json.xml"));
    }

    @AfterEach
    void resetLogback() {
        rootLogger.iteratorForAppenders().forEachRemaining(Appender::start);
        rootLogger.getAppender("JSON-CONSOLE").stop();
    }

    @Test
    void logTest(CapturedOutput output) throws IOException {
        try (MdcContext c = MdcContext.of(ExampleBeanKeySupplier.class, ExampleBean.getExample())) {
            MDC.put("an_unmanaged_mdc_field", "some value");
            log.info("something in which the ExampleBean context is relevant");

            String consoleOutput = output.toString();  //works right now because the output contains exactly one log message

            ObjectMapper mapper = new ObjectMapper();

            JsonNode expectedJson = mapper.readTree(SAMPLE_LOGSTASH_JSON_LOG);
            JsonNode actualJson = mapper.readTree(consoleOutput);

            ((ObjectNode) actualJson).remove("@timestamp"); //because only ObjectNode provides .remove(...)

            Assertions.assertThat(actualJson).isEqualTo(expectedJson);
        }
    }
}
