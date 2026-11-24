package com.campus.career.holland.profile;

/**
 * 求职意向的存储端口。
 *
 * <h2>这里有一个契约缺口，需要记录</h2>
 * architecture 4.2 的五个端点里<b>没有「填写意向城市与期望行业」的写入端点</b>：
 * <ul>
 *   <li>4.2.1 取题、4.2.2 保存作答、4.2.3 提交（无 body）、4.2.4 读画像、4.2.5 重新生成
 *   <li>M1 档案的四类字段是教育/技能/项目/实习，不含城市与行业
 * </ul>
 * 但 AC-2 的 Given 写着「意向城市填了杭州、苏州，期望行业填了互联网」，4.2.3 的响应里
 * 这两项也有值 —— 说明它们必须有来源，而当前契约没给出写入路径。
 *
 * <p><b>本 PR 的处理</b>：只定义存储端口与内存实现，测试直接注入数据来验 AC-2 与第 9 条
 * 验收条件（未填写返回空数组），<b>不自造端点</b>。加端点属于修改已冻结的接口契约，
 * 要走「先改 spec、再改 architecture 4.2、再写实现」的流程，不在 T5 范围内。
 * 已在 PR 描述里写明该缺口，建议单开 Issue 处理。
 */
public interface StudentIntentRepository {

    /** 学生填写的意向；从未填写时返回 {@link StudentIntent#empty()} 而不是 null。 */
    StudentIntent find(String studentId);

    void save(String studentId, StudentIntent intent);
}
