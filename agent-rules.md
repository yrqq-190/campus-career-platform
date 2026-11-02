# AI 协作规则（agent-rules.md）

> **定位**：AI 的"法律底线、强制标准"，非必要不修改；修改须经全员评审并递增版本号。
> **适用范围**：本仓库所有 AI 协作（Trae、Cursor、Copilot 等）。
> **项目技术栈**：前端 Vue 3 + Vite + TypeScript；后端 Spring Boot + MyBatis-Plus + MySQL；对象存储；Docker 部署。
> **业务边界**：详见 `context.md`。本文档仅约束代码与协作规范，不重复业务范围。

---

## 一、代码风格规范

### 1.1 通用原则

- 所有源码文件使用 **UTF-8 编码**，行尾统一 **LF**（Windows 提交前必须转换）。
- 缩进统一 **2 个空格**，禁止使用 Tab，禁止空格与 Tab 混用。
- 每行不超过 **120 字符**，超出必须换行。
- 文件末尾保留一个空行。
- 禁止行尾空白字符。

### 1.2 硬性禁止（违反即拒合）

#### 1.2.1 通用禁止

- ❌ 硬编码密钥、密码、Token、API Key。必须通过环境变量或配置中心注入。
- ❌ 提交 `.env`、密钥文件、IDE 配置（`.idea/`、`.vscode/`）到仓库。
- ❌ 留下 `TODO`、`FIXME`、`HACK` 而不创建对应 Issue。
- ❌ 复制粘贴超过 3 行的相同逻辑，必须抽取为方法/组件。
- ❌ 提交中包含调试输出：前端 `console.log` / `debugger`、后端 `System.out.println` / `printStackTrace`。
- ❌ 前端使用 `var`，后端使用 `Object` 作为方法参数或返回类型。
- ❌ 空 `catch` 块吞掉异常，至少记录日志并向上抛出或返回错误码。
- ❌ 魔法数字，必须定义为带注释的常量。

#### 1.2.2 前端禁止（Vue 3 + TypeScript）

- ❌ 使用 Vue 2 的 Options API，必须使用 Composition API + `<script setup lang="ts">`。
- ❌ 直接操作 DOM（`document.getElementById`、`querySelector`、`$refs` 操作 DOM）。
- ❌ `v-for` 中使用 `index` 作为 `:key`，必须使用业务唯一标识。
- ❌ 模板中写复杂逻辑，模板仅做渲染，逻辑放入 `computed` 或方法。
- ❌ 使用 `any` 类型，必须定义明确的 TypeScript 接口。
- ❌ 全局事件总线（`mitt`、`eventBus`），组件通信使用 props/emit 或 Pinia。
- ❌ 在组件内直接发起 HTTP 请求，必须通过 composable 或 store 封装。

#### 1.2.3 后端禁止（Spring Boot + MyBatis-Plus）

- ❌ Controller 中写业务逻辑，必须放入 Service 层。
- ❌ Service 中手动组装响应 DTO，必须使用 MapStruct。
- ❌ 使用 `@Autowired` 字段注入，必须使用构造器注入（`@RequiredArgsConstructor`）。
- ❌ 直接返回 Entity 给前端，必须经过 DTO/VO 转换。
- ❌ 事务方法（`@Transactional`）中调用外部 HTTP/RPC 接口，必须拆分到非事务方法。
- ❌ 拼接 SQL 字符串，必须使用 MyBatis-Plus 的参数绑定（`#{}`）或 QueryWrapper。
- ❌ `SELECT *`，必须明确查询字段。
- ❌ 循环中调用数据库，必须批量查询（`listByIds`、`in` 条件）。

### 1.3 注释规范

- 类、接口、公开方法必须有 Javadoc / JSDoc，说明用途、参数含义、返回值语义。
- 复杂业务逻辑加行内注释，说明"为什么这样做"，不说明"做了什么"。
- 注释与代码同步更新，注释与代码不一致视为 Bug。

---

## 二、命名规范

### 2.1 文件命名

