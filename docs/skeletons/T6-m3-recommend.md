# T6：M3 推荐列表与推荐理由 (骨架)

## 1. 接口契约说明
- **URL**: `GET /api/v1/jobs/recommendations`
- **请求参数**: `studentId` (可选，String)
- **对应 spec/AC**: module-03 AC-1、AC-5

## 2. DTO / VO 结构
### RecommendResponse (VO)
- `code`: String (状态码，如 "OK")
- `data`: List<JobItem>

### JobItem (DTO)
- `jobId`: String (岗位 ID)
- `title`: String (岗位标题)
- `reason`: String (命中理由，如 "命中技能：Vue、JavaScript")
- `sourceUrl`: String (外链地址)
- `isDemo`: Boolean (是否为演示数据)

## 3. Mock JSON (成功响应 200 OK)
```json
{
  "code": "OK",
  "data": [
    {
      "jobId": "J001",
      "title": "前端开发实习生",
      "reason": "命中技能：Vue、JavaScript",
      "sourceUrl": "https://example.com/job/1",
      "isDemo": true
    }
  ]
}
```

**无命中/空态 Mock：**

```json
{
  "code": "OK",
  "data": []
}
```

## 4. 错误码
- `401` -（无业务码）：未登录或 Token 失效
- `404` `JOB_LINK_INVALID`：岗位外链失效（对应 AC-3）

## 5. 页面结构说明
- 卡片列表展示 `title` 和 `reason`。
- 若 `isDemo` 为 true，卡片右上角显示「演示数据」标签。
- 若无推荐（`data` 为空数组），展示空态提示：「暂无匹配岗位，请完善技能标签」。

## 6. 后续真实 Controller 代码参考

> 第 10 周脚手架 #11 合并后，直接移入 src/ 即可

```java
package com.campus.career.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/jobs")
public class M3RecommendController {

    @GetMapping("/recommendations")
    public Map<String, Object> getRecommendations(@RequestParam(required = false) String studentId) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "OK");
        List<Map<String, Object>> data = new ArrayList<>();
        // Mock 数据...
        response.put("data", data);
        return response;
    }
}
```
