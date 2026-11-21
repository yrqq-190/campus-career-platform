package com.campus.career.holland.profile;

import com.campus.career.holland.HollandDimension;
import java.util.List;

/**
 * 一个可推荐的岗位方向。
 *
 * @param name          方向名称，直接作为画像 {@code directions[].name} 输出
 * @param primary       该方向的主导兴趣维度，用于匹配六维分数
 * @param requiredSkills 该方向要求的关键技能，用于算能力短板与技能命中率
 */
public record JobDirection(String name, HollandDimension primary, List<String> requiredSkills) {

    public JobDirection {
        requiredSkills = List.copyOf(requiredSkills);
    }
}
