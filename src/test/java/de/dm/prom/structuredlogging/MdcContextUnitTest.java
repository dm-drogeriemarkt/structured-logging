package de.dm.prom.structuredlogging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dm.infrastructure.logcapture.LogCapture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static de.dm.infrastructure.logcapture.ExpectedException.exception;
import static de.dm.infrastructure.logcapture.LogExpectation.error;
import static de.dm.infrastructure.logcapture.LogExpectation.warn;
import static de.dm.prom.structuredlogging.MdcContext.mdc;
import static de.dm.prom.structuredlogging.StructuredMdcJsonProvider.JSON_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            "\"localTime\":\"13:37\"," +
            "\"duration\":\"PT42M\"," +
            "\"dayOfWeek\":\"MONDAY\"," +
            "\"month\":\"JANUARY\"," +
            "\"monthDay\":\"--12-24\"," +
            "\"year\":\"1984\"," +
            "\"yearMonth\":\"2000-08\"" +
            "}";

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    @AfterEach
    void resetMdc() {
        MdcContext.resetGlobalObjectMapper();
    }

    @Nested
    class ContextId {
        @Test
        void withTryBlock() throws IOException {
            try (MdcContext c = MdcContext.of(ExampleBeanKeySupplier.class, ExampleBean.getExample())) {
                assertMdcFieldContentIsCorrect("example_bean", SAMPLE_BEAN_JSON);
            }
        }

        @Test
        void withRunnable() throws IOException {
            mdc(ExampleBeanKeySupplier.class, ExampleBean.getExample(), () ->
                    assertMdcFieldContentIsCorrect("example_bean", SAMPLE_BEAN_JSON));
        }

        @Test
        void withThrowingRunnable() {
            try {
                mdc(ExampleBeanKeySupplier.class, ExampleBean.getExample(), () -> {
                    assertMdcFieldContentIsCorrect("example_bean", SAMPLE_BEAN_JSON);
                    throwIOException();
                });
                fail("expected an Exception");
            } catch (IOException e) {
                // everything is fine
            }
        }

        @Test
        void withSupplier() throws IOException {
            var actual = mdc(ExampleBeanKeySupplier.class, ExampleBean.getExample(), () -> {
                assertMdcFieldContentIsCorrect("example_bean", SAMPLE_BEAN_JSON);
                return 42;
            });
            assertThat(actual).isEqualTo(42);
        }

        @Test
        void withThrowingSupplier() {
            try {
                mdc(ExampleBeanKeySupplier.class, ExampleBean.getExample(), () -> {
                    assertMdcFieldContentIsCorrect("example_bean", SAMPLE_BEAN_JSON);
                    throwIOException();
                    return 42;
                });
                fail("expected an Exception");
            } catch (IOException e) {
                // everything is fine
            }
        }
    }

    @Nested
    class ClassOnly {
        @Test
        void withTryBlock() throws IOException {
            try (MdcContext c = MdcContext.of(ExampleBean.getExample())) {
                assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
            }
        }

        @Test
        void withRunnable() throws IOException {
            mdc(ExampleBean.getExample(), () ->
                    assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON));
        }

        @Test
        void withThrowingRunnable() {
            try {
                mdc(ExampleBean.getExample(), () -> {
                    assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
                    throwIOException();
                });
                fail("expected an Exception");
            } catch (IOException e) {
                // everything is fine
            }
        }

        @Test
        void withSupplier() throws IOException {
            var actual = mdc(ExampleBean.getExample(), () -> {
                assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
                return 42;
            });
            assertThat(actual).isEqualTo(42);
        }

        @Test
        void withThrowingSupplier() {
            try {
                mdc(ExampleBean.getExample(), () -> {
                    assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
                    throwIOException();
                    return 42;
                });
                fail("expected an Exception");
            } catch (IOException e) {
                // everything is fine
            }
        }
    }

    @Nested
    class KeyFromString {
        @Test
        void withTryBlock() throws IOException {
            try (MdcContext c = MdcContext.of("explicit_key", ExampleBean.getExample())) {
                assertMdcFieldContentIsCorrect("explicit_key", SAMPLE_BEAN_JSON);
            }
        }

        @Test
        void withRunnable() throws IOException {
            mdc("explicit_key", ExampleBean.getExample(), () ->
                    assertMdcFieldContentIsCorrect("explicit_key", SAMPLE_BEAN_JSON));
        }

        @Test
        void withThrowingRunnable() {
            try {
                mdc("explicit_key", ExampleBean.getExample(), () -> {
                    assertMdcFieldContentIsCorrect("explicit_key", SAMPLE_BEAN_JSON);
                    throwIOException();
                });
                fail("expected an Exception");
            } catch (IOException e) {
                // everything is fine
            }
        }

        @Test
        void withSupplier() throws IOException {
            var actual = mdc("explicit_key", ExampleBean.getExample(), () -> {
                assertMdcFieldContentIsCorrect("explicit_key", SAMPLE_BEAN_JSON);
                return 42;
            });
            assertThat(actual).isEqualTo(42);
        }

        @Test
        void withThrowingSupplier() {
            try {
                mdc("explicit_key", ExampleBean.getExample(), () -> {
                    assertMdcFieldContentIsCorrect("explicit_key", SAMPLE_BEAN_JSON);
                    throwIOException();
                    return 42;
                });
                fail("expected an Exception");
            } catch (IOException e) {
                // everything is fine
            }
        }
    }

    private static void throwIOException() throws IOException {
        throw new IOException("not really an IOException, just an example");
    }


    @Test
    void customObjectMapperIsUsedAndReset() throws IOException {
        ObjectMapper customObjectMapper = mock(ObjectMapper.class);
        ExampleBean objectToSerialize = ExampleBean.getExample();
        String expectedCustomJson = "{\"content\": \"custom json string\"}";

        when(customObjectMapper.writeValueAsString(objectToSerialize)).thenReturn(expectedCustomJson);

        try (MdcContext c = MdcContext.of(objectToSerialize)) {
            assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
        }

        MdcContext.setGlobalObjectMapper(customObjectMapper);

        try (MdcContext c = MdcContext.of(objectToSerialize)) {
            assertMdcFieldContentIsCorrect("ExampleBean", expectedCustomJson);
        }

        MdcContext.resetGlobalObjectMapper();

        try (MdcContext c = MdcContext.of(objectToSerialize)) {
            assertMdcFieldContentIsCorrect("ExampleBean", SAMPLE_BEAN_JSON);
        }
    }

    @Test
    void exceptionsThrownByObjectMapperAreCaughtAndLogged() throws IOException {
        ObjectMapper customObjectMapper = mock(ObjectMapper.class);
        String objectToSerialize = "I have a toString method";

        when(customObjectMapper.writeValueAsString(objectToSerialize)).thenThrow(new RuntimeException("something terrible happened"));

        MdcContext.setGlobalObjectMapper(customObjectMapper);
        try (MdcContext c = MdcContext.of(objectToSerialize)) {
            log.info("something happened");
        }

        logCapture.assertLogged(error("Object cannot be serialized\\: \"I have a toString method\"", exception().expectedMessageRegex("something terrible happened").build()));
    }

    private void assertMdcFieldContentIsCorrect(String mdcFieldName, String expectedJson) throws JsonProcessingException {
        String jsonStringFromMdc = MDC.get(mdcFieldName);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode sampleBeanTree = objectMapper.readTree(expectedJson);

        assertThat(jsonStringFromMdc).startsWith(JSON_PREFIX);
        String actualJson = jsonStringFromMdc.replaceFirst(JSON_PREFIX, "");
        JsonNode treeFromMDC = objectMapper.readTree(actualJson);

        assertThat(treeFromMDC).as("Expecting:\n<%s>\nto be equal to:\n<%s>\nbut was not.\n\n\n",
                        treeFromMDC.toPrettyString(), sampleBeanTree.toPrettyString())
                .isEqualTo(sampleBeanTree);
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
    void failedUpdate() {
        MdcContext.update(ExampleBean.getExample());

        logCapture.assertLogged(warn("^Cannot update content of MDC key ExampleBean in .*\\.failedUpdate\\(MdcContextUnitTest.java:[0-9]+\\) because it does not exist.$"));
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

        logCapture.assertLoggedInOrder(warn("^Overwriting MDC key string_sample in .*\\.accidentallyOverwriteMDCValue\\(MdcContextUnitTest.java:[0-9]+\\) " +
                        "- a context with a certain key should never contain another context with the same one. " +
                        "The value is overwritten with the same value. This is superfluous and should be removed."),
                error("^Overwriting MDC key string_sample in .*\\.accidentallyOverwriteMDCValue\\(MdcContextUnitTest.java:[0-9]+\\) " +
                        "- a context with a certain key should never contain another context with the same one. " +
                        "The old value differs from new value. This should never happen, because it messes up the MDC context. " +
                        "Old value: MDC_JSON_VALUE:\"some value\" - new value: MDC_JSON_VALUE:\"other value\""));
    }

    // useful for this test: ObjectMapper is not needed for comparison of serialized JSON
    public static final class StringKeySupplier implements MdcKeySupplier<String> {
        @Override
        public String getMdcKey() {
            return "string_sample";
        }
    }
}
