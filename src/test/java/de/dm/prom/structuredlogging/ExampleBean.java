package de.dm.prom.structuredlogging;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Data
@Builder
class ExampleBean {
    private String name;
    private int age;
    private LocalDateTime importantTime;
    private OffsetDateTime importantOffsetTime;
    private Instant instant;
    private LocalDate localDate;
    private OffsetTime offsetTime;
    private Period period;
    private ZonedDateTime zonedDateTime;
    private LocalTime localTime;

    static ExampleBean getExample() {
        LocalDateTime importantTime = LocalDateTime.of(2019, Month.JANUARY, 1, 13, 37);

        return ExampleBean.builder()
                .name("John Doe")
                .age(35)
                .importantTime(importantTime)
                .importantOffsetTime(OffsetDateTime.of(importantTime, ZoneOffset.of("+01:00")))
                .instant(Instant.ofEpochMilli(1000))
                .localDate(LocalDate.of(2020, 1, 1))
                .offsetTime(OffsetTime.of(LocalTime.of(13, 37), ZoneOffset.of("+01:00")))
                .period(Period.ofDays(42))
                .zonedDateTime(ZonedDateTime.of(importantTime, ZoneId.of("UTC")))
                .localTime(LocalTime.of(13, 37))
                .build();
    }
}
