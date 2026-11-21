package com.campus.career.resume;

import java.util.Optional;

/**
 * 暂存区：本轮解析结果在学生确认之前只存在这里，任何模块都不得读取（FR-6）。
 *
 * <p>按 architecture 5.1「幂等与无副作用」，暂存区按 {@code student_id} 覆盖，
 * 同一学生任意时刻只有一条暂存记录。所以接口只提供 {@link #replace}，
 * 没有「追加」「按 id 存多条」这类方法——从类型上就写不出并存状态。
 *
 * <p>失败链路上不写暂存区，靠结构保证而不是靠「记得回滚」：
 * 服务层把唯一一次写入放在全部校验通过之后（见 {@link ResumeParseService}），
 * 失败路径上根本没有写点。因此 T3 验收条件 8 的「整体丢弃」不是靠一次删除实现的，
 * 而是「部分结果从未被写入过」。再加一个 {@code discard} 反而会把「可能写脏」
 * 当成正常情况，掩盖了真正的边界。
 *
 * <p>本周是内存占位，T1 建表后换成落库实现，方法签名不变。
 */
public interface ResumeStagingRepository {

    /** 当前暂存结果；没有待确认内容时返回空。 */
    Optional<ParsedResume> find(String studentId);

    /** 整份替换该学生的暂存结果。这是暂存区唯一的写入口。 */
    void replace(String studentId, ParsedResume parsed);
}
