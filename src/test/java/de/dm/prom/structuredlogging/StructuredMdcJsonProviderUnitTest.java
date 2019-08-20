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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.io.IOException;

@Slf4j
public class StructuredMdcJsonProviderUnitTest {
    private static String SAMPLE_LOGSTASH_JSON_LOG = "{\"@version\":\"1\",\"message\":\"something in which the ExampleBean context is relevant\",\"logger_name\":\"de.dm.prom.structuredlogging.StructuredMdcJsonProviderUnitTest\",\"thread_name\":\"main\",\"level\":\"INFO\",\"level_value\":20000,\"an_unmanaged_mdc_field\":\"some value\",\"example_bean\":{\"name\":\"John Doe\",\"age\":35,\"importantTime\":\"2019-01-01T13:37\",\"importantOffsetTime\":\"2019-01-01T13:37+01:00\"}}";
    private Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @Rule
    public final OutputCapture outputCapture = new OutputCapture();
    // LogCapture is not applicable here because the actual output format of the log is relevant

    @Before
    public void setupLogback() throws FileNotFoundException, JoranException {
        rootLogger.iteratorForAppenders().forEachRemaining(Appender::stop);
        new ContextInitializer(rootLogger.getLoggerContext())
                .configureByResource(ResourceUtils.getURL("src/test/resources/logback-stdout-json.xml"));
    }

    @After
    public void resetLogback() {
        rootLogger.iteratorForAppenders().forEachRemaining(Appender::start);
        rootLogger.getAppender("JSON-CONSOLE").stop();
    }

    @Test
    public void logTest() throws IOException {
        try (MdcContext c = MdcContext.of(ExampleBeanId.class, ExampleBean.getExample())) {
            MDC.put("an_unmanaged_mdc_field", "some value");
            log.info("something in which the ExampleBean context is relevant");
            String consoleOutput = outputCapture.toString();  //works right now because the output contains exactly one log message

            ObjectMapper mapper = new ObjectMapper();

            JsonNode expectedJson = mapper.readTree(SAMPLE_LOGSTASH_JSON_LOG);
            JsonNode actualJson = mapper.readTree(consoleOutput);

            ((ObjectNode) actualJson).remove("@timestamp"); //because only ObjectNode provides .remove(...)

            Assertions.assertThat(actualJson).isEqualTo(expectedJson);
        }
    }
}
