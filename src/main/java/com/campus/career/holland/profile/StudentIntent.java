package com.campus.career.holland.profile;

import java.util.List;

/**
 * 学生自行填写的求职意向，画像四项里的两项（spec/module-02 FR-5、FR-6）。
 *
 * <p>AC-2 要求「意向城市返回杭州、苏州，期望行业返回互联网，均为学生填写值原样保留」，
 * 所以这里<b>不做任何归一化</b>：不去重、不排序、不裁剪空白，学生填什么就存什么。
 * 归一化会让「杭州、苏州」变成别的样子，验收时对不上。
 *
 * <p>未填写时返回空数组而不是 null，对应边界表「意向城市或期望行业未填写 → 对应字段返回空数组，
 * 不阻断画像生成」。
 */
public record StudentIntent(List<String> cities, List<String> industries) {

    public StudentIntent {
        cities = cities == null ? List.of() : List.copyOf(cities);
        industries = industries == null ? List.of() : List.copyOf(industries);
    }

    public static StudentIntent empty() {
        return new StudentIntent(List.of(), List.of());
    }
}