| 类型 | 规范 | 示例 |
| --- | --- | --- |
| 普通文件 | kebab-case | `user-profile.vue`、`job-recommend.ts` |
| Vue 组件 | PascalCase | `JobCard.vue`、`InterviewReport.vue` |
| Java 类 | PascalCase | `UserController.java`、`ResumeParserService.java` |
| 前端测试 | 原名 + `.test.ts` | `user-profile.test.ts` |
| 后端测试 | 原名 + `Test.java` | `UserServiceTest.java` |
| 数据库迁移 | `V{版本号}__{描述}.sql` | `V1__create_user_table.sql` |
| ADR 文档 | `{四位编号}-{描述}.md` | `0001-repo-structure.md` |

### 2.2 前端命名（Vue 3 + TypeScript）

| 元素 | 规范 | 示例 |
| --- | --- | --- |
| 组件名 | PascalCase，多词 | `JobCard`、`InterviewSchedule` |
| 变量 | camelCase | `userProfile`、`jobList` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RESUME_SIZE`、`INTERVIEW_STATUS` |
| 类型/接口 | PascalCase | `User`、`JobRecommendParams` |
| 函数 | camelCase，动词开头 | `fetchJobList`、`parseResume` |
| 自定义事件 | kebab-case | `@update:status`、`@interview-complete` |
| Pinia store | `use{资源}Store` | `useUserStore`、`useInterviewStore` |
| Composable | `use{动作}` | `useFetchJobs`、`useInterviewTimer` |
| CSS class | BEM | `.job-card__title--active` |

### 2.3 后端命名（Java + Spring Boot）

| 元素 | 规范 | 示例 |
| --- | --- | --- |
| 包名 | 全小写，域名反转 | `com.campus.career` |
| 类名 | PascalCase，名词 | `UserController`、`ResumeEntity` |
| 接口 | PascalCase | `ResumeParser`、`InterviewService` |
| 方法 | camelCase，动词开头 | `getUserById`、`createInterview` |
| 变量 | camelCase | `userId`、`interviewList` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE`、`MAX_RETRY_TIMES` |
| Controller | `{资源}Controller` | `UserController`、`JobController` |
| Service 接口 | `{资源}Service` | `InterviewService` |
| Service 实现 | `{资源}ServiceImpl` | `InterviewServiceImpl` |
| Mapper | `{资源}Mapper` | `UserMapper` |
| Entity | `{资源}Entity` | `ResumeEntity` |
| DTO（入参） | `{资源}{动作}DTO` | `UserCreateDTO`、`InterviewQueryDTO` |
| VO（出参） | `{资源}VO` | `UserVO` |

### 2.4 数据库命名（MySQL）

| 元素 | 规范 | 示例 |
| --- | --- | --- |
| 表名 | snake_case 复数 | `users`、`interview_schedules` |
| 字段名 | snake_case | `user_id`、`created_at` |
| 主键 | `id` | `id` |
| 外键 | `{表名单数}_id` | `user_id`、`job_id` |
| 普通索引 | `idx_{字段名}` | `idx_user_phone` |
| 唯一索引 | `uk_{字段名}` | `uk_email` |
| 创建时间 | `created_at` | `created_at` |
| 更新时间 | `updated_at` | `updated_at` |
| 逻辑删除 | `is_deleted`，值 0=未删，1=已删 | `is_deleted` |
| 布尔字段 | `is_{状态}`，TINYINT(1)，0=false，1=true | `is_active` |

### 2.5 业务术语与代码命名映射

| 业务术语 | 前端变量/组件 | 后端类/字段 | 数据库表/字段 |
| --- | --- | --- | --- |
| 求职画像 | `jobProfile` / `JobProfileCard` | `JobProfile` / `jobProfile` | `job_profiles` |
| 匹配度 | `matchRate` / `MatchRateBar` | `MatchRate` / `matchRate` | `match_rate` |
| 意向清单 | `favoriteList` / `FavoriteJobList` | `FavoriteJob` / `favoriteJob` | `favorite_jobs` |
| 投递状态 | `applicationStatus` | `ApplicationStatus`（枚举） | `application_status` |
| 多级提醒 | `interviewReminder` / `ReminderTimeline` | `InterviewReminder` | `interview_reminders` |
| 到面率 | `attendanceRate` | `AttendanceRate` | `attendance_rate` |
| 面经 | `interviewExp` / `InterviewExpCard` | `InterviewExperience` | `interview_experiences` |
| 复盘 | `review` / `InterviewReviewForm` | `InterviewReview` | `interview_reviews` |
| 跳转记录 | `redirectRecord` | `RedirectRecord` | `redirect_records` |
| 模拟面试 | `mockInterview` / `MockInterviewPanel` | `MockInterview` | `mock_interviews` |
| 简历解析 | `resumeParse` | `ResumeParser` | — |
| 霍兰德问卷 | `hollandQuiz` | `HollandQuiz` | `holland_quizzes` |

