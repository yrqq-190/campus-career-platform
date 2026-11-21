package com.campus.career.resume;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * M1 上传校验，按 docs/architecture.md 4.1.1「判定顺序」实现，属服务层业务规则。
 *
 * <p>四步顺序不能调换，因为一次上传可能同时命中多个条件，取第一个命中的结果，
 * 保证同一个文件只返回一个错误码：
 * <ol>
 *   <li>0 字节 → 415 {@code RESUME_UNREADABLE}
 *   <li>超过 10MB → 413 {@code RESUME_TOO_LARGE}
 *   <li>文件头与扩展名不匹配 → 415 {@code RESUME_FORMAT_UNSUPPORTED}
 *   <li>文件打不开 → 415 {@code RESUME_UNREADABLE}
 * </ol>
 *
 * <p>第 3 步有一处明文例外：设了打开密码的 .docx 会被包成 OLE2 容器，
 * 文件头与 .doc 相同，这类文件不按格式不支持拒绝，留到第 4 步判不可读。
 */
@Service
public class ResumeFileValidator {

    /** 单文件上限，与 spec/mvp1-scope.md 的 MAX_RESUME_SIZE 一致：恰好 10MB 放行。 */
    public static final long MAX_RESUME_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    /** %PDF */
    private static final byte[] MAGIC_PDF = {0x25, 0x50, 0x44, 0x46};
    /** PK\x03\x04，DOCX 本质是 zip 包 */
    private static final byte[] MAGIC_ZIP = {0x50, 0x4B, 0x03, 0x04};
    /** OLE2 复合文档，.doc 的正常格式，也是加密 .docx 的外壳 */
    private static final byte[] MAGIC_OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private static final String DOCX_CONTENT_TYPES = "[Content_Types].xml";
    private static final String DOCX_DOCUMENT = "word/document.xml";

    /**
     * 校验一份上传文件，不通过时抛 {@link BusinessException}，通过则正常返回。
     */
    public void validate(MultipartFile file) {
        // 第 1 步：0 字节。空文件没有任何可判定内容，先于其他条件。
        if (file == null || file.getSize() == 0L) {
            throw new BusinessException(ErrorCode.RESUME_UNREADABLE, "文件为空，请重新选择");
        }

        // 第 2 步：超过 10MB。不读文件内容即可判定。
        if (file.getSize() > MAX_RESUME_SIZE) {
            throw new BusinessException(ErrorCode.RESUME_TOO_LARGE);
        }

        // 第 3 步：扩展名白名单 + 文件头比对
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.RESUME_FORMAT_UNSUPPORTED);
        }

        byte[] head = readHead(file);
        boolean looksLikePdf = startsWith(head, MAGIC_PDF);
        boolean looksLikeZip = startsWith(head, MAGIC_ZIP);
        boolean looksLikeOle2 = startsWith(head, MAGIC_OLE2);

        switch (extension) {
            case "pdf" -> {
                if (!looksLikePdf) {
                    // 扩展名是 .pdf，实际是图片、压缩包或可执行文件
                    throw new BusinessException(ErrorCode.RESUME_FORMAT_UNSUPPORTED);
                }
            }
            case "docx" -> {
                if (looksLikeOle2) {
                    // 例外：加密的 .docx 是 OLE2 外壳，不在这里拒绝，进入第 4 步
                    throw new BusinessException(ErrorCode.RESUME_UNREADABLE,
                            "文件已加密或已损坏，请解除密码后重新上传");
                }
                if (!looksLikeZip || !isRealDocx(file)) {
                    // 伪装成 docx 的 zip（例如 xlsx 改后缀）也走这里
                    throw new BusinessException(ErrorCode.RESUME_FORMAT_UNSUPPORTED);
                }
            }
            case "doc" -> {
                if (!looksLikeOle2) {
                    throw new BusinessException(ErrorCode.RESUME_FORMAT_UNSUPPORTED);
                }
            }
            default -> throw new BusinessException(ErrorCode.RESUME_FORMAT_UNSUPPORTED);
        }

        // 第 4 步：文件打不开
        // 本周只覆盖「加密 .docx」，已在上面的例外分支拦下。
        // .doc 与 .pdf 的深度可读性（损坏文件）需要引入 POI / PDFBox 才能可靠判定，
        // 随解析链路一起在后续 PR 补，不在本 PR 用启发式规则假装判过。
    }

    /** DOCX 要求包内同时存在 [Content_Types].xml 与 word/document.xml，缺一即视为伪装。 */
    private boolean isRealDocx(MultipartFile file) {
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        try (InputStream in = file.getInputStream(); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (DOCX_CONTENT_TYPES.equals(name)) {
                    hasContentTypes = true;
                } else if (DOCX_DOCUMENT.equals(name)) {
                    hasDocument = true;
                }
                if (hasContentTypes && hasDocument) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            // 头部像 zip 但读不动，按不可读处理
            throw new BusinessException(ErrorCode.RESUME_UNREADABLE, "文件已损坏，无法读取");
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(4);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESUME_UNREADABLE, "文件无法读取");
        }
    }

    private boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
