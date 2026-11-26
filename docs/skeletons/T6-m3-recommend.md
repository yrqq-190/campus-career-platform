# T6：M3 推荐列表与推荐理由 (骨架)

## 1. 接口契约说明
- **URL**: `GET /api/v1/jobs/recommendations`
- **请求参数**: 无。学生身份从 JWT Token 中获取（`SecurityContext` 或 `@AuthenticationPrincipal`），不使用 Query 参数。
- **对应 spec/AC**: module-03 AC-1、AC-2、AC-3、AC-4、AC-5

## 2. DTO / VO 结构

### RecommendResponse (VO)
- `code`: String (状态码，如 "OK")
- `requestId`: String (请求追踪 ID，NFR-2 日志对账靠它)
- `data`: List<JobItem>

### JobItem (DTO)
- `jobId`: String (岗位 ID)
- `title`: String (岗位标题)
- `reason`: String (命中理由，如 "命中技能：Vue、JavaScript")
- `sourceUrl`: String (外链地址)
- `isDemo`: Boolean (是否为演示数据)
- `linkStatus`: String (外链状态：OK / INVALID，AC-3 用)
- `retryKeyword`: String (重检关键词，AC-3 用)

## 3. Mock JSON

**成功响应 200 OK:**
```json
{
  "code": "OK",
  "requestId": "req-123456",
  "data": [
    {
      "jobId": "J001",
      "title": "前端开发实习生",
      "reason": "命中技能：Vue、JavaScript",
      "sourceUrl": "https://example.com/job/1",
      "isDemo": true,
      "linkStatus": "OK",
      "retryKeyword": null
    }
  ]
}
```

**无技能标签空态 200 OK (AC-2):**

```json
{
  "code": "OK",
  "requestId": "req-123457",
  "data": [],
  "personalizationApplied": false,
  "status": "PROFILE_SKILL_MISSING"
}
```

**无匹配岗位空态 200 OK (AC-5):**

```json
{
  "code": "OK",
  "requestId": "req-123458",
  "data": [],
  "personalizationApplied": true
}
```

## 4. 错误码
- `401` -（无业务码）：未登录或 Token 失效
- `404` `JOB_LINK_INVALID`：岗位外链失效（单条记录失效，不应整个列表报错，具体见下方待实现说明）

## 5. 页面结构说明
- 卡片列表展示 `title` 和 `reason`。
- 若 `isDemo` 为 true，显示「演示数据」标签。
- 若 `status` 为 `PROFILE_SKILL_MISSING`，展示空态提示：「暂无匹配岗位，请完善技能标签」。
- 若 `personalizationApplied` 为 true 且 `data` 为空，展示空态提示：「暂无匹配岗位」。

## 6. 后续真实 Controller 代码参考
*(第 10 周脚手架合并后移入 src/，当前仅作示意)*

```java
@RestController
@RequestMapping("/api/v1/jobs")
public class M3RecommendController {

    @GetMapping("/recommendations")
    public Map<String, Object> getRecommendations(@AuthenticationPrincipal String studentId) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "OK");
        response.put("requestId", UUID.randomUUID().toString());
        // Mock 数据构建...
        response.put("data", data);
        return response;
    }
}
```

## 7. 待实现/已知缺陷 (留待后续 PR)
- 当前 architecture 4.3 契约将 404 `JOB_LINK_INVALID` 放在列表接口上，若单条外链失效会导致整个列表 404。建议后续单独提 spec 变更，改为在 `JobItem` 中通过 `linkStatus = INVALID` 标记单条失效。
- 真实实现时应统一使用 `ApiResponse` 返回壳，不手拼 `Map<String, Object>`。
