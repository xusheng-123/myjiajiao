package com.nurture.learning.app;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
class LearningFileValidatorTest {
    private byte[] doc(String...names) throws Exception {var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){for(var name:names){zip.putNextEntry(new ZipEntry(name));zip.write("<xml/>".getBytes());zip.closeEntry();}}return out.toByteArray();}
    @Test void 无宏Office必须有对应结构() throws Exception {var v=new LearningFileValidator();var valid=doc("[Content_Types].xml","word/document.xml");assertThat(v.validate("课件.docx","application/octet-stream",valid)).contains("wordprocessingml");assertThatThrownBy(()->v.validate("课件.xlsx",null,valid)).isInstanceOf(IllegalArgumentException.class);}
    @Test void 拒绝宏路径与无结构ZIP() throws Exception {var v=new LearningFileValidator();for(var data:new byte[][]{doc("[Content_Types].xml","word/document.xml","word/vbaProject.bin"),doc("[Content_Types].xml","word/document.xml","../escape"),doc("random")})assertThatThrownBy(()->v.validate("x.docx",null,data)).isInstanceOf(IllegalArgumentException.class);}
    @Test void 图片签名验证与双向文本文件名被拒绝(){var v=new LearningFileValidator();assertThat(v.validate("a.png",null,new byte[]{(byte)137,80,78,71,13,10,26,10,0})).isEqualTo("image/png");assertThatThrownBy(()->v.validate("a.png",null,"not an image".getBytes())).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->v.validate("safe\u202eevil.pdf",null,"%PDF-1".getBytes())).isInstanceOf(IllegalArgumentException.class);}
}
