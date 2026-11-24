package com.campus.career.resume;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模型输出的结构校验，对应 spec FR-4「经 JSON Schema 校验通过后才进入下一步」与 AC-7。
 *
 * <p>校验失败一律抛 {@code MODEL_OUTPUT_INVALID}（502），且<b>不返回任何部分结果</b>：
 * AC-7 要求「前端不展示任何字段，不把非结构化文本当作解析结果展示或入库」，
 * 所以这里只有「整份通过」与「整份失败」两种结局，没有半成品。
 *
 * <h2>三道闸</h2>
 * <ol>
 *   <li><b>是不是 JSON 对象。</b>自然语言说明、markdown 代码栅栏包裹的 JSON、被截断的输出，全部在此拦下。
 *   <li><b>四类字段是否齐全。</b>FR-5 写明「不存在某类内容时以空数组返回，不省略字段」，
 *       所以缺 key 判失败，而不是默认补空数组。补空数组会把「模型漏输出」和「简历里真没有」
 *       混成同一件事，学生看到空数组会以为简历里没写实习。
 *   <li><b>元素必填键是否到位。</b>只强制 architecture 4.1.1 成功示例里出现过的键：
 *       教育背景的 {@code school / major / degree}、项目经历的 {@code name}。
 *       实习经历在示例里是空数组，没有字段可对照，本层只要求它是对象数组，不自造键名。
 * </ol>
 *
 * <h2>刻意不做的事</h2>
 * <p>不剥离 markdown 代码栅栏，不做「从一段话里抠出花括号」这类容错。
 * 真实接入时应在 prompt 里约束模型只输出 JSON；在校验层做猜测性清洗，
 * 等于允许半结构化文本混进档案，与 AC-7 正面冲突。
 */
final class ResumeParseSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /** 教育背景必填键，取自 architecture 4.1.1 成功响应示例。 */
    private static final List<String> EDUCATION_REQUIRED = List.of("school", "major", "degree");

    /** 项目经历必填键，取自 architecture 4.1.1 成功响应示例。 */
    private static final List<String> PROJECT_REQUIRED = List.of("name");

    /** 实习经历示例里为空数组，无可对照的键，因此不强制具体字段。 */
    private static final List<String> INTERNSHIP_REQUIRED = List.of();

    private ResumeParseSchema() {
    }

    /**
     * 校验并转换模型原始输出。
     *
     * @param rawOutput 模型返回的原文
     * @return 四类字段，恒存在、可为空数组
     * @throws BusinessException 结构不合法时抛 {@code MODEL_OUTPUT_INVALID}
     */
    static ParsedResume validate(String rawOutput) {
        JsonNode root = readObject(rawOutput);
        return new ParsedResume(
                objectArray(root, "education", EDUCATION_REQUIRED),
                stringArray(root, "skills"),
                objectArray(root, "projects", PROJECT_REQUIRED),
                objectArray(root, "internships", INTERNSHIP_REQUIRED));
    }

    private static JsonNode readObject(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw invalid();
        }
        String text = rawOutput.trim();
        // 先做一次廉价的前缀判断：JSON 对象必然以 { 开头、以 } 结束。
        // 自然语言说明与代码栅栏都在这里出局，不必进解析器。
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw invalid();
        }
        try {
            JsonNode root = MAPPER.readTree(text);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            return root;
        } catch (JsonProcessingException e) {
            throw invalid();
        }
    }

    private static List<Map<String, Object>> objectArray(JsonNode root,
                                                         String field,
                                                         List<String> requiredKeys) {
        JsonNode node = requiredField(root, field);
        if (!node.isArray()) {
            throw invalid();
        }
        List<Map<String, Object>> result = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (!element.isObject()) {
                throw invalid();
            }
            for (String key : requiredKeys) {
                JsonNode value = element.get(key);
                if (value == null || !value.isTextual() || value.asText().isBlank()) {
                    throw invalid();
                }
            }
            result.add(MAPPER.convertValue(element, MAP_TYPE));
        }
        return result;
    }

    private static List<String> stringArray(JsonNode root, String field) {
        JsonNode node = requiredField(root, field);
        if (!node.isArray()) {
            throw invalid();
        }
        List<String> result = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (!element.isTextual() || element.asText().isBlank()) {
                throw invalid();
            }
            result.add(element.asText());
        }
        return result;
    }

    /** 缺 key 与显式 null 同罪：两者都违反 FR-5「不省略字段」。 */
    private static JsonNode requiredField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            throw invalid();
        }
        return node;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.MODEL_OUTPUT_INVALID);
    }
}
