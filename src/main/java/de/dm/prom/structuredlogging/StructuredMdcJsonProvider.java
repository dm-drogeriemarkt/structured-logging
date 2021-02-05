package de.dm.prom.structuredlogging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * extension of MdcJsonProvider to log Json content from MDC context as actual Json and not as a string that contains json
 * <p>
 * to be used with logstash to enable structured logging
 */
public class StructuredMdcJsonProvider extends MdcJsonProvider {
    static final String JSON_PREFIX = "MDC_JSON_VALUE:";

    /**
     * get the prefix for structured (json) MDC entries. Needed if you need to check MDC contents before they are written
     * into a log - for example for testing
     *
     * @return prefix used ot mark json contents in MDC
     */
    public static String getJsonPrefix() {
        return JSON_PREFIX;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        Map<String, String> mdcProperties = event.getMDCPropertyMap();
        if (mdcProperties != null && !mdcProperties.isEmpty()) {
            if (getFieldName() != null) {
                generator.writeObjectFieldStart(getFieldName());
            }
            if (!getIncludeMdcKeyNames().isEmpty()) {
                mdcProperties = new HashMap<>(mdcProperties);
                mdcProperties.keySet().retainAll(getIncludeMdcKeyNames());
            }
            if (!getExcludeMdcKeyNames().isEmpty()) {
                mdcProperties = new HashMap<>(mdcProperties);
                mdcProperties.keySet().removeAll(getExcludeMdcKeyNames());
            }
            //so far, this is the inherited writeTo()
            writeNormalFields(generator, mdcProperties);
            writeJsonFields(generator, mdcProperties);
            //do the rest of the inherited writeTo()
            if (getFieldName() != null) {
                generator.writeEndObject();
            }
        }
    }

    private void writeNormalFields(JsonGenerator generator, Map<String, String> mdcProperties) throws IOException {
        Map<String, String> normalFields = mdcProperties.entrySet().stream()
                .filter(entry -> !isFieldWithJsonObject(entry))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        JsonWritingUtils.writeMapEntries(generator, normalFields);
    }

    private void writeJsonFields(JsonGenerator generator, Map<String, String> mdcProperties) throws IOException {
        Map<String, String> jsonFields = mdcProperties.entrySet().stream()
                .filter(StructuredMdcJsonProvider::isFieldWithJsonObject)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (Map.Entry<String, String> field : jsonFields.entrySet()) {
            generator.writeFieldName(field.getKey());
            generator.writeRawValue(field.getValue().replaceFirst(JSON_PREFIX, ""));
        }
    }

    private static boolean isFieldWithJsonObject(Map.Entry<String, String> field) {
        return field.getValue() != null && field.getValue().startsWith(JSON_PREFIX);
    }
}
