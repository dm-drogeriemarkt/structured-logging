package de.dm.prom.structuredlogging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * a context that can be used to wrap MDC information in a try-with-resources block.
 * When that block is done, the respective information is removed from MDC
 * <p>
 * see the documentation for an example
 */
@Slf4j
public final class MdcContext implements java.io.Closeable {
    private final String oldValue; //MDC value outside this context
    private final String key;

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
    private static Optional<ObjectMapper> customObjectMapper = Optional.empty();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, ToStringSerializer.instance);
        module.addSerializer(LocalDate.class, ToStringSerializer.instance);
        module.addSerializer(LocalDateTime.class, ToStringSerializer.instance);
        module.addSerializer(OffsetDateTime.class, ToStringSerializer.instance);
        module.addSerializer(OffsetTime.class, ToStringSerializer.instance);
        module.addSerializer(Period.class, ToStringSerializer.instance);
        module.addSerializer(ZonedDateTime.class, ToStringSerializer.instance);
        module.addSerializer(LocalTime.class, ToStringSerializer.instance);
        module.addSerializer(Duration.class, ToStringSerializer.instance);
        module.addSerializer(MonthDay.class, ToStringSerializer.instance);
        module.addSerializer(Year.class, ToStringSerializer.instance);
        module.addSerializer(YearMonth.class, ToStringSerializer.instance);

        DEFAULT_OBJECT_MAPPER.registerModule(module);
        DEFAULT_OBJECT_MAPPER.registerModule(new Jdk8Module());
    }

    /**
     * set a custom object mapper globally for MdcContext.of(...), which means that all subsequent
     * calls will use this object mapper to serialize the MDC value
     *
     * @param objectMapper custom object mapper
     */
    public static void setGlobalObjectMapper(ObjectMapper objectMapper) {
        customObjectMapper = Optional.of(objectMapper);
    }

    /**
     * reset the object mapper for MdcContext.of(...) globally to the default
     */
    public static void resetGlobalObjectMapper() {
        customObjectMapper = Optional.empty();
    }

    /**
     * create an MDC context
     * <p>
     * use this to construct an MDC context ensuring that the same key is always used for a certain type
     *
     * @param keySupplier {@link de.dm.prom.structuredlogging.MdcKeySupplier} implementation to describe which MDC key to use
     * @param mdcValue the object to write to MDC
     * @param <T> the type of the object to serialize
     * @param <S> an implementation of MdcKeySupplier that supplies the MDC key for a certain type
     *
     * @return an MDC context to use in a try-with-resources block
     */
    public static <T, S extends MdcKeySupplier<T>> MdcContext of(Class<S> keySupplier, T mdcValue) {
        try {
            MdcKeySupplier<T> id = keySupplier.getDeclaredConstructor().newInstance();
            return new MdcContext(id.getMdcKey(), mdcValue);
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            log.error("Cannot put key of type {} to MDC because no new instance of {} can be created: {}",
                    mdcValue.getClass().getSimpleName(), keySupplier.getSimpleName(), e.getMessage());
        }
        return new MdcContext(mdcValue.getClass().getSimpleName(), mdcValue);
    }

    /**
     * create an MDC context
     * <p>
     * use this to construct an MDC context with a manually defined key
     * <p>
     * See {@link MdcContext#of(Class, Object)} if you want to ensure that the same key is always used for a certain type
     *
     * @param mdcKey MDC key to use
     * @param mdcValue the object to write to MDC
     *
     * @return an MDC context to use in a try-with-resources block
     */
    public static MdcContext of(String mdcKey, Object mdcValue) {
        return new MdcContext(mdcKey, mdcValue);
    }

    /**
     * create an MDC context
     * <p>
     * use this to construct an MDC context that uses the serialized object's simpleName as the MDC key
     * <p>
     * See {@link MdcContext#of(Class, Object)} if you want to ensure that the same MDC key is always used for a certain type
     * <p>
     * See {@link MdcContext#of(String, Object)} if you want to manually define the MDC key
     *
     * @param mdcValue the object to write to MDC
     *
     * @return an MDC context to use in a try-with-resources block
     */
    public static MdcContext of(Object mdcValue) {
        return new MdcContext(mdcValue.getClass().getSimpleName(), mdcValue);
    }

    /**
     * execute a supplier with MDC Context
     *
     * @param mdcValue the object to write to MDC
     * @param supplier the callback to execute with the MDC Context
     * @param <T> type of return value
     * @param <E> type of checked Exception thrown (if any, can usually be inferred)
     *
     * @return the return value of the callback
     *
     * @throws E checked exception thrown by callback
     */
    public static <T, E extends Throwable> T mdc(Object mdcValue, MdcSupplier<T, E> supplier) throws E {
        try (MdcContext c = MdcContext.of(mdcValue)) {
            return supplier.get();
        }
    }

    /**
     * execute a runnable with MDC Context
     *
     * @param mdcValue the object to write to MDC
     * @param runnable the callback to execute with the MDC Context
     * @param <E> type of checked Exception thrown (if any, can usually be inferred)
     *
     * @throws E checked exception thrown by callback
     */
    public static <E extends Throwable> void mdc(Object mdcValue, MdcRunnable<E> runnable) throws E {
        try (MdcContext c = MdcContext.of(mdcValue)) {
            runnable.run();
        }
    }

    /**
     * execute a supplier with MDC Context
     *
     * @param mdcKey MDC key to use
     * @param mdcValue the object to write to MDC
     * @param supplier the callback to execute with the MDC Context
     * @param <T> type of return value
     * @param <E> type of checked Exception thrown (if any, can usually be inferred)
     *
     * @return the return value of the callback
     *
     * @throws E checked exception thrown by callback
     */
    public static <T, E extends Throwable> T mdc(String mdcKey, Object mdcValue, MdcSupplier<T, E> supplier) throws E {
        try (MdcContext c = MdcContext.of(mdcKey, mdcValue)) {
            return supplier.get();
        }
    }

    /**
     * execute a runnable with MDC Context
     *
     * @param mdcKey MDC key to use
     * @param mdcValue the object to write to MDC
     * @param runnable the callback to execute with the MDC Context
     * @param <E> type of checked Exception thrown (if any, can usually be inferred)
     *
     * @throws E checked exception thrown by callback
     */
    public static <E extends Throwable> void mdc(String mdcKey, Object mdcValue, MdcRunnable<E> runnable) throws E {
        try (MdcContext c = MdcContext.of(mdcKey, mdcValue)) {
            runnable.run();
        }
    }

    /**
     * execute a supplier with MDC Context
     *
     * @param keySupplier {@link de.dm.prom.structuredlogging.MdcKeySupplier} implementation to describe which MDC key to use
     * @param mdcValue the object to write to MDC
     * @param supplier the callback to execute with the MDC Context
     * @param <T> type of return value
     * @param <E> type of checked Exception thrown (if any, can usually be inferred)
     * @param <M> the type of the object to serialize
     * @param <S> an implementation of MdcKeySupplier that supplies the MDC key for a certain type
     *
     * @return the return value of the callback
     *
     * @throws E checked exception thrown by callback
     */
    public static <T, E extends Throwable, M, S extends MdcKeySupplier<M>> T mdc(Class<S> keySupplier, M mdcValue, MdcSupplier<T, E> supplier) throws E {
        try (MdcContext c = MdcContext.of(keySupplier, mdcValue)) {
            return supplier.get();
        }
    }

    /**
     * execute a runnable with MDC Context
     *
     * @param keySupplier {@link de.dm.prom.structuredlogging.MdcKeySupplier} implementation to describe which MDC key to use
     * @param mdcValue the object to write to MDC
     * @param runnable the callback to execute with the MDC Context
     * @param <E> type of checked Exception thrown (if any, can usually be inferred)
     * @param <M> the type of the object to serialize
     * @param <S> an implementation of MdcKeySupplier that supplies the MDC key for a certain type
     *
     * @throws E checked exception thrown by callback
     */
    public static <E extends Throwable, M, S extends MdcKeySupplier<M>> void mdc(Class<S> keySupplier, M mdcValue, MdcRunnable<E> runnable) throws E {
        try (MdcContext c = MdcContext.of(keySupplier, mdcValue)) {
            runnable.run();
        }
    }

    /**
     * update an existing MDC context
     * <p>
     * use this to update an MDC context ensuring that the same key is always used for a certain type
     * <p>
     * will log a WARNing instead if no matching MDC contents are present
     *
     * @param keySupplier {@link MdcKeySupplier} implementation to describe which MDC key to use
     * @param mdcValue the object to write to MDC
     * @param <T> the type of the object to serialize
     * @param <S> an implementation of MdcKeySupplier that supplies the MDC key for a certain type
     */
    public static <T, S extends MdcKeySupplier<T>> void update(Class<S> keySupplier, T mdcValue) {
        try {
            MdcKeySupplier<T> id = keySupplier.getDeclaredConstructor().newInstance();
            updateMdcContent(id.getMdcKey(), toJson(mdcValue));
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            log.error("Cannot update MDC with type {} because no new instance of {} can be created: {}",
                    mdcValue.getClass().getSimpleName(), keySupplier.getSimpleName(), e.getMessage());
        }
    }

    /**
     * update an existing MDC context
     * <p>
     * use this to update an MDC context with a manually defined key
     * <p>
     * will log a WARNing instead if no matching MDC contents are present
     * <p>
     * See {@link MdcContext#of(Class, Object)} if you want to ensure that the same key is always used for a certain type
     *
     * @param mdcKey MDC key to use
     * @param mdcValue the object to write to MDC
     */
    public static void update(String mdcKey, Object mdcValue) {
        updateMdcContent(mdcKey, toJson(mdcValue));
    }

    /**
     * update an existing MDC context
     * <p>
     * use this to update an MDC context that uses the serialized object's simpleName as the MDC key
     * <p>
     * will log a WARNing instead if no matching MDC contents are present
     * <p>
     * See {@link MdcContext#of(Class, Object)} if you want to ensure that the same MDC key is always used for a certain type
     * <p>
     * See {@link MdcContext#of(String, Object)} if you want to manually define the MDC key
     *
     * @param mdcValue the object to write to MDC
     */
    public static void update(Object mdcValue) {
        updateMdcContent(mdcValue.getClass().getSimpleName(), toJson(mdcValue));
    }

    private MdcContext(String key, Object value) {
        this.key = key;
        oldValue = putToMDCwithOverwriteWarning(key, toJson(value));
    }

    @Override
    public void close() {
        if (oldValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, oldValue);
        }
    }

    private static String toJson(Object object) {
        String objectToJson = "{\"json_error\":\"Unserializable Object.\"}";
        //needs to be an object, not a string, for Kibana. Otherwise, Kibana will throw away the log entry because the field has the wrong type.

        try {
            objectToJson = getObjectMapper().writeValueAsString(object);
        } catch (Exception e) {
            log.error("Object cannot be serialized: \"{}\"", object, e);
        }
        return objectToJson;
    }

    private static ObjectMapper getObjectMapper() {
        return customObjectMapper.orElse(DEFAULT_OBJECT_MAPPER);
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
        } else {
            log.warn("{} The value is overwritten with the same value. This is superfluous and should be removed.", message);
        }
    }

    private static void updateMdcContent(String key, String newValue) {
        newValue = StructuredMdcJsonProvider.JSON_PREFIX + newValue;
        String oldValue = MDC.get(key);
        if (oldValue == null) {
            logFailedUpdate(key);
        } else {
            MDC.put(key, newValue);
        }
    }

    private static void logFailedUpdate(String key) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[4]; //always [4] because of call depth
        log.warn("Cannot update content of MDC key {} in {}.{}({}:{}) because it does not exist.",
                key, caller.getClassName(), caller.getMethodName(), caller.getFileName(), caller.getLineNumber());
    }
}
