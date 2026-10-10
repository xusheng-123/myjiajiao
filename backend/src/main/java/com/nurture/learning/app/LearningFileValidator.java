package com.nurture.learning.app;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
@Component
public class LearningFileValidator {
    public static final long MAX_BYTES=20L*1024*1024;
    private static final Map<String,String> TYPES=Map.of(
            "pdf","application/pdf","png","image/png","jpg","image/jpeg","jpeg","image/jpeg",
            "docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation");
    public String validate(String filename,String declared,byte[] content) {
        if(filename==null || filename.length()>180 || filename.isBlank() || filename.codePoints().anyMatch(c->
                c=='/' || c=='\\' || c==':' || Character.isISOControl(c) || c>=0x202a && c<=0x202e || c>=0x2066 && c<=0x2069))
            throw new IllegalArgumentException("文件名非法或超过180字符");
        var extension=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        var mime=TYPES.get(extension);
        if(mime==null) throw new IllegalArgumentException("仅支持PDF、PNG、JPEG及无宏的DOCX/XLSX/PPTX");
        if(content.length==0 || content.length>MAX_BYTES) throw new IllegalArgumentException("文件不能为空且不得超过20MB");
        if(declared!=null && !declared.equalsIgnoreCase(mime) && !declared.equalsIgnoreCase("application/octet-stream"))
            throw new IllegalArgumentException("声明的文件类型与扩展名不一致");
        boolean signature=switch(extension) {
            case "pdf" -> starts(content,"%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "png" -> starts(content,new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "jpg","jpeg" -> starts(content,new byte[]{(byte)255,(byte)216,(byte)255});
            default -> office(content,extension);
        };
        if(!signature) throw new IllegalArgumentException("文件内容与类型不符或Office文档无效");
        return mime;
    }
    private boolean starts(byte[] data,byte[] prefix){return data.length>=prefix.length && Arrays.equals(Arrays.copyOf(data,prefix.length),prefix);}
    private boolean office(byte[] data,String extension) {
        if(!starts(data,new byte[]{80,75,3,4})) return false;
        String required=switch(extension){case "docx" -> "word/document.xml";case "xlsx" -> "xl/workbook.xml";default -> "ppt/presentation.xml";};
        Set<String> names=new HashSet<>();long bytes=0;int count=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                var name=entry.getName();
                if(++count>2000 || name.contains("..") || name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || name.toLowerCase(Locale.ROOT).contains("vbaproject") || !names.add(name)) return false;
                int read;while((read=zip.read(buffer))!=-1){bytes+=read;if(bytes>100L*1024*1024) return false;}
            }
            return names.contains("[Content_Types].xml") && names.contains(required);
        } catch(IOException e){return false;}
    }
}
