package com.campus.career.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模型输出的结构校验，对应 FR-4 的 Schema 校验与 AC-7。
 *
 * <p>这一层的价值在于「整份通过或整份失败」。只要有一处不合法就抛 502，
 * 不存在返回半份结果的路径，所以下面每个非法用例都同时隐含一条断言：
 * 拿不到任何字段。
 */
class ResumeParseSchemaTest {

    private static final String VALID = """
            {
              "education": [
                {"school": "XX大学", "major": "软件工程", "degree": "本科",
                 "startDate": "2022-09", "endDate": "2026-06"}
              ],
              "skills": ["Java", "SQL"],
              "projects": [
                {"name": "校园二手交易", "role": "后端", "description": "负责订单模块"}
              ],
              "internships": [
                {"company": "某科技有限公司", "role": "后端实习生"}
              ]
            }
            """;

    @Test
    @DisplayName("合法 JSON：四类字段按原样解析出来，键顺序不变")
    void validJson() {
        ParsedResume parsed = ResumeParseSchema.validate(VALID);

        assertEquals(1, parsed.education().size());
        assertEquals("XX大学", parsed.education().get(0).get("school"));
        assertEquals(2, parsed.skills().size());
        assertEquals("Java", parsed.skills().get(0));
        assertEquals(1, parsed.projects().size());
        assertEquals(1, parsed.internships().size());
        assertFalse(parsed.isEmpty());
        // 键顺序保留，序列化结果稳定，前端 diff 不飘
        assertEquals("[school, major, degree, startDate, endDate]",
                parsed.education().get(0).keySet().toString());
    }

    @Test
    @DisplayName("AC-7：模型返回自然语言说明时判失败，不把它当解析结果")
    void naturalLanguageRejected() {
        assertInvalid("这份简历整体不错，候选人有 Java 与 SQL 的使用经验，建议投递后端岗位。");
    }

    @Test
    @DisplayName("AC-7：markdown 代码栅栏包裹的 JSON 同样判失败，不做猜测性剥离")
    void fencedJsonRejected() {
        assertInvalid("```json\n" + VALID + "\n```");
    }

    @Test
    @DisplayName("AC-7：教育背景缺必填键 major 时判失败")
    void missingRequiredFieldRejected() {
        assertInvalid("""
                {
                  "education": [{"school": "XX大学", "degree": "本科"}],
                  "skills": ["Java"],
                  "projects": [],
                  "internships": []
                }
                """);
    }

    @Test
    @DisplayName("FR-5：四类字段缺任意一个 key 都判失败，不默认补空数组")
    void missingCategoryRejected() {
        // 缺 internships。若默认补空数组，「模型漏输出」与「简历里真没有」就分不开了
        assertInvalid("""
                {"education": [], "skills": [], "projects": []}
                """);
        assertInvalid("""
                {"education": [], "skills": [], "projects": [], "internships": null}
                """);
    }

    @Test
    @DisplayName("类型不符判失败：skills 里混入数字、教育背景写成字符串")
    void wrongTypesRejected() {
        assertInvalid("""
                {"education": [], "skills": ["Java", 3], "projects": [], "internships": []}
                """);
        assertInvalid("""
                {"education": ["XX大学"], "skills": [], "projects": [], "internships": []}
                """);
        assertInvalid("""
                {"education": [], "skills": "Java", "projects": [], "internships": []}
                """);
    }

    @Test
    @DisplayName("空技能标签判失败：空串不是有效技能，会污染画像的方向匹配")
    void blankSkillRejected() {
        assertInvalid("""
                {"education": [], "skills": ["Java", "  "], "projects": [], "internships": []}
                """);
    }

    @Test
    @DisplayName("根不是 JSON 对象判失败：数组、空串、被截断的输出都算")
    void nonObjectRootRejected() {
        assertInvalid("[]");
        assertInvalid("");
        assertInvalid("   ");
        assertInvalid(null);
        assertInvalid("{\"education\": [");
    }

    @Test
    @DisplayName("AC-8 的前置判定：四类字段全空的合法 JSON 能被解析出来，由服务层转成 422")
    void allEmptyIsParsedButEmpty() {
        ParsedResume parsed = ResumeParseSchema.validate(
                """
                {"education": [], "skills": [], "projects": [], "internships": []}
                """);

        assertTrue(parsed.isEmpty());
    }

    @Test
    @DisplayName("AC-4：项目与实习为空数组时字段仍在，不是 null 也不省略")
    void emptyCategoriesStayAsEmptyArrays() {
        ParsedResume parsed = ResumeParseSchema.validate("""
                {
                  "education": [{"school": "XX大学", "major": "软件工程", "degree": "本科"}],
                  "skills": ["Java"],
                  "projects": [],
                  "internships": []
                }
                """);

        assertEquals(0, parsed.projects().size());
        assertEquals(0, parsed.internships().size());
        assertFalse(parsed.isEmpty());
    }

    private void assertInvalid(String rawOutput) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ResumeParseSchema.validate(rawOutput));
        assertEquals(ErrorCode.MODEL_OUTPUT_INVALID, ex.errorCode(),
                "结构不合法只能返回 502 MODEL_OUTPUT_INVALID，不新增错误码");
    }
}
