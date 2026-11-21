package com.campus.career.resume;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * M1 上传校验的单元测试，逐条对应 spec/module-01 的 AC-3、AC-5 与「边界与异常」表前 4 行。
 *
 * <p>直接测服务层，不启动 Spring 上下文，因此 10MB 超限走的是服务层判定，
 * 与真实 HTTP 请求下 Spring multipart 先行拦截构成两道防线，两条都有测试。
 */
class ResumeFileValidatorTest {

    private static final long MB10 = 10L * 1024 * 1024;

    private final ResumeFileValidator validator = new ResumeFileValidator();

    @Test
    @DisplayName("AC-3 边界：恰好 10MB 的 PDF 放行")
    void exactlyTenMegabytesPdfPasses() {
        MockMultipartFile file = pdf(10L * 1024 * 1024);
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    @DisplayName("AC-3 边界：10MB + 1KB 返回 413 RESUME_TOO_LARGE")
    void tenMegabytesPlusOneKilobyteRejected() {
        MockMultipartFile file = pdf(MB10 + 1024);
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(file));
        assertAll(
                () -> assertEquals(ErrorCode.RESUME_TOO_LARGE, ex.errorCode()),
                () -> assertEquals("单份简历不超过 10MB", ex.getMessage())
        );
    }

    @Test
    @DisplayName("AC-5 异常：上传 .png 图片返回 415 RESUME_FORMAT_UNSUPPORTED")
    void pngRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", pngBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(file));
        assertEquals(ErrorCode.RESUME_FORMAT_UNSUPPORTED, ex.errorCode());
    }

    @Test
    @DisplayName("边界行 2：扩展名是 .pdf 但实际内容是图片，按实际类型拒绝")
    void fakePdfRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", pngBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(file));
        assertEquals(ErrorCode.RESUME_FORMAT_UNSUPPORTED, ex.errorCode());
    }

    @Test
    @DisplayName("AC-5 异常：设了打开密码的 .docx 返回 415 RESUME_UNREADABLE")
    void encryptedDocxUnreadable() {
        // 加密后的 docx 被包成 OLE2 容器，文件头与 .doc 相同
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.docx", "application/octet-stream", ole2Bytes(2048));
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(file));
        assertEquals(ErrorCode.RESUME_UNREADABLE, ex.errorCode());
    }

    @Test
    @DisplayName("边界行 4：0 字节文件返回 415 RESUME_UNREADABLE")
    void zeroByteUnreadable() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(file));
        assertEquals(ErrorCode.RESUME_UNREADABLE, ex.errorCode());
    }

    @Test
    @DisplayName("边界行 2：伪装成 .docx 的普通 zip（缺 word/document.xml）被拒绝")
    void fakeDocxRejected() throws IOException {
        byte[] zip = zipOf(new String[]{"[Content_Types].xml"}, new byte[][]{"<Types/>".getBytes(StandardCharsets.UTF_8)});
        MockMultipartFile file = new MockMultipartFile(
                "file", "sheet.docx", "application/octet-stream", zip);
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(file));
        assertEquals(ErrorCode.RESUME_FORMAT_UNSUPPORTED, ex.errorCode());
    }

    @Test
    @DisplayName("AC-1 前置：结构完整的 docx 通过校验")
    void realDocxPasses() throws IOException {
        byte[] zip = zipOf(
                new String[]{"[Content_Types].xml", "word/document.xml"},
                new byte[][]{"<Types/>".getBytes(StandardCharsets.UTF_8),
                        "<w:document/>".getBytes(StandardCharsets.UTF_8)});
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx", "application/octet-stream", zip);
        assertDoesNotThrow(() -> validator.validate(file));
    }

    // ---------- 测试数据构造 ----------

    private MockMultipartFile pdf(long size) {
        byte[] bytes = new byte[(int) size];
        // %PDF
        bytes[0] = 0x25;
        bytes[1] = 0x50;
        bytes[2] = 0x44;
        bytes[3] = 0x46;
        Arrays.fill(bytes, 4, bytes.length, (byte) 0x20);
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);
    }

    private byte[] pngBytes() {
        // 89 50 4E 47 0D 0A 1A 0A
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    }

    private byte[] ole2Bytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xD0;
        bytes[1] = (byte) 0xCF;
        bytes[2] = 0x11;
        bytes[3] = (byte) 0xE0;
        bytes[4] = (byte) 0xA1;
        bytes[5] = (byte) 0xB1;
        bytes[6] = 0x1A;
        bytes[7] = (byte) 0xE1;
        return bytes;
    }

    private byte[] zipOf(String[] names, byte[][] contents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new ZipEntry(names[i]));
                zip.write(contents[i]);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
