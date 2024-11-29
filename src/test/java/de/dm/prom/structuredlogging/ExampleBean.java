package de.dm.prom.structuredlogging;

import lombok.Builder;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.DayOfWeek.MONDAY;
import static java.time.Month.JANUARY;

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
    private Duration duration;
    private DayOfWeek dayOfWeek;
    private Month month;
    private MonthDay monthDay;
    private Year year;
    private YearMonth yearMonth;
    private Optional<String> emptyOptional;
    private Optional<String> nonEmptyOptional;

    static ExampleBean getExample() {
        LocalDateTime importantTime = LocalDateTime.of(2019, JANUARY, 1, 13, 37);

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
                .duration(Duration.ofMinutes(42))
                .dayOfWeek(MONDAY)
                .month(JANUARY)
                .monthDay(MonthDay.of(12, 24))
                .year(Year.of(1984))
                .yearMonth(YearMonth.of(2000, 8))
                .emptyOptional(Optional.empty())
                .nonEmptyOptional(Optional.of("Hello"))
                .build();
    }
}
