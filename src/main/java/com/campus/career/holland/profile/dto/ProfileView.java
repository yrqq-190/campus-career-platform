package com.campus.career.holland.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 画像四项输出，对应 architecture 4.2.3 的 {@code data.profile} 与 4.2.4 的整个 {@code data}。
 *
 * <p>字段顺序与契约示例一致，便于逐字比对。
 *
 * <p>{@code skillGaps} 声明为 {@link Object}，因为它在契约里有两种形态：
 * 正常时是 {@link SkillGapView} 数组，技能缺失时是 {@link SkillGapMissing} 对象。
 * 用一个字段承载两种结构，是为了让前端只判断一次字段类型就能区分，
 * 不必额外加一个 {@code skillGapsStatus} 之类的旁路字段（那会改动契约）。
 *
 * <p>没有画像版本号或 id 字段：契约里没有，AC-3 的「旧画像不存在」由存储侧
 * 「每学生只保留一个位置、生成即整体替换」保证，不靠对外暴露版本号去比对。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileView(List<DirectionView> directions,
                          List<String> cities,
                          List<String> industries,
                          Object skillGaps) {
}
