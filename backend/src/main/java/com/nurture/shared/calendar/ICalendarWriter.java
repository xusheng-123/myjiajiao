package com.nurture.shared.calendar;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class ICalendarWriter {
    private ICalendarWriter(){}
    public record Event(UUID id,String title,OffsetDateTime start,OffsetDateTime end,boolean tentative) {}
    private static final DateTimeFormatter UTC=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    public static String write(List<Event> events) {
        var lines=new ArrayList<String>(List.of("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//NURTURE//Learning Calendar//ZH","CALSCALE:GREGORIAN","METHOD:PUBLISH"));
        String stamp=UTC.format(Instant.now());
        for(var e:events){lines.addAll(List.of("BEGIN:VEVENT","UID:"+e.id()+"@nurture.local","DTSTAMP:"+stamp,"DTSTART:"+UTC.format(e.start()),"DTEND:"+UTC.format(e.end()),"SUMMARY:"+escape(e.title()),"STATUS:"+(e.tentative()?"TENTATIVE":"CONFIRMED"),"END:VEVENT"));}
        lines.add("END:VCALENDAR");return lines.stream().map(ICalendarWriter::fold).collect(java.util.stream.Collectors.joining("\r\n","","\r\n"));
    }
    static String escape(String text){return text.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n").replace("\n","\\n").replace(",","\\,").replace(";","\\;");}
    static String fold(String line) {
        var out=new StringBuilder();int width=0;
        for(var point:line.codePoints().toArray()) {
            String s=new String(Character.toChars(point));int bytes=s.getBytes(StandardCharsets.UTF_8).length;
            if(width+bytes>75){out.append("\r\n ");width=1;}
            out.append(s);width+=bytes;
        }
        return out.toString();
    }
}
