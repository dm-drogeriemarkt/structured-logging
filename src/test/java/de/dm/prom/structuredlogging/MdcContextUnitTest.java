package de.dm.prom.structuredlogging;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dm.infrastructure.logcapture.LogCapture;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static de.dm.prom.structuredlogging.StructuredMdcJsonProvider.JSON_PREFIX;

@Slf4j
class MdcContextUnitTest {
    private static final String SAMPLE_BEAN_JSON = "{\"name\":\"John Doe\"," +
            "\"age\":35," +
            "\"importantTime\":\"2019-01-01T13:37\"," +
            "\"importantOffsetTime\":\"2019-01-01T13:37+01:00\"," +
            "\"instant\":\"1970-01-01T00:00:01Z\"," +
            "\"localDate\":\"2020-01-01\"," +
            "\"offsetTime\":\"13:37+01:00\"," +
            "\"period\":\"P42D\"," +
            "\"zonedDateTime\":\"2019-01-01T13:37Z[UTC]\"}";

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    @Test
    void createSampleContextWithContextId() throws IOException {
        try (MdcContext c = MdcContext.of(ExampleBeanId.class, ExampleBean.getExample())) {
            assertMdcFieldContentIsCorrect("example_bean");
        }
    }

    @Test
    void createSampleContextWithClassOnly() throws IOException {
        try (MdcContext c = MdcContext.of(ExampleBean.getExample())) {
            assertMdcFieldContentIsCorrect("ExampleBean");
        }
    }

    @Test
    void createSampleContextWithKey() throws IOException {
        try (MdcContext c = MdcContext.of("explicit_key", ExampleBean.getExample())) {
            assertMdcFieldContentIsCorrect("explicit_key");
        }
    }

    private void assertMdcFieldContentIsCorrect(String mdcFieldName) throws JsonProcessingException {
        String jsonStringFromMdc = MDC.get(mdcFieldName);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode sampleBeanTree = objectMapper.readTree(SAMPLE_BEAN_JSON);

        Assertions.assertThat(jsonStringFromMdc).startsWith(JSON_PREFIX);
        JsonNode treeFromMDC = objectMapper.readTree(jsonStringFromMdc.replaceFirst(JSON_PREFIX, ""));

        Assertions.assertThat(treeFromMDC).isEqualTo(sampleBeanTree);
    }

    @Test
    void putSomethingToMDCAndRemoveWhenDone() {
        String mdcKey = new StringId().getMdcKey();
        String mdcValue = "test value"; //JSON strings are expected at this point
        try (MdcContext c = MdcContext.of(StringId.class, mdcValue)) {
            Assertions.assertThat(MDC.get(mdcKey)).isEqualTo(String.format("%s\"%s\"", JSON_PREFIX, mdcValue));
        }
        Assertions.assertThat(MDC.get(mdcKey)).isNull();
    }

    @Test
    void overwriteMDCValue() {
        String someValue = "some value";
        String someValueJson = JSON_PREFIX + "\"" + someValue + "\"";
        String otherValue = "other value";
        String otherValueJson = JSON_PREFIX + "\"" + otherValue + "\"";

        String mdcKey = new StringId().getMdcKey();

        try (MdcContext c = MdcContext.of(StringId.class, someValue)) {
            Assertions.assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
            try (MdcContext inner = MdcContext.of(StringId.class, someValue)) {
                Assertions.assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
                try (MdcContext sameIdWithDifferentValue = MdcContext.of(StringId.class, otherValue)) {
                    Assertions.assertThat(MDC.get(mdcKey)).isEqualTo(otherValueJson);
                }
                Assertions.assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
            }
            Assertions.assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
        }
        Assertions.assertThat(MDC.get(mdcKey)).isNull();

        logCapture
                .assertLogged(Level.WARN, "^Overwriting MDC key.*MdcContextUnitTest.overwriteMDCValue.*same value")
                .thenLogged(Level.ERROR, "^Overwriting MDC key.*MdcContextUnitTest.overwriteMDCValue.*value differs");
    }

    // useful for this test: ObjectMapper is not needed for comparison of serialized JSON
    public static final class StringId implements MdcContextId<String> {
        @Override
        public String getMdcKey() {
            return "string_sample";
        }
    }
}
