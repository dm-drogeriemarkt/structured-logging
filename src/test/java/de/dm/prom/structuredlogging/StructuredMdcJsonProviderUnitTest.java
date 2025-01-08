package de.dm.prom.structuredlogging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
// LogCapture is not applicable here because the actual output format of the log is relevant
@Slf4j
class StructuredMdcJsonProviderUnitTest {
    private static final String SAMPLE_LOGSTASH_JSON_LOG = """
            {
                "@version":"1",
                "message":"something in which the ExampleBean context is relevant",
                "logger_name":"de.dm.prom.structuredlogging.StructuredMdcJsonProviderUnitTest",
                "thread_name":"main",
                "level":"INFO",
                "level_value":20000,
                "an_unmanaged_mdc_field":"some value",
                "example_bean": {
                    "name":"John Doe",
                    "age":35,
                    "importantTime":"2019-01-01T13:37",
                    "importantOffsetTime":"2019-01-01T13:37+01:00",
                    "instant":"1970-01-01T00:00:01Z",
                    "localDate":"2020-01-01",
                    "offsetTime":"13:37+01:00",
                    "period":"P42D",
                    "zonedDateTime":"2019-01-01T13:37Z[UTC]",
                    "localTime":"13:37",
                    "duration":"PT42M",
                    "dayOfWeek":"MONDAY",
                    "month":"JANUARY",
                    "monthDay":"--12-24",
                    "year":"1984",
                    "yearMonth":"2000-08",
                    "emptyOptional" : null,
                    "nonEmptyOptional" : "Hello"
                }
            }
            """;

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);


    @BeforeEach
    void setupLogback() throws FileNotFoundException, JoranException {
        rootLogger.iteratorForAppenders().forEachRemaining(Appender::stop);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(ResourceUtils.getURL("src/test/resources/logback-stdout-json.xml"));
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

            var consoleOutputLines = output.toString().split(System.lineSeparator());
            var jsonLogLines = Arrays.stream(consoleOutputLines).filter(line -> line.startsWith("{")).toList();

            assertThat(jsonLogLines).withFailMessage("no JSON log lines found").isNotEmpty();

            ObjectMapper mapper = new ObjectMapper();

            JsonNode expectedJson = mapper.readTree(SAMPLE_LOGSTASH_JSON_LOG);
            JsonNode actualJson = mapper.readTree(jsonLogLines.get(0));

            ((ObjectNode) actualJson).remove("@timestamp"); //because only ObjectNode provides .remove(...)

            assertThat(actualJson.toPrettyString()).isEqualTo(expectedJson.toPrettyString());
        }
    }
}
