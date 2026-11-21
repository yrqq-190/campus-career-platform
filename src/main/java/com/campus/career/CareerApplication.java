package com.campus.career;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校级学生求职发展平台后端入口。
 *
 * <p>分层依据 docs/architecture.md 第 2 章：展示层 → API 层 → 服务层 → 数据层，单向依赖。
 * 包按业务模块划分（resume / profile / ...），模块内部再分 controller / service，
 * 与「8 个 Controller、8 个 Service 对 M1–M8」以及按模块分工的方式一致。
 */
@SpringBootApplication
public class CareerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerApplication.class, args);
    }
}
