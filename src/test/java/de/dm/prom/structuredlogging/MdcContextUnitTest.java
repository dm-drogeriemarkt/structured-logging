package de.dm.prom.structuredlogging;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dm.infrastructure.logcapture.LogCapture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static ch.qos.logback.classic.Level.WARN;
import static de.dm.prom.structuredlogging.StructuredMdcJsonProvider.JSON_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

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
            "\"zonedDateTime\":\"2019-01-01T13:37Z[UTC]\"," +
            "\"localTime\":\"13:37\"" +
            "}";

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    @Test
    void createSampleContextWithContextId() throws IOException {
        try (MdcContext c = MdcContext.of(ExampleBeanKeySupplier.class, ExampleBean.getExample())) {
            assertMdcFieldContentIsCorrect("example_bean", SAMPLE_BEAN_JSON);
        }
    }

    @Test
    void createSampleContextWithClassOnly() throws IOException {
        try (MdcContext c = MdcContext.of(ExampleBean.getExample())) {
            assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
        }
    }

    @Test
    void createSampleContextWithKey() throws IOException {
        try (MdcContext c = MdcContext.of("explicit_key", ExampleBean.getExample())) {
            assertMdcFieldContentIsCorrect("explicit_key", SAMPLE_BEAN_JSON);
        }
    }

    private void assertMdcFieldContentIsCorrect(String mdcFieldName, String expectedJson) throws JsonProcessingException {
        String jsonStringFromMdc = MDC.get(mdcFieldName);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode sampleBeanTree = objectMapper.readTree(expectedJson);

        assertThat(jsonStringFromMdc).startsWith(JSON_PREFIX);
        JsonNode treeFromMDC = objectMapper.readTree(jsonStringFromMdc.replaceFirst(JSON_PREFIX, ""));

        assertThat(treeFromMDC).isEqualTo(sampleBeanTree);
    }

    @Test
    void putSomethingToMDCUpdateAndRemoveWhenDone() {
        String mdcKey = new StringKeySupplier().getMdcKey();
        String mdcValue = "test value"; //JSON strings are expected at this point
        String updatedValue = "updated value";
        try (MdcContext c = MdcContext.of(StringKeySupplier.class, mdcValue)) {
            assertThat(MDC.get(mdcKey)).isEqualTo(String.format("%s\"%s\"", JSON_PREFIX, mdcValue));
            MdcContext.update(StringKeySupplier.class, updatedValue);
            assertThat(MDC.get(mdcKey)).isEqualTo(String.format("%s\"%s\"", JSON_PREFIX, updatedValue));
        }
        assertThat(MDC.get(mdcKey)).isNull();
    }

    @Test
    void withSuppliedStringAsKey() throws Exception {
        ExampleBean exampleBean = ExampleBean.getExample();
        String mdcKey = "custom_key";

        try (MdcContext c = MdcContext.of(mdcKey, exampleBean)) {
            assertMdcFieldContentIsCorrect(mdcKey, SAMPLE_BEAN_JSON);
            String updatedName = "Jack Frost";
            exampleBean.setName(updatedName);
            MdcContext.update(mdcKey, exampleBean);
            assertMdcFieldContentIsCorrect(mdcKey, SAMPLE_BEAN_JSON.replace("John Doe", updatedName));
        }
        assertThat(MDC.get(mdcKey)).isNull();
    }

    @Test
    void withSimpleNameAsKey() throws Exception {
        ExampleBean exampleBean = ExampleBean.getExample();
        String mdcKey = "ExampleBean";

        try (MdcContext c = MdcContext.of(exampleBean)) {
            assertMdcFieldContentIsCorrect(mdcKey, SAMPLE_BEAN_JSON);
            String updatedName = "Jack Frost";
            exampleBean.setName(updatedName);
            MdcContext.update(exampleBean);
            assertMdcFieldContentIsCorrect(mdcKey, SAMPLE_BEAN_JSON.replace("John Doe", updatedName));
        }
        assertThat(MDC.get(mdcKey)).isNull();
    }

    @Test
    void failedUpdate() throws Exception {
        MdcContext.update(ExampleBean.getExample());

        logCapture.assertLogged(WARN, "^Cannot update content of MDC key ExampleBean in .*\\.failedUpdate\\(MdcContextUnitTest.java:[0-9]+\\) because it does not exist.$");
    }

    @Test
    void accidentallyOverwriteMDCValue() {
        String someValue = "some value";
        String someValueJson = JSON_PREFIX + "\"" + someValue + "\"";
        String otherValue = "other value";
        String otherValueJson = JSON_PREFIX + "\"" + otherValue + "\"";

        String mdcKey = new StringKeySupplier().getMdcKey();

        try (MdcContext c = MdcContext.of(StringKeySupplier.class, someValue)) {
            assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
            try (MdcContext inner = MdcContext.of(StringKeySupplier.class, someValue)) {
                assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
                try (MdcContext sameIdWithDifferentValue = MdcContext.of(StringKeySupplier.class, otherValue)) {
                    assertThat(MDC.get(mdcKey)).isEqualTo(otherValueJson);
                }
                assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
            }
            assertThat(MDC.get(mdcKey)).isEqualTo(someValueJson);
        }
        assertThat(MDC.get(mdcKey)).isNull();

        logCapture
                .assertLogged(WARN, "^Overwriting MDC key string_sample in .*\\.accidentallyOverwriteMDCValue\\(MdcContextUnitTest.java:[0-9]+\\) " +
                        "- a context with a certain key should never contain another context with the same one. " +
                        "The value is overwritten with the same value. This is superfluous and should be removed.")
                .thenLogged(Level.ERROR, "^Overwriting MDC key string_sample in .*\\.accidentallyOverwriteMDCValue\\(MdcContextUnitTest.java:[0-9]+\\) " +
                        "- a context with a certain key should never contain another context with the same one. " +
                        "The old value differs from new value. This should never happen, because it messes up the MDC context. " +
                        "Old value: MDC_JSON_VALUE:\"some value\" - new value: MDC_JSON_VALUE:\"other value\"");
    }

    // useful for this test: ObjectMapper is not needed for comparison of serialized JSON
    public static final class StringKeySupplier implements MdcKeySupplier<String> {
        @Override
        public String getMdcKey() {
            return "string_sample";
        }
    }
}