### 2.6 Git 命名

| 元素 | 规范 | 示例 |
| --- | --- | --- |
| 分支名 | `{类型}/{简述-kebab-case}` | `feature/job-recommend`、`fix/interview-reminder`、`chore/week1-empty-pr`、`spike/llm-json-output` |
| Commit | `{类型}: {简述}` | `feat: add job recommendation module` |
| Tag | `v{主版本}.{次版本}.{修订号}` | `v1.0.0` |

**Commit 类型（固定枚举）**：

| 类型 | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 仅文档变更 |
| `style` | 代码格式（不影响逻辑） |
| `refactor` | 重构（不新增功能、不修 Bug） |
| `test` | 新增或修改测试 |
| `chore` | 构建工具、依赖、配置变更 |
| `spike` | 技术探针（仅 `docs/spike/` 目录） |

### 2.7 API 命名

| 元素 | 规范 | 示例 |
| --- | --- | --- |
| 路径前缀 | `/api/v{版本号}/` | `/api/v1/` |
| 资源路径 | 名词复数，kebab-case | `/api/v1/interview-schedules` |
| HTTP 方法 | GET 查询、POST 创建、PUT 全量更新、PATCH 部分更新、DELETE 删除 | — |
| 路径参数 | camelCase | `/api/v1/interviews/{interviewId}` |
| 查询参数 | camelCase | `?pageSize=10&keyword=java` |
| 响应统一结构 | `{ code, message, data }` | `{ "code": 200, "message": "success", "data": {} }` |

---

## 三、架构红线

### 3.1 技术选型红线

- ✅ 前端固定：Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus。
- ✅ 后端固定：Spring Boot 3.x + MyBatis-Plus + MySQL 8.0。
- ✅ 对象存储：本地文件系统（MVP1），接口预留 S3 兼容层。
- ✅ 部署：Docker Compose。
- ❌ 禁止引入上述清单外的框架，新增依赖必须先提 ADR。

### 3.2 业务边界红线

- ❌ 禁止抓取或缓存 BOSS 直聘等第三方平台的岗位数据，仅保存检索条件与跳转记录。
- ❌ 禁止复制面经正文，仅引用公开网页链接并标注来源。
- ❌ 禁止建设自有岗位库与站内投递闭环。
- ❌ 禁止实现企业端完整招聘管理。
- ❌ 禁止实现在线笔试与视频面试。
- ❌ 禁止实现摄像头行为与仪态分析。

### 3.3 大模型调用红线

- ✅ 大模型输出必须为结构化 JSON，通过 JSON Schema 校验后方可使用。
- ✅ 大模型调用超时固定 30 秒，失败重试最多 2 次，重试间隔 1 秒。
- ❌ 禁止将大模型原始输出直接返回前端，必须经过结构校验与字段映射。
- ❌ 禁止在大模型 prompt 中写入用户敏感信息（手机号、身份证号）。

### 3.4 语音交互红线

- ✅ MVP1 不实现语音功能，仅保留"识别失败降级为文字作答"的接口占位。
- ❌ 禁止在 MVP1 中集成语音识别或语音合成 SDK。

### 3.5 提醒功能红线

- ✅ 面试提醒必须通过定时任务（`@Scheduled`）扫描待办生成，禁止即时推送。
- ✅ 提醒渠道固定为站内消息 + 邮件 + .ics 日历文件。
- ❌ 禁止在面试登记时立即发送所有提醒，必须按时间节点（前 3 天、前 1 天、前 2 小时、前 30 分钟、结束后）分别触发。

### 3.6 演示数据红线

- ✅ 所有演示用岗位、企业、面经数据必须标注"演示数据"。
- ✅ 模拟面试评分页面必须标注"仅供参考，不作为评价依据"。
- ❌ 禁止将演示数据与真实数据混用同一数据表，演示数据使用独立 `demo_` 前缀表或 `is_demo` 字段。

