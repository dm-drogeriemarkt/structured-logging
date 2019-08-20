package de.dm.prom.structuredlogging;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@Builder
class ExampleBean {
    private String name;
    private int age;
    private LocalDateTime importantTime;
    private OffsetDateTime importantOffsetTime;

    static ExampleBean getExample() {
        LocalDateTime importantTime = LocalDateTime.of(2019, Month.JANUARY, 1, 13, 37);

        return ExampleBean.builder()
                .name("John Doe")
                .age(35)
                .importantTime(importantTime)
                .importantOffsetTime(OffsetDateTime.of(importantTime, ZoneOffset.of("+01:00")))
                .build();
    }
}
