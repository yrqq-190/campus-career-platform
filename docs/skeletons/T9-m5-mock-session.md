# T9 M5 文字模拟面试 - 骨架与 Mock

## 一、接口定义
GET /api/v1/mock-interviews

## 二、成功响应 Mock (201 Created)
```json
{
  "code": "OK",
  "requestId": "mock_a4b2c1d0",
  "data": {
    "sessionId": "mi_20261121_001",
    "status": "IN_PROGRESS",
    "totalQuestions": 5,
    "currentQuestion": {
      "questionId": 1,
      "content": "请描述一次你解决线上故障的经历。",
      "dimension": "项目经验"
    }
  }
}
```

## 三、失败响应 Mock (400 JD_REQUIRED)
```json
{
  "code": "JD_REQUIRED",
  "message": "请先输入岗位或粘贴 JD",
  "requestId": "mock_err_001"
}
```

## 四、说明
- 对应 spec/AC：module-05 AC-1、AC-3、AC-4
- 抽题与评分响应时间不超过 1 秒；接口超时定为 10 秒，失败重试 1 次。
- 本模块不涉及大模型调用，严格使用本地题库与关键词匹配。

## 五、页面结构 (UI 骨架)
1. **准备页**：标题“文字模拟面试”、输入区（选择已投递岗位或粘贴 JD 文本框）、[开始面试]按钮。
2. **答题页**：顶部进度条(1/5)、题目展示区、答题多行文本框、[提交作答]按钮。
3. **报告页**：总分大数字展示、四维雷达图（专业知识/逻辑表达/项目经验/岗位匹配度）、文字评价总结、[归档到看板]按钮。

## 六、Java 骨架代码 (Controller)

```java
package com.campus.career.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/mock-interviews")
public class M5MockInterviewController {

    @PostMapping
    public Map<String, Object> startInterview(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "OK");
        
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "mi_20261121_001");
        data.put("status", "IN_PROGRESS");
        
        response.put("data", data);
        return response;
    }
}
```
