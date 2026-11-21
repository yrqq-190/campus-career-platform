package com.campus.career.holland;

import java.util.List;
import java.util.Optional;

/**
 * 固定题库：30 道 RIASEC 题目、每题四档选项、顺序固定（spec/module-02 FR-1）。
 *
 * <p>排列方式是六个维度交错，每 6 题一轮、每轮 R / I / A / S / E / C 各出一题，
 * 五轮正好 30 题、每个维度 5 题。这样安排有两个原因：
 * <ol>
 *   <li>学生做到任何一段都在覆盖不同维度，中途退出不会让某一维度样本偏少（AC-4 的续答场景）；
 *   <li>如果按维度连续分组，学生做完前 5 题就能看出规律并倾向性作答，量表就失效了。
 * </ol>
 *
 * <p>题库是常量而不是数据库表：spec 的 Non-Goals 明确「不做题目随机抽题、跳题与题库后台管理」，
 * 题目不需要运营，也没有增删场景，放进表里只会多一层没人改的数据。
 */
public final class HollandQuestionBank {

    /** 四档选项，分值即下标 + 1，范围 1–4（architecture 4.2.2 的 {@code option} 约束）。 */
    public static final List<String> OPTIONS = List.of("非常不符合", "不太符合", "比较符合", "非常符合");

    public static final int OPTION_MIN = 1;
    public static final int OPTION_MAX = OPTIONS.size();

    public static final int QUESTION_COUNT = 30;

    /** 每个维度固定 5 题，计分归一化要用到，不写死成魔法数字。 */
    public static final int QUESTIONS_PER_DIMENSION = QUESTION_COUNT / HollandDimension.values().length;

    private static final List<HollandQuestion> QUESTIONS = List.of(
            // 第 1 轮
            new HollandQuestion(1, "我喜欢动手组装或修理东西", HollandDimension.R),
            new HollandQuestion(2, "我喜欢弄清楚事情背后的原因", HollandDimension.I),
            new HollandQuestion(3, "我喜欢用文字、图像或音乐表达自己的想法", HollandDimension.A),
            new HollandQuestion(4, "我喜欢帮助别人解决遇到的困难", HollandDimension.S),
            new HollandQuestion(5, "我愿意说服别人接受我的观点", HollandDimension.E),
            new HollandQuestion(6, "我喜欢把资料整理得井井有条", HollandDimension.C),
            // 第 2 轮
            new HollandQuestion(7, "我愿意使用工具或设备完成具体的工作", HollandDimension.R),
            new HollandQuestion(8, "我对新知识、新技术有强烈的好奇心", HollandDimension.I),
            new HollandQuestion(9, "我不喜欢被固定流程束缚，愿意尝试新做法", HollandDimension.A),
            new HollandQuestion(10, "我愿意耐心倾听他人的想法并给出建议", HollandDimension.S),
            new HollandQuestion(11, "我愿意承担带领团队完成任务的责任", HollandDimension.E),
            new HollandQuestion(12, "我做事习惯按计划和流程一步步推进", HollandDimension.C),
            // 第 3 轮
            new HollandQuestion(13, "比起久坐办公室，我更喜欢需要外出走动的工作", HollandDimension.R),
            new HollandQuestion(14, "我愿意花时间做数据分析和实验验证", HollandDimension.I),
            new HollandQuestion(15, "我对设计感和视觉呈现比较敏感", HollandDimension.A),
            new HollandQuestion(16, "我在团队中愿意承担协调与沟通的角色", HollandDimension.S),
            new HollandQuestion(17, "我对创业、经营或谈判类的话题感兴趣", HollandDimension.E),
            new HollandQuestion(18, "我对细节和准确性要求较高", HollandDimension.C),
            // 第 4 轮
            new HollandQuestion(19, "我对机械、电子设备的运作原理感兴趣", HollandDimension.R),
            new HollandQuestion(20, "遇到复杂问题时，我倾向于先查资料再动手", HollandDimension.I),
            new HollandQuestion(21, "我喜欢头脑风暴，不断产生新点子", HollandDimension.A),
            new HollandQuestion(22, "我从帮助他人成长中获得满足感", HollandDimension.S),
            new HollandQuestion(23, "我倾向于主动争取机会，而不是等待分配", HollandDimension.E),
            new HollandQuestion(24, "我愿意处理需要耐心核对的表格或数据", HollandDimension.C),
            // 第 5 轮
            new HollandQuestion(25, "我能耐心完成需要动手操作的重复性任务", HollandDimension.R),
            new HollandQuestion(26, "我喜欢用逻辑推理的方式得出最终结论", HollandDimension.I),
            new HollandQuestion(27, "我愿意参与需要创意的项目", HollandDimension.A),
            new HollandQuestion(28, "比起和数据打交道，我更喜欢和人打交道", HollandDimension.S),
            new HollandQuestion(29, "在压力下我仍然愿意做出决策", HollandDimension.E),
            new HollandQuestion(30, "我喜欢规则明确、职责清晰的工作环境", HollandDimension.C));

    private HollandQuestionBank() {
    }

    /** 按固定顺序返回 30 题（FR-1）。 */
    public static List<HollandQuestion> all() {
        return QUESTIONS;
    }

    /** 题号范围外的返回 Optional.empty，由调用方决定怎么处理，不在这里抛业务异常。 */
    public static Optional<HollandQuestion> byId(int id) {
        if (id < 1 || id > QUESTIONS.size()) {
            return Optional.empty();
        }
        return Optional.of(QUESTIONS.get(id - 1));
    }

    public static boolean isLegalQuestionId(int id) {
        return id >= 1 && id <= QUESTIONS.size();
    }

    public static boolean isLegalOption(int option) {
        return option >= OPTION_MIN && option <= OPTION_MAX;
    }
}
