# T10 M6 面试登记与多级提醒 - 骨架与 Mock

## 一、接口定义
POST /api/v1/interviews

## 二、成功响应 Mock (201 Created)
```json
{
  "code": "OK",
  "requestId": "mock_d7e5f4a3",
  "data": {
    "interviewId": "itv_20261121_001",
    "status": "SCHEDULED",
    "reminders": [
      {
        "nodeType": "D3",
        "triggerAt": "2026-11-18T14:00:00+08:00"
      },
      {
        "nodeType": "D1",
        "triggerAt": "2026-11-20T14:00:00+08:00"
      },
      {
        "nodeType": "H2_DEPARTURE",
        "triggerAt": "2026-11-21T10:30:00+08:00"
      },
      {
        "nodeType": "M30",
        "triggerAt": "2026-11-21T13:30:00+08:00"
      },
      {
        "nodeType": "POST_REVIEW",
        "triggerAt": "2026-11-21T16:00:00+08:00"
      }
    ],
    "baselineFallback": false
  }
}
```

## 三、失败响应 Mock (500 REMINDER_SEND_FAILED)
```json
{
  "code": "REMINDER_SEND_FAILED",
  "message": "提醒发送失败，已重试3次",
  "requestId": "mock_err_002"
}
```

## 四、说明
- 对应 spec/AC：module-06 AC-1、AC-2、AC-8。
- 5次触发 = 4个节点(前3天/前1天/前2小时/前30分钟) + 结束后复盘。
- 前2小时按出发时间计算，若未填则按开始时间倒推。

## 五、页面结构 (UI 骨架)
1. **登记页**：表单（公司、岗位、开始时间选择器、出发时间选择器、地点/链接输入框）、[保存]按钮。
2. **详情页**：垂直时间轴视图展示 5 个提醒触发时间、[导出到日历(.ics)]按钮、[修改时间]和[取消面试]按钮。

## 六、Java 骨架代码 (Controller)
```java
package com.campus.career.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/interviews")
public class M6InterviewReminderController {
    
    @PostMapping
    public Map<String, Object> createInterview(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "OK");
        
        Map<String, Object> data = new HashMap<>();
        data.put("interviewId", "itv_20261121_001");
        data.put("status", "SCHEDULED");
        
        response.put("data", data);
        return response;
    }
}
```
