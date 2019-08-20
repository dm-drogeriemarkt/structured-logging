# structured-logging

[<img src="https://opensourcelogos.aws.dmtech.cloud/dmTECH_opensource_logo.svg" height="20" width="130">](https://dmtech.de/)
[![Build Status](https://travis-ci.org/dm-drogeriemarkt/structured-logging.svg?branch=master)](https://travis-ci.org/dm-drogeriemarkt/structured-logging) 

Structured logging utility. Designed to work with logback and logstash. Works well (but not only) with Spring and ELK Stack.

## Why use this?

If you use your logs for monitoring, alerting or visualization, you want them to have structured information so you can properly build your monitoring/alerting/visualizations on that.

You can attach information (key/value) to log messages with [MDC](https://logback.qos.ch/manual/mdc.html). Pure MDC, however, has some shortcomings:

* It is a pure key/value store where you can store strings. There is no further hierarchical structure.
* You need to manually make sure to remove the key/value when you leave a certain context.

To solve this problem, **structured–logging** is built on MDC/logback and provides:

* Structured logging that
  * simply serializes beans to provide structured information
  * defines the MDC key for a certain type in one place
* MDC management to make sure that
  * information is removed from MDC when it is not relevant anymore
  * conflicting contexts (same key) don't overlap

## Example

If there are log messages that happen in the context of an order, you may want to attach information about that order to these log messages.

```java
    try (MdcContext c = MdcContext.of(OrderKey.class, incomingOrder)) {
        log.info("A new order has come in.");
        if (isValid(incomingOrder)) {
            prepareForDelivery(incomingOrder);
        }
        // if isValid() and prepareForDelivery() log something, 
        // those log messages will also have the context of that order
    }
```

## Usage

To use this, you need to:

 * [Add structured-logging as a dependency](#add-structured-logging-as-a-dependency)
 * [Define how Objects should be named in MDC](#Define-how-Objects-should-be-named-in-MDC)
 * [Put Objects into MDC](#Put-Objects-into-MDC)
    * [Excluding properties from serialization](#Excluding-properties-from-serialization)
 * [Configure Logback for Logstash](#Configure-Logback-for-Logstash)
 * [Configure a Task Decorator in Spring](#Configure-a-Task-Decorator-in-Spring)
 * [Test your logging](#Test-your-logging)

### Add structured-logging as a dependency

If you use maven, add this to your pom.xml:

```pom.xml
<dependency>
    <groupId>de.dm.infrastructure</groupId>
    <artifactId>structured-logging</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### Define how Objects should be named in MDC

Define what the MDC key of a certain object type should be by implementing `MdcContextKey<T>`:

```java
public final class MyBeanKey implements MdcContextKey<MyBean> 
    @Override
    public String getMdcKey() {
        return "my_bean";
    }
}
```

Make sure that your `MdcContextKey`

* is `public`
* has a `public`, non-parameterized constructor

### Put Objects into MDC

Create a new `MdcContext` in a try-with-resources statement to define in which scope certain MDC information should be set:

```java
void doSomething(MyBean myBean) {
    log.info("a message without MyBean context");

    try (MdcContext c = MdcContext.of(MyBean.class, myBean)) {
        log.info("a message with MyBean context");
        doSomethingElse(myBean);
    }

    log.info("another message without MyBean context");
}

void doSomethingElse(MyBean myBean) {
    log.info("another message with MyBean context");
}
```

#### Excluding properties from serialization

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

### Configure Logback for Logstash

Objects are put into MDC because they contain structured information. To make sure this is understood as structured information, you need to make sure that logback is configured correctly.

To log the whole log message including the object you put into MDC as Json, you need to configure Logback to use the **LogstashEncoder**.

The **LogstashEncoder** in turn must know about the **StructuredMdcJsonProvider** to properly encode the structured JSON information from MDC.

To log into a file as Json with Spring, you can use this configuration:

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <appender name="JSON_FILE" class="ch.qos.logback.core.FileAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
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

### Configure a Task Decorator in Spring

When you start another thread, MDC will be empty. Usually, you probably want to remain in the same MdcContext, though.

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
