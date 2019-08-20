package de.dm.prom.structuredlogging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * a context that can be used to wrap MDC information in a try-with-resources block.
 * When that block is done, the respective information is removed from MDC
 * <p>
 * see the documentation for an example
 *
 * @param <SerializedType> the type of the object to serialize
 * @param <MdcIdType> an implementation of MdcContextId that defines the field into which the serialized object is written
 */
@Slf4j
public final class MdcContext<SerializedType, MdcIdType extends MdcContextId<SerializedType>> implements java.io.Closeable {
    private final String oldValue; //MDC value outside this context
    private final String key;

    /**
     * create an MDC context
     * <p>
     * this is a static constructor to enforce generic type safety
     *
     * @param type {@link MdcContextId} implementation to describe to which field to serialize the value
     * @param mdcValue the object to write to MDC
     * @param <SerializedType> an implementation of MdcContextId that defines the field into which the serialized object is written
     * @param <MdcIdType> the type of the object to serialize
     *
     * @return an MDC context to use in a try-with-resources block
     */
    public static <SerializedType, MdcIdType extends MdcContextId<SerializedType>> MdcContext<SerializedType, MdcIdType> of(Class<MdcIdType> type, SerializedType mdcValue) {
        try {
            MdcContextId<SerializedType> id = type.newInstance();
            return new MdcContext<>(id.getMdcKey(), toJson(mdcValue));
        } catch (IllegalAccessException | InstantiationException e) {
            log.error("Cannot put key of type {} to MDC because no new instance of {} can be created: {}",
                    mdcValue.getClass().getSimpleName(), type.getSimpleName(), e.getMessage());
        }
        return new MdcContext<>(mdcValue.getClass().getSimpleName(), mdcValue.toString());
    }

    private MdcContext(String key, String value) {
        this.key = key;
        oldValue = putToMDCwithOverwriteWarning(key, value);
    }

    @Override
    public void close() {
        if (oldValue == null) {
            MDC.remove(key);
        }
        else {
            MDC.put(key, oldValue);
        }
    }

    private static String toJson(Object object) {
        String objectToJson = "{\"json_error\":\"Unserializable Object.\"}"; //needs to be an object, not a string, for Kibana. Otherwise, Kibana will throw away the log entry because the field has the wrong type.
        try {
            objectToJson = getObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Object cannot be serialized {}. ({})", object, e);
        }
        return objectToJson;
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(OffsetDateTime.class, new ToStringSerializer());
        module.addSerializer(LocalDateTime.class, new ToStringSerializer());
        objectMapper.registerModule(module);
        return objectMapper;
    }

    private static String putToMDCwithOverwriteWarning(String key, String newValue) {
        newValue = StructuredMdcJsonProvider.JSON_PREFIX + newValue;
        String oldValue = MDC.get(key);
        if (oldValue != null) {
            logOverwriting(key, newValue, oldValue);
        }
        MDC.put(key, newValue);
        return oldValue;
    }

    private static void logOverwriting(String key, String value, String oldValue) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[5]; //always [5] because it is always accessed through MdcContext's constructor
        String message = String.format("Overwriting MDC key %s in %s.%s(%s:%s) - a context with a certain key should never contain another context with the same one.",
                key, caller.getClassName(), caller.getMethodName(), caller.getFileName(), caller.getLineNumber());
        if (!oldValue.equals(value)) {
            log.error("{} The old value differs from new value. This should never happen, because it messes up the MDC context. Old value: {} - new value: {}",
                    message, oldValue, value);
        }
        else {
            log.warn("{} The value is overwritten with the same value. This is superfluous and should be removed.", message);
        }
    }
}
