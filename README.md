# Structured Logging [<img src="https://opensourcelogos.aws.dmtech.cloud/dmTECH_opensource_logo.svg" height="20" width="130">](https://dmtech.de/) [![Build Status](https://travis-ci.org/dm-drogeriemarkt/structured-logging.svg?branch=master)](https://travis-ci.org/dm-drogeriemarkt/structured-logging)

Structured Logging is a library that complements [SLF4J](http://www.slf4j.org/) and
its  [MDC](http://logback.qos.ch/manual/mdc.html) to

* augment log messages with information about the context in which they happen
* manage
    * the lifetime of that context information
    * serialization of that context information

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
  * [Step 4: (Optional) Use the Task Decorator](#step-4-optional-use-the-task-decorator)
  * [Step 5: (also Optional) Test your logging](#step-5-also-optional-test-your-logging)
* [Advanced usage](#advanced-usage)
  * [Define how Objects should be named in MDC](#define-how-objects-should-be-named-in-mdc)
  * [Excluding properties from serialization](#excluding-properties-from-serialization)
* [Changes](#changes)
  * [2.0.0](#200)
  * [1.0.3](#103)

---

## Example

If there are log messages that happen in the context of an order, you may want to attach information about that order to
these log messages.

```java
    try(var c = MdcContext.of(incomingOrder)){
        log.info("A new order has come in.");
        
        if(isValid(incomingOrder)){
            prepareForDelivery(incomingOrder);
        }
    }
```

The `incomingOrder` will be attached to the log messages generated in this `try` block, including

* the message from `log.info("A new order has come in.")` .
* all messages logged by `prepareForDelivery(...)`, `isValid(...)`
* all messages logged by methods called indirectly by the methods above

Here's what a log message with an `incomingOrder` looks like in Kibana:

![Kibana-Example](docs/structured-logging-kibana.png)

## Advantages

This approach has various advantages over both plain logging as well as
using [MDC](http://logback.qos.ch/manual/mdc.html) directly.

### Advantages over plain logging

* If you log context information, you can easily trace the order from the example above by filtering
  by `incomingOrder.id : 1234`
* Searching is easier if the log message itself does not vary that much (just search
  for `message : "A new order has come in."`)
* Because the type of fields can be inferred, you can for example search by `incomingOrder.paymentDate > 2020-01-01`
* You don't have to remember to attach context information to every log message: Context information is attached automatically while the context is active.
* You can do alerting and monitoring based on specific MDC values. Want to know the summary monetary value of
  those `incomingOrder`s in the example above? You can now do that.

### Advantages over using MDC directly

Structured Logging adds crucial features missing from plain [MDC](http://logback.qos.ch/manual/mdc.html), even when
using MDC's [putCloseable](http://www.slf4j.org/api/org/slf4j/MDC.html#putCloseable(java.lang.String,java.lang.String)):

* Proper serialization of complex objects and their fields is taken care of. MDC itself does not support a hierarchical
  structure, although most log processors like logstash do support it.
* MDC is properly reset after the `try` block if it contained another value before.
* If a task in another thread is started, context information is retained because it is still relevant. This is solved
  by providing a custom Task Decorator (see below).

## Prerequisites

For creating logs:

* You need to use **SLF4J** as your logging facade.
* You need to use **logback** as your logging implementation.
* **Optionally**, you can use **Spring (Boot)** which uses both of these per default and already lets you register Task
  Decorators. But other frameworks (or no framework) work just as well.

For consuming logs:

* Works with log consumers that can digest JSON logs, like the **ELK Stack** or **Datadog**. The consumer needs to
  support hierarchical data in a log message. **Greylog does not** support this at the moment, for example.

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

Create a new `MdcContext` in a try-with-resources statement to define the scope in which context information should
be set:

```java
log.info("a message without context");

TimeMachine timeMachine=TimeMachineBuilder.build();

//set the MdcContext as soon as possible after object (timeMachine) creation
try(var c = MdcContext.of(timeMachine)){
    log.info("time machine found. Trying to use it");

    travelSomewhereWith(timeMachine);

    timeMachine.setFluxFactor(42);

    MdcContext.update(timeMachine);

    travelSomewhereWith(timeMachine);
}

log.info("another message without context");
```

### Step 4: (Optional) Use the Task Decorator

When you start another thread, MDC will be empty although the things happening there usually happen in the same context.

So if you want to retain the context information in the new thread, there's a decorator for your `Runnable` that solves this problem by copying the current MDC contents to the new Thread in which your `Runnable` runs and resets it when it's done:

```java
Runnable decoratedRunnable = MdcTaskDecorator.decorate(() -> doSomethingThatLogs());
```

If you use Spring and start the other thread by calling an `@Async` method, use the `SpringMdcTaskDecorator` for decorating the threads. You just need to configure an Executor:

```java

@Configuration
public class AsyncThreadConfiguration {
    ...

    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new SpringMdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    ...
}
```

If you already configured Executors for other reasons, just add `.setTaskDecorator(new MdcTaskDecorator())`.

### Step 5: (also Optional) Test your logging

If you use your logs for monitoring, alerting or visualization, they are a functional requirement and should be
tested.

The tests for **structured-logging** itself are implemented
with **[log-capture](https://github.com/dm-drogeriemarkt/log-capture)**, which can be easily used from JUnit or
Cucumber. Give it a try.

## Advanced usage

### Define how Objects should be named in MDC

There are three ways to define the MDC key for the objects you put into the context:

**1. Use the default (The class's shortName, as seen in [Step 3](#step-3-put-objects-into-the-logging-context))**

**2. Define it manually**

```java
try(var c = MdcContext.of("de_lorean", timeMachine)){
    ...
    MdcContext.update("de_lorean", timeMachine);
    ...
}
```

**3. Provide an MdcKeySupplier**

This is the recommended approach if you want to make sure that the same MDC key is always used for the same type, even
if it differs from the type's shortName or the type is renamed.

First, define the MDC key for the type:

```java
// must be public and have a public, non-parameterized constructor
public final class TimeMachineKey implements MdcKeySupplier<TimeMachine> {
    @Override
    public String getMdcKey() {
        return "de_lorean";
    }
}
```

Then use it:

```java
//MdcContext.of(...) is defined so that you can only use TimeMachineKey with a TimeMachine.
try(var c = MdcContext.of(TimeMachineKey.class, timeMachine)){
    ...
    MdcContext.update(TimeMachineKey.class, timeMachine);
    ...
}
```

### Excluding properties from serialization

Json serialization is done with Jackson, so you can use the `com.fasterxml.jackson.annotation.JsonIgnore` annotation to
exclude fields (or getters) from serialization.

```java
    public class TimeMachine {
        ...

    @JsonIgnore
    getIrrelevantProperty() {
            ...
    }
}
```

## Changes

### 2.0.0

* New Feature: Added convenience methods for creating MdcContexts without having an MdcKeySupplier.
* New Feature: MdcContext can now be updated in case the Bean that it describes is updated.
* New Feature: Runnables can now directly be decorated via `MdcTaskDecorator` to retain MDC information even when not using Spring.
* Breaking Change: Renamed MdcContextId to MdcKeySupplier to make its purpose clear
* Fix: Do not create ObjectMapper every time an MdcContext is created.
* Fix: Do not overwrite MDC information in threads that already have a context in MdcTaskDecorator - a WARNing is logged instead because this indicates incorrect usage

### 1.0.3

* Added proper serialization for further JSR310 types. Now properly serializes
    * Instant (new)
    * LocalDate (new)
    * LocalDateTime
    * OffsetDateTime
    * OffsetTime (new)
    * Period (new)
    * ZonedDateTime (new)
