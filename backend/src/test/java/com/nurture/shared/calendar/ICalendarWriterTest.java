package com.nurture.shared.calendar;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ICalendarWriterTest {
    @Test void 导出UTC稳定UID与CRLF(){var id=UUID.randomUUID();var start=OffsetDateTime.parse("2026-11-01T15:00:00+08:00");var text=ICalendarWriter.write(List.of(new ICalendarWriter.Event(id,"数学",start,start.plusHours(1),true)));assertThat(text).contains("UID:"+id+"@nurture.local\r\n","DTSTART:20261101T070000Z\r\n","STATUS:TENTATIVE\r\n").endsWith("END:VCALENDAR\r\n");}
    @Test void 中文长行按字节折行且完整还原(){String original="SUMMARY:"+"中文测验与课堂安排😀".repeat(20);var folded=ICalendarWriter.fold(original);for(var line:folded.split("\r\n"))assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);assertThat(folded.replace("\r\n ","")).isEqualTo(original);}
    @Test void 标题换行标点转义阻止事件注入(){assertThat(ICalendarWriter.escape("数学;线性,函数\\图像\r\nBEGIN:VEVENT")).isEqualTo("数学\\;线性\\,函数\\\\图像\\nBEGIN:VEVENT");}
}
