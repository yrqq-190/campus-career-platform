package com.campus.career.holland;

/**
 * 霍兰德六维 RIASEC，对应 spec/module-02 FR-4「按固定量表规则把 30 题映射到六个维度」。
 *
 * <p>枚举名直接当 JSON 键用（{@code R} / {@code I} / {@code A} / {@code S} / {@code E} / {@code C}），
 * 与 architecture 4.2.3 响应示例的 {@code scores} 完全一致，不做二次映射，
 * 免得前后端各写一张对照表再对不上。
 */
public enum HollandDimension {

    R("现实型"),
    I("研究型"),
    A("艺术型"),
    S("社会型"),
    E("企业型"),
    C("常规型");

    /** 仅用于日志与文档，不进接口响应。 */
    private final String label;

    HollandDimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
