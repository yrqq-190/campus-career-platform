package com.campus.career.job;

import com.campus.career.common.ApiResponse;
import com.campus.career.common.RequestIdFilter;
import com.campus.career.common.StudentIdentity;
import com.campus.career.job.dto.JobLinkView;
import com.campus.career.job.dto.JobRecommendationItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M3 岗位推荐的 API 层，对应 architecture 4.3.1 与 module-03 AC-1、AC-3。
 *
 * <p>推荐排序与技能匹配在服务层；本类只做学生身份解析与响应壳组装。
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobRecommendationController {

    private final JobRecommendationService service;

    public JobRecommendationController(JobRecommendationService service) {
        this.service = service;
    }

    /** 获取推荐列表（AC-1、AC-3 的列表降级）。 */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<JobRecommendationItem>>> recommendations(
            @RequestHeader(value = StudentIdentity.HEADER, required = false) String studentHeader,
            HttpServletRequest request) {
        List<JobRecommendationItem> data = service.recommend(StudentIdentity.resolve(studentHeader));
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), data));
    }

    /**
     * 点击岗位外链时校验并返回跳转地址（AC-3）。
     *
     * <p>外链失效由 {@link JobExceptionAdvice} 转成 404 + {@code JOB_LINK_INVALID}，
     * 并附带同关键词重检信息。
     */
    @GetMapping("/{jobId}/link")
    public ResponseEntity<ApiResponse<JobLinkView>> link(@PathVariable String jobId,
                                                         HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(requestId(request), service.link(jobId)));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }
}
