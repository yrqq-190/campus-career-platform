# Spike 02：接口契约 Mock（M4 409 / M6 幂等重复触发）

## 1. 验证目标
通过静态 Mock 模拟 M4 的 409 响应与 M6 的幂等结果，验证前端表现，并核对契约字段。

## 2. 动作与预期前端表现

### 2.1 M4 投递登记 409 Mock
前端连续发送两次相同的投递请求。

**请求报文（第二次）：**
```json
POST /api/v1/applications
{
  "jobId": "J001",
  "status": "已投递"
}
```

**响应报文：**
```json
HTTP/1.1 409 Conflict
{
  "code": "ALREADY_APPLIED",
  "message": "您已投递过该岗位，请勿重复提交",
  "requestId": "req_mock_409_01"
}
```

**前端实际表现**：页面成功弹出可见的 Toast 提示（文案与 message 一致），并且提交按钮在请求期间被置灰，防止用户连续狂点。

### 2.2 M6 面试提醒幂等 Mock
（注：M6 无 409，幂等靠唯一约束拦截，因此重复触发 Mock 为 200 返回）

**请求报文（内部定时任务触发第二次）：**
```json
POST /api/v1/internal/reminders/trigger
{
  "interviewId": "itv_001",
  "nodeType": "D3"
}
```

**响应报文：**
```json
HTTP/1.1 200 OK
{
  "code": "OK",
  "data": {
    "nodeType": "D3",
    "sendStatus": "SENT",
    "message": "该节点已发送，执行幂等拦截"
  }
}
```

**前端实际表现**：页面的提醒列表中，D3 节点仅出现一次，未产生重复的通知红点或重复弹窗。

## 3. 契约字段差异清单
- **契约多余字段**：M4 响应体中的 `requestId` 对前端展示无用，仅用于后端排障溯源。
- **契约缺失字段**：M6 缺少 `reminderId`。若前端需手动“忽略”某条提醒，缺少该主键无法向服务端发起精准请求。建议在 4.6 契约中补充。

## 4. 结论
静态 Mock 验证通过（结论：支持）。前端具备防抖与 409 弹窗捕获能力；M4 契约可维持原状，M6 契约需在后续迭代中补充 `reminderId` 字段（与 `results.md` 的 S2 节完全对齐）。
