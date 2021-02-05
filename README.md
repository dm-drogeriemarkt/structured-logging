# Structured Logging [<img src="https://opensourcelogos.aws.dmtech.cloud/dmTECH_opensource_logo.svg" height="20" width="130">](https://dmtech.de/) [![Build Status](https://travis-ci.org/dm-drogeriemarkt/structured-logging.svg?branch=master)](https://travis-ci.org/dm-drogeriemarkt/structured-logging) 

Structured Logging is a library that

* helps augment log messages with information about the context in which they happen.
* manages
  * the lifetime of the context information
  * serialization of the context information

---

**Table of Contents**

* [Example](#example)
* [Advantages](#advantages)
  * [Advantages over plain logging](#advantages-over-plain-logging)
  * [Advantages over using MDC directly](#advantages-over-using-mdc-directly)
* [Prerequisites](#prerequisites)
* [Getting Started](#getting-started)
  * [Step 1: Add structured-logging as a dependency](#step-1-add-structured-logging-as-a-dependency)
  * [Step 2: Configure Logback](#step-2-configure-logback)
  * [Step 3: Put Objects into the logging context](#step-3-put-objects-into-the-logging-context)
* [Advanced usage](#advanced-usage)
  * [Define how Objects should be named in MDC](#define-how-objects-should-be-named-in-mdc)
    * [Manual key definition](#manual-key-definition)
    * [Manual key definition via MdcKeySupplier](#manual-key-definition-via-mdckeysupplier)
  * [Excluding properties from serialization](#excluding-properties-from-serialization)
  * [Configure a Task Decorator in Spring](#configure-a-task-decorator-in-spring)
  * [Test your logging](#test-your-logging)
* [Changes](#changes)
  * [2.0.0](#200)
  * [1.0.3](#103)

---

## Example

If there are log messages that happen in the context of an order, you may want to attach information about that order to these log messages.

```java
    try (var c = LoggingContext.of(incomingOrder)) {
        log.info("A new order has come in.");
        if (isValid(incomingOrder)) {
            prepareForDelivery(incomingOrder);
        }
        // if isValid() and prepareForDelivery() log something, 
        // those log messages will also have the context of that order
    }
```

Used like this, the `incomingOrder` will be attached to the log message generated in this `try`block, including 

* the message from `log.info("A new order has come in.")` .
* all messages logged by `prepareForDelivery(...)` and the methods called by that method

Here's how this would look in Kibana:

![Kibana-Example](docs/structured-logging-kibana.png)

## Advantages

This approach has various advantages over both plain logging as well as using MDC directly.

### Advantages over plain logging

* If you log context information, you can easily trace the order from the example above by filtering by `incomingOrder.id : 1234`
* Search becomes easier if die log message itself does not vary that much (just search for `message : "A new order has come in."`)
* Because the type of fields can be inferred, you can for example search by `incomingOrder.paymentDate > 2020-01-01`
* Everything that is relevant in the context of the log message is attached to it, even if you didn't think of it when writing that specific log message.
* You can do alerting and monitoring based on specific MDC values. Want to know the summary monetary value of those `incomingOrder`s in the example above? You can now do that.

### Advantages over using MDC directly

Structured Logging adds crucial features missing from plain [MDC](http://logback.qos.ch/manual/mdc.html), even when using MDC's [putCloseable](http://www.slf4j.org/api/org/slf4j/MDC.html#putCloseable(java.lang.String,java.lang.String)):

* Proper serialization of complex objects and their fields is taken care of. MDC itself does not support a hierarchical structure, although most log processors like logstash do support it.
* MDC is properly cleared, even if the key has been set before. In that case, it's reset to what it was before the `try` block.
* If a task in another thread is started, context information is retained because it is still relevant. This is solved by providing a custom Task Decorator (see below).

## Prerequisites

For creating logs:

* You need to use **Slf4j** as your logging facade.
* You need to use **logback** as your logging implementation.
* **Optionally**, you can use **Spring (Boot)** which uses both of these per default and already lets you register Task Decorators. But other frameworks (or no framework) work just as well.

For consuming logs:

* Works with log consumers that can digest JSON logs, like the **ELK Stack** or **Datadog**. The consumer needs to support hierarchical data in a log message. **Greylog does not** support this at the moment, for example.

## Getting Started

### Step 1: Add structured-logging as a dependency

If you use maven, add this to your pom.xml:

```pom.xml
<dependency>
    <groupId>de.dm.infrastructure</groupId>
    <artifactId>structured-logging</artifactId>
    <version>2.0.0</version>
    <scope>test</scope>
</dependency>
```

### Step 2: Configure Logback

To log the whole log message including the object you put into MDC as Json, you need to configure Logback to use the **LogstashEncoder** and **StructuredMdcJsonProvider**.

The following configuration can be used as an example:

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <appender name="JSON_FILE" class="ch.qos.logback.core.FileAppender">
        <!-- use LogstashEncoder to log as JSON -->
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- use StructuredMdcJsonProvider to log structured data as json in MDC fields -->
            <provider class="de.dm.infrastructure.structuredlogging.StructuredMdcJsonProvider"/>
        </encoder>
        <file>${LOG_LOCATION}</file>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_FILE"/>
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>

```

### Step 3: Put Objects into the logging context

Create a new `LoggingContext` in a try-with-resources statement to define the scope in which context information should be set:

```java
log.info("a message without context");

TimeMachine timeMachine = TimeMachineBuilder.build();

try (var c = LoggingContext.of(timeMachine)) {
    log.info("time machine found. Trying to use it");

    travelSomewhereWith(timeMachine);
    
    timeMachine.setFluxFactor(42);

    LoggingContextupdate(timeMachine);

    travelSomewhereWith(timeMachine);
}

log.info("another message without context");
}

void travelSomewhereWith(TimeMachine timeMachine) {
    log.info("Where we’re going, we don’t need roads.");

    ...
}
```

## Advanced usage

### Define how Objects should be named in MDC

There are three ways to define the MDC key for the objects you put into the context:

1. Use the default (the shortName of the type, as seen in the Basic Usage)
1. Define it manually
1. Provide a MdcKeySupplier to make sure you always give the same key to the same type

#### Manual key definition

```java
try (var c = LoggingContext.of("de_lorean", timeMachine)) {
    ...
    
    timeMachine.setFluxFactor(42);

    LoggingContextupdate("de_lorean", timeMachine);
}
```

#### Manual key definition via MdcKeySupplier

```java
// must be public and have a public, non-parameterized constructor
public final class TimeMachineKey implements MdcKeySupplier<TimeMachine>
    @Override
    public String getMdcKey() {
        return "de_lorean";
    }
}

...

//LoggingContext.of(...) is defined so that you can only use TimeMachineKey with a TimeMachine.
try (var c = LoggingContext.of(TimeMachineKey.class, timeMachine)) {
    ...
    
    timeMachine.setFluxFactor(42);

    LoggingContextupdate(TimeMachineKey.class, timeMachine);
}
```

### Excluding properties from serialization

Json serialization is done with Jackson, so you can use the `com.fasterxml.jackson.annotation.JsonIgnore` annotation to exclude fields (or getters) from serialization.

```java
    public class MyBean {
        ...

        @JsonIgnore
        getIrrelevantProperty() {
            ...
        }
    }
```

### Configure a Task Decorator in Spring

When you start another thread, MDC will be empty. Usually, you probably want to remain in the same LoggingContext, though.

If you use Spring and start the other thread by calling an `@Async` method, this can be easily solved by making Spring use the `MdcTaskdecorator`.

To use the `MdcTaskDecorator`, you need to confiugure your Executor:

```java
@Configuration
public class AsyncThreadConfiguration {
    ...

    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    ...
}
```

If you already configured various Executors, just add `.setTaskDecorator(new MdcTaskDecorator())` to them.

### Test your logging

If you use **structured-logging** and thus use your logs for monitoring, alerting or visualization, they become a functional requirement and should be tested.

The tests for **structured-logging** itself are implemented with **[log-capture](https://github.com/dm-drogeriemarkt/log-capture)**, which can be easily used from JUnit or Cucumber.

## Changes

### 2.0.0

### 1.0.3

* Added proper serialization for further JSR310 types. Now properly serializes
  * Instant (new)
  * LocalDate (new)
  * LocalDateTime
  * OffsetDateTime
  * OffsetTime (new)
  * Period (new)
  * ZonedDateTime (new)
