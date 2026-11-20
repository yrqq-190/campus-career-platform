# T6：M3 推荐列表与推荐理由 (Mock 骨架)

## 接口定义
GET /api/v1/jobs/recommendations

## 成功响应 Mock (200 OK)
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

## 说明
- 对应 spec/AC：module-03 AC-1、AC-5
- 无命中技能时返回 `data: []`。
- 演示数据带 `isDemo: true` 标识。
