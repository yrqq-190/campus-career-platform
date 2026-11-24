package com.campus.career.resume;

/**
 * 大模型解析端口，对应 spec FR-4「解析由大模型完成」。
 *
 * <p><b>返回的是模型原始文本，不是结构化对象。</b>这一点是刻意的：AC-7 要求
 * 「模型返回自然语言说明时返回 502，不把非结构化文本当作解析结果」，
 * 如果端口直接返回 POJO，反序列化就会散落在各实现里，换一个模型实现就可能绕过校验。
 * 把「调模型」与「校验模型输出」拆成两段，校验只有一处（{@link ResumeParseSchema}）。
 *
 * <p>实现允许抛运行时异常，例如网络错误、鉴权失败。服务层统一按「本次尝试失败」处理，
 * 只有超时才计入重试，其余失败不重试（见 {@link ResumeParseService}）。
 */
public interface ResumeParseModel {

    /**
     * 调用大模型解析一份简历。
     *
     * @param document 简历文件字节，调用方已完成格式与大小校验
     * @return 模型输出的原始文本，可能是合法 JSON，也可能是自然语言
     */
    String parse(byte[] document);
}