### 3.7 分层红线

- ✅ 严格四层：Controller → Service → Mapper → Entity。
- ❌ 禁止跨层调用（Controller 不能直接调 Mapper）。
- ❌ 禁止 Service 之间循环依赖。

---

## 四、安全约束

### 4.1 认证与授权

- ✅ 使用 JWT 做认证，Token 有效期 2 小时，刷新 Token 有效期 7 天。
- ✅ 密码使用 BCrypt 加密存储，禁止明文。
- ✅ 接口按角色鉴权：学生、企业招聘方、就业指导中心。
- ❌ 禁止在 URL 中传递 Token，必须放在 Authorization Header。
- ❌ 禁止前端存储敏感信息到 localStorage，Token 存 sessionStorage 或 HttpOnly Cookie。

### 4.2 数据安全

- ✅ 简历文件存储使用随机文件名，禁止使用原始文件名。
- ✅ 简历文件访问必须经过鉴权，禁止直接通过 URL 访问。
- ✅ 个人信息采集遵循最小必要原则，采集前明示用途。
- ✅ 学生可查看、更正、删除个人信息，必须提供删除入口。
- ❌ 禁止向第三方提供学生简历与个人信息。

### 4.3 接口安全

- ✅ 所有接口必须校验请求参数，使用 `@Valid` + 注解校验。
- ✅ 分页接口默认 pageSize=10，最大不超过 100。
- ✅ 写操作接口必须校验数据归属，禁止越权操作。
- ❌ 禁止将内部异常堆栈返回前端，统一返回错误码与提示信息。

### 4.4 SQL 安全

- ✅ 所有查询使用参数绑定，禁止字符串拼接。
- ✅ 批量操作必须限制单次数量（最多 500 条）。
- ❌ 禁止在代码中动态拼接表名或字段名。

---

## 五、构建与输出命令约定

### 5.1 前端命令

| 操作 | 命令 |
| --- | --- |
| 安装依赖 | `npm install` |
| 开发模式 | `npm run dev` |
| 生产构建 | `npm run build` |
| 预览构建结果 | `npm run preview` |
| 代码检查 | `npm run lint` |
| 单元测试 | `npm run test` |

### 5.2 后端命令

| 操作 | 命令 |
| --- | --- |
| 编译打包 | `mvn clean package -DskipTests` |
| 运行测试 | `mvn test` |
| 本地运行 | `mvn spring-boot:run` |
| 构建镜像 | `mvn spring-boot:build-image` |

### 5.3 数据库命令

| 操作 | 命令 |
| --- | --- |
| 执行迁移 | `mvn flyway:migrate` |
| 查看迁移状态 | `mvn flyway:info` |

### 5.4 Docker 命令

| 操作 | 命令 |
| --- | --- |
| 启动全部服务 | `docker-compose up -d` |
| 停止全部服务 | `docker-compose down` |
| 查看日志 | `docker-compose logs -f` |

### 5.5 代码提交前检查清单

提交前必须逐项确认，全部通过方可提交：

- [ ] 前端：`npm run lint` 无 error
- [ ] 前端：`npm run build` 构建成功
- [ ] 后端：`mvn test` 全部通过
- [ ] 后端：无 `System.out.println`、无空 catch
- [ ] 全量：无密钥、无 `.env`、无 IDE 配置
- [ ] 全量：无调试代码（`console.log`、`debugger`）

---

## 六、AI 协作规则

1. **先读后写**：AI 修改代码前必须先阅读相关文件的完整内容，禁止基于片段猜测。
2. **小步提交**：每次提交只做一件事，禁止一次提交跨多个功能模块。
3. **先测试后提交**：新增功能必须配套测试，修复 Bug 必须先写复现测试。
4. **不越界**：AI 只在分配的任务范围内工作，禁止修改其他组的文件。
5. **不改红线**：本文件的"硬性禁止"与"架构红线"不可协商，AI 不得绕过。
6. **技术探针隔离**：本周所有实验性代码只能放入 `docs/spike/`，禁止进入 `src/`。

---

> **版本**：v1.0（D3 完稿）
> **生效日期**：2026-11-02
> **变更说明**：本文件为稳定规范，修改需经全员评审并更新版本号。
