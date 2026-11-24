package com.campus.career.holland.profile;

import com.campus.career.common.RequestIdFilter;
import com.campus.career.common.StudentIdentity;
import com.campus.career.holland.QuizController;
import com.campus.career.holland.profile.dto.ProfileResponse;
import com.campus.career.holland.profile.dto.ProfileView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 画像相关的 API 层，对应 docs/architecture.md 4.2.4 与 4.2.5。
 *
 * <p>与 {@link QuizController} 共用 {@code /api/v1/holland} 前缀但拆成两个类：
 * 问卷是「作答与计分」，画像是「合成与读取」，两组端点的输入输出和生命周期都不一样，
 * 塞进一个类会让依赖变成一堆可选分支。路径本身不冲突，Spring 按具体路径分别路由。
 */
@RestController
@RequestMapping("/api/v1/holland")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    /** 读取当前画像（4.2.4）。尚未生成时返回 {@code "data": null}，不报错。 */
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> current(
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        ProfileView profile = service.current(StudentIdentity.resolve(studentHeader)).orElse(null);
        return ResponseEntity.ok(ProfileResponse.ok(requestId(request), profile));
    }

    /** 重新生成并整体替换旧画像（4.2.5、AC-3）。 */
    @PostMapping("/profile/regenerate")
    public ResponseEntity<ProfileResponse> regenerate(
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        ProfileView profile = service.regenerate(StudentIdentity.resolve(studentHeader));
        return ResponseEntity.ok(ProfileResponse.ok(requestId(request), profile));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
