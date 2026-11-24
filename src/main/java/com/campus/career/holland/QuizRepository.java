package com.campus.career.holland;

import java.util.Map;

/**
 * M2 作答与计分结果的存储契约。
 *
 * <p>抽出接口而不是直接写内存 Map，是因为 T1（#107）的建表 PR 还没合并，本周不能写落库代码
 * （本周约束：本体依赖未完成时只做契约骨架 + Mock）。等 {@code quiz_answer} 表合并后，
 * 补一个 JdbcQuizRepository 实现本接口即可，Service 与 Controller 一行都不用改。
 *
 * <p>两个「IfNotSubmitted / IfAbsent」方法返回 boolean 而不是抛异常，
 * 是为了把「是否重复提交」这一业务判定留在服务层，仓储只负责原子性。
 * 否则并发下「先查再写」会出现两次提交都通过检查的窗口。
 */
public interface QuizRepository {

    /** 该学生已保存的作答，题号 → 选项。 */
    Map<Integer, Integer> findAnswers(String studentId);

    /**
     * 原子保存单题作答（FR-2 即时保存）。
     *
     * @return 保存成功返回 true；问卷已提交返回 false 且不覆盖已提交版本（AC-7、architecture 4.2.2）
     */
    boolean saveAnswerIfNotSubmitted(String studentId, int questionId, int option);

    boolean isSubmitted(String studentId);

    /**
     * 原子标记「已提交」。
     *
     * @return 首次提交返回 true；重复提交返回 false（AC-7 只接受第一次）
     */
    boolean markSubmittedIfAbsent(String studentId);

    /** 已生成的六维分数，未提交时为空 Map。 */
    Map<HollandDimension, Integer> findScores(String studentId);

    void saveScores(String studentId, Map<HollandDimension, Integer> scores);
}
