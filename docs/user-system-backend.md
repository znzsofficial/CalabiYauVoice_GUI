# 用户系统后端实现（Cloudflare）

本文档记录自定义 Wiki 用户档案、留言板与点赞功能的完整后端实现，
包括云资源配置、数据库结构、API 契约、鉴权模型与运维操作。

前端入口：
- 侧栏「留言板」页（公共留言板，匿名可发）
- 用户信息弹窗（自定义资料编辑 + 点赞）
- 网页管理后台 `/admin`

---

## 1. 架构概览

```
客户端 (Android / Desktop / Web)
   │
   ├── 读档案 / 留言 / 点赞数          GET  /api/user/*
   ├── 发留言 / 点赞（带 Wiki Cookie）  POST /api/user/*
   ├── 更新资料 / 上传头像（带 Cookie） PUT/POST /api/user/*
   │        ▼
   │   Cloudflare Pages Worker (downloadPage/src/api/_worker.js)
   │        ├── D1  calabiyau-db       用户档案 / 留言 / 点赞 / 限流
   │        ├── R2  calabiyau-releases  APK
   │        ├── R2  calabiyau-user-assets 用户头像
   │        └── MediaWiki userinfo API  Cookie 身份校验
   │
   └── 管理后台 /admin（密码保护） → /api/admin/*
```

- 客户端共享封装：`shared/src/commonMain/kotlin/data/CustomUserApi.kt`
- 数据模型：`shared/src/commonMain/kotlin/data/CustomUserProfile.kt`

Worker 按职责拆分：`_worker.js` 只负责路由和统一错误处理；`http.js` 负责限量读取与参数校验，
`auth.js` 负责 Wiki/管理员鉴权及访客 HMAC，`writes.js` 负责原子写入门禁，
`profiles.js`、`avatars.js`、`comments.js`、`likes.js`、`admin.js` 是业务模块，
`releases.js` 负责 APK Range/HEAD/ETag，`proxy.js` 负责上游代理。
`npm run build` 使用 esbuild 将模块打包为 `dist/_worker.js`；`webStatic` 不再覆盖此产物。

## 2. 云资源与配置

| 资源 | 名称 / ID | 绑定 | 用途 |
|---|---|---|---|
| D1 | `calabiyau-db`<br>`cb8d4bdf-540c-420e-931e-6e6c1da88cd1` | `DB` | 档案 / 留言 / 点赞 / 限流表 |
| R2 | `calabiyau-releases` | `RELEASES` | APK 分发 |
| R2 | `calabiyau-user-assets` | `USER_ASSETS` | 用户头像 |
| Pages 密钥 | `ADMIN_PASSWORD` | `env.ADMIN_PASSWORD` | 管理后台密码 |
| Pages 密钥 | `GUEST_SECRET` | `env.GUEST_SECRET` | 独立随机访客 HMAC 密钥，至少 32 字符；不得复用管理员密码 |
| Pages 密钥 | `GITHUB_TOKEN` | `env.GITHUB_TOKEN` | Star 计数（既有） |

配置见 `downloadPage/wrangler.jsonc`（含 `migrations_dir: ./migrations`）。

密钥管理（不进仓库）：

```powershell
# 查看当前登录
npx wrangler whoami

# 修改管理后台密码（改完需重新 webPush 部署生效）
cmd /c "echo 新密码| npx wrangler pages secret put ADMIN_PASSWORD --project-name calabiyauwiki"
```

## 3. 数据库结构

迁移脚本在 `downloadPage/migrations/`，依次：

### 0001_create_user_profiles.sql — 用户档案

```sql
CREATE TABLE user_profiles (
    bid TEXT PRIMARY KEY,            -- B站 UID（Wiki 用户名）
    wiki_user_id INTEGER,            -- MediaWiki 内部 ID
    custom_name TEXT,                -- 自定义昵称（≤30 字）
    avatar_url TEXT,                 -- 头像相对路径（/api/user/avatar/...）
    bio TEXT,                        -- 签名（≤200 字）
    badge TEXT,                      -- 徽章（≤20 字）
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX idx_user_profiles_wiki_id ON user_profiles(wiki_user_id);  -- 非唯一：允许改名重建
```

- `bid` 是主键：用户在 Wiki 改名（新 bid）会生成新档案，旧档保留但不可达。
- 头像只存相对路径，读取时由 Worker 拼请求 origin 返回绝对 URL。

### 0002_create_comments_likes.sql — 留言板与点赞

```sql
CREATE TABLE profile_comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_bid TEXT NOT NULL,        -- 被留言的用户；公共板为 "__public__"
    author_bid TEXT NOT NULL,        -- 作者 BID；匿名固定为 "anon"
    author_name TEXT,                -- 匿名昵称（0003 新增；登录用户为 NULL）
    author_ip_hash TEXT,             -- 匿名限流用 IP 哈希（0003 新增）
    content TEXT NOT NULL,           -- ≤200 字
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX idx_profile_comments_target ON profile_comments(target_bid, created_at DESC);

CREATE TABLE profile_likes (
    target_bid TEXT NOT NULL,
    author_bid TEXT NOT NULL,        -- 每人对每目标唯一 → 点赞/取消
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    PRIMARY KEY (target_bid, author_bid)
);
```

### 0003_anonymous_comments.sql — 匿名限流

```sql
CREATE TABLE write_throttle (
    key TEXT PRIMARY KEY,            -- "bid:<bid>" 或 "ip:<sha256>"
    last_at INTEGER NOT NULL
);
```

## 4. 用户 API（/api/user/*）

`0004_anonymous_tag.sql` 增加匿名展示编号 `author_tag`。
`0005_backend_consistency.sql` 增加：
- `write_throttle.token`：标识一次成功取得写权限的请求，并索引过期时间。
- `write_requests`：按 `(actor, request_key)` 保存留言指纹及 ID，支持 7 天内幂等重放。
- `avatar_assets`：记录对象归属、创建时间和 active/deleting 状态，避免并发清理误删。
- `maintenance_state`：保存旧 R2 对象扫描游标；每次维护只处理一页。
- `(target_bid, created_at DESC, id DESC)` 留言游标索引。

`0006_comment_replies.sql` 增加 `root_id`、`reply_to_id`、`deleted_at` 及讨论索引。
旧留言的 `root_id` 为空，保持主留言语义；回复只归属顶层主留言，没有无限嵌套。

`0007_comment_moderation.sql` 增加 `hidden_at`、`pinned_at` 和 `admin_audit` 操作记录。
此迁移必须先于内容管理 Worker 部署执行。

`0008_reply_notifications.sql` 增加登录用户回复通知表。回复写入和通知生成在同一个 D1 batch 中；
通知只发送给直接被回复的登录用户，匿名作者、自回复不产生通知。通知不保存原文快照，
内容在读取时按当前隐藏/删除状态返回。

`0009_stable_user_identity.sql` 将留言作者和通知收件人绑定到不可变的 MediaWiki 用户 ID：
留言保存 `author_wiki_user_id`，通知保存 `recipient_wiki_user_id`；删除归属、自回复判断、
通知读写都优先按 Wiki 用户 ID 匹配，BID 仅用于显示和迁移前旧行的兼容回退。
迁移按 `user_profiles.wiki_user_id` 回填旧行，用户改名后仍能读取旧通知、删除自己的旧留言，
也不会因 BID 变化被误当成新身份。同一 Wiki 用户即使 BID 相同也不会跨账号合并。

所有响应带 CORS 头；错误统一为 `{"error": "..."}` + 非 200；业务错误额外带结构化
`errorCode`（如 `DISCUSSION_UNAVAILABLE`、`FOCUS_UNAVAILABLE`、`REPLY_TARGET_UNAVAILABLE`、
`IDEMPOTENT_RESULT_DELETED`、`IDEMPOTENT_RESULT_HIDDEN`），客户端只对这类权威后端错误做缓存失效。
头像字段在读取时拼成绝对 URL（`origin + 相对路径`）。

### 档案

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/user/profile?bid=` 或 `?wiki_id=` | 公开 | 单查；未建档返回 `{"profile": null}`，no-store |
| PUT | `/api/user/profile` | Cookie | 更新自己的档案；`avatarUrl` 只接受本服务 `/api/user/avatar/` 路径（相对/绝对均可，落库存相对路径）；替换头像时删旧 R2 对象 |

PUT 请求体：

```json
{ "customName": "...", "avatarUrl": "...", "bio": "...", "badge": "..." }
```

字段校验：昵称 ≤30、签名 ≤200、徽章 ≤20；超长或地址非法返回 400。
用户 PUT 保持全量替换语义以兼容旧客户端；管理员 PUT 为部分更新，省略字段保留、null 清空。
保存头像时还验证对象确实存在且属于目标用户；只允许自己的 active 对象。

### 头像

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/user/avatar` | Cookie | multipart 字段 `file`；≤2MB；PNG/JPEG/WEBP；魔数与声明 MIME 一致性校验 |
| GET | `/api/user/avatar/<key>` | 公开 | 流式读取 R2，Cache 1 天 |

- 返回 `{ "avatarUrl": "/api/user/avatar/avatars/<uuid>.<ext>", "objectKey": "..." }`。
- 新对象使用随机 key，归属保存在 D1；旧 `<bid>_<16位hash>` key 精确解析 BID 后懒迁移。
- 服务端缓存 1 天；支持 HEAD。魔数校验只识别格式，不等同于完整图像解码。
- 请求读取期间限制实际字节数：图片 ≤2MB，multipart 额外允许 64KB 开销；超限 413。
- 替换/删除档案后，旧对象只有在属于该用户且没有任何档案引用时才进入 deleting 状态并删除。
- R2 删除失败留下可重试状态。上传成功但保存失败产生的孤儿，在超过 24 小时后通过维护接口回收。
- R2 与 D1 没有跨服务事务：上传采用限流占用和失败补偿，进程意外中断最多占用 30 秒窗口。

### 留言板

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/user/comments?bid=&page=&size=` | 公开 | 按时间倒序；带作者头像/昵称（档案 LEFT JOIN，匿名回退昵称/BID）；单页 ≤50 |
| POST | `/api/user/comments` | 可选 | 登录带身份（限流 10s）；未登录匿名（`authorName` 可选 ≤20 字，默认「访客」，按 IP 哈希限流 30s）；内容 ≤200 字 |
| DELETE | `/api/user/comments?id=` | Cookie | 仅作者本人可删；匿名留言只能走管理接口 |
| GET | `/api/user/replies?rootId=&before=&size=` | 公开 | 返回主留言 `root` 和游标分页回复 `comments`；按时间/id 倒序 |
| POST | `/api/user/replies` | 可选 | `{targetBid,replyToId,content,authorName?}`；与发帖共享限流及幂等契约 |
| GET | `/api/user/notifications?before=&size=` | Cookie | 当前用户的回复通知、未读数和游标；内容不可用时只返回状态 |
| PUT | `/api/user/notifications` | Cookie | `{id}` 标记单条已读，或 `{throughId}` 标记当前用户更早通知已读；重复调用幂等 |

新客户端使用 `before=<createdAt>:<id>` 游标，首屏不传；响应含 `hasMore`、`nextCursor`。
旧客户端的 `page/size` 继续可用。新插入、删除不会使游标翻页偏移。
发帖请求可带 `Idempotency-Key`（8–128 位字母、数字、下划线或连字符）；网络重试必须复用该值。
相同身份及 key 重放原留言，不再次计数；修改内容复用 key 返回 409，原留言已删除返回 410。
旧客户端不传 key 仍可发帖，但无法保证跨请求重试去重。
成功响应直接包含作者昵称和头像，不再让客户端等待刷新后才显示。

#### 两层讨论与删除规则

- 主列表仅返回主留言（旧版客户端不混入回复）；新增 `replyCount` 表示未删除回复数。
- 回复主留言或另一条回复均传 `replyToId`，服务端计算 `rootId`，不信任客户端传入的根 ID。
- 回复目标必须与 `targetBid` 属于同一留言板，且目标和主留言都未删除。
  SQL 写入时再次检查，避免校验后主留言被并发删除；拒绝时释放限流占用。
- 返回字段包括 `rootId`、`replyToId`、`replyToName`、`replyToTag`、`deleted`。
- 回复列表包含删除占位，`total` 为含占位的数量；主留言 `replyCount` 是有效回复数。
- 删除会清空原文并设置 `deleted_at`。公开接口不返回删除内容、作者或头像；引用显示“已删除留言”。
- 主留言删除后已有回复仍可读取，但整条讨论停止接收新回复；无有效回复的已删除主留言从主列表移除。
- 重复删除同一作者的留言仍成功；已删除留言的幂等重放返回 410，不能借重试复活内容。
- 管理删除接口采用相同规则，管理列表标记主留言/所属讨论和删除状态。
- Android 在同一页面进入讨论详情，支持回复目标取消、分页重试、分别保存主列表及讨论草稿。
  请求和状态由 `MessageBoardState.kt` 管理，离开页面取消请求，切换讨论丢弃旧请求结果。
- 草稿与未确认发送的原始内容、回复目标、身份标识、幂等 key 按讨论一起保存在本地，不存 Cookie。
  切换讨论、页面重建后重试同一内容继续使用原 key；确认成功才清除。超过服务端 7 天去重窗口的
  未确认请求不会自动作为新请求重发，需先核对发送结果或明确修改内容。
- 刷新发现原回复目标已删除时，只标记目标不可用，保留原目标及待确认请求；不会自动改为回复主楼。
  用户核对发送结果后可明确选择新的回复目标，再创建新请求。此不可用状态也随草稿持久化。
- 删除成功立即将本地内容及引用改为占位、关闭已删除主留言的回复输入；刷新失败不会恢复原文。
- 主列表和已打开讨论各自保留已加载数据、游标、滚动位置。返回主列表只更新变动主留言，避免重回第一页。
- 发帖/删除期间，主列表与讨论详情的系统返回和顶部返回统一限制；意外离开后待确认请求仍可恢复。
- 个人板主留言的条件 INSERT 同样重新检查目标档案存在性，校验后被删除时返回 409 并释放限流。
- 登录用户在留言板顶部看到未读通知数；点击通知先导航到对应讨论，已读标记随后独立异步更新，
  认证服务临时不可用不影响打开公开可读的讨论。
  自己回复自己不通知，匿名用户不接收通知；匿名用户回复登录用户仍会给对方生成通知。
  隐藏/删除回复、主留言或直接被回复的留言后，通知保留但只显示状态，不返回内容或作者信息。

#### 回复通知契约

- 只通知直接被回复留言的作者；回复另一条回复不会额外通知主楼作者。仅统计上线后的回复，不回填历史通知。
- GET 响应含 `notifications`、`unreadCount`、`latestId`、`nextCursor`；按通知 ID 倒序，`before` 使用通知 ID。
- PUT `{id}` 仅标记本人的单条通知；`{throughId}` 标记本人编号不超过边界的通知。
  对其他用户或不存在的 ID 不执行变更，重复调用不会重复计数。不可见通知仍计入未读，用户可正常标记已读。
- Android 的“全部已读”边界固定为首屏返回的 `latestId`，翻页期间新到的通知不会被误标已读。
- 通知和回复、幂等记录共用同一事务；通知写入失败，回复和限流占用一起回滚。重放已有发帖结果不重复通知。
- `GET /api/user/replies?rootId=&focusId=` 返回从目标回复开始向更早内容的窗口，用于直接定位旧回复。
  `focusId` 与 `before` 不可同时使用，后续翻页使用返回游标；“查看最新回复”退出定位模式。
- 未读数在进入留言板、刷新、打开通知列表时拉取；本阶段不包含后台系统推送或长连接。
- 通知按不可变的 Wiki 用户 ID（`recipient_wiki_user_id`）归属；迁移前的旧行按 `recipient_bid` 兼容读取，
  0009 回填后改名不影响归属。

公共留言板 `target_bid = "__public__"`；个人档案留言板 `target_bid = <该用户 bid>`。
发留言与点赞的 `targetBid` 为 `__public__` 之外时，要求 `user_profiles` 中该档案已存在
（防任意字符串刷垃圾板），否则 404。

### 点赞

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/user/likes?bid=` | 公开 | `{count, likedByMe}`；带有效 Cookie 才有 `likedByMe` |
| PUT | `/api/user/likes` | Cookie | `{targetBid}`；设为已赞，重复请求不反转；返回 `{liked, count}` |
| DELETE | `/api/user/likes` | Cookie | `{targetBid}`；取消点赞，重复请求不反转 |
| POST | `/api/user/likes` | Cookie | 兼容 2.1.10 的 toggle；在单个事务中执行；新客户端不用此入口 |

## 5. 管理后台（/api/admin/*，/admin 页面）

### 内容管理

- `PUT /api/admin/comment`：`{id,action,reason?}`；action 为 `hide/restore/pin/unpin`，原因最多 200 字。
- 隐藏不清空原文，可恢复；删除清空原文，不可恢复。隐藏主留言使整个讨论不可公开访问。
- 回复单独隐藏后，恢复主留言不会取消回复自己的隐藏状态。公开引用只显示“已隐藏留言”，不返回被隐藏作者的昵称/编号。
- 隐藏检查覆盖主列表、讨论详情、回复写入和幂等重放；管理列表仍可查看并恢复隐藏内容。
- 每个板最多 3 条置顶，只允许未删除且未隐藏的主留言；隐藏或删除自动取消置顶，恢复不自动重新置顶。
- 主列表返回独立 `pinnedComments`；普通 `comments` 保持完整时间序列（含置顶），游标不依赖置顶状态。
  新 Android 顶部展示置顶区，并按 ID 从普通展示区去重；旧客户端继续看到普通时间序列。
- `GET /api/admin/audit?page=` 返回管理操作记录，倒序分页。记录置顶/隐藏/恢复、留言删除、档案编辑及删除。
- 状态修改和审计写入使用同一 D1 事务，审计失败整体回滚。重复设定同一状态不重复记录。
- 当前管理员鉴权只有共享密码，因此 actor 固定为 `admin`，不能区分具体个人；不记录凭据、Cookie 或原文快照。
  moderation 操作支持填写原因；档案编辑/删除及旧留言删除接口的原因留空。
- Android 每次进入讨论重新校验可见性；401/403/404/410 清空失效讨论，超时、断网、503 等临时失败保留已有内容及游标。
- 已删除主留言仍可隐藏或取消隐藏整条讨论；取消隐藏仅恢复讨论可见性，不恢复原文，也不能重新置顶。
  已删除回复不能恢复原文。重复删除返回幂等成功，仅首次发生删除状态变化时写审计；不存在或无权访问返回 404。
- 通知列表临时刷新失败保留上次未读数、列表、游标和“全部已读”边界，并显示数据未刷新的提示；鉴权失效或账号切换清空缓存。

鉴权：请求头 `X-Admin-Password` 与 `env.ADMIN_PASSWORD` 比较
（两侧 SHA-256 后比对，密钥值做 trim 防御）。
哈希字节使用定时安全比较，不直接比较字符串。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/profiles?q=&page=&size=` | 搜索（BID/昵称模糊、WikiID 精确），按 updated_at 倒序 |
| PUT | `/api/admin/profile` | 编辑任意档案（`bid` 必填；头像校验与用户接口一致；清空头像删 R2 对象） |
| DELETE | `/api/admin/profile?bid=` | 删除档案 + R2 头像 + 该用户留言板与点赞 |
| GET | `/api/admin/comments?page=&size=&q=` | 全站留言列表（跨目标，含匿名昵称；按内容/作者/目标搜索） |
| DELETE | `/api/admin/comment?id=` | 删除任意留言 |
| POST | `/api/admin/maintenance` | 有界清理头像、24h 过期限流记录、7 天前的幂等记录；可由运维定期调用 |

页面 `downloadPage/admin.html`（`/admin` 由 Pages pretty-URL 解析）：
密码存 sessionStorage；顶部切换「用户档案 / 留言板管理」两个区块；
档案：列表 / 搜索 / 分页 / 编辑 / 删除 / 头像预览；
留言：列表 / 搜索 / 分页 / 删除；已加 `noindex`。

## 6. 鉴权与限流模型

- **用户写操作**（改档案、传头像、发留言、点赞、删自己的留言）：
  仅将显式请求头 `X-Wiki-Cookie` 转发到
  `MediaWiki action=query&meta=userinfo`，取 `id != 0` 且非 anon 的
  `name`（即 BID）与 `id`（即 wiki_user_id）。
  请求带浏览器 UA / Referer / Origin，避免 EdgeOne WAF 拦截。
- **管理操作**：`X-Admin-Password` 头。
- **鉴权失败分类**：无凭据才允许匿名；凭据失效返回 401；上游超时/风控/数据错误返回 503。
  鉴权请求超时 5 秒，不会把登录用户悄悄降级成匿名。站点普通 Cookie 不转发。
- **限流**：留言、档案使用 D1 batch 条件写入事务；并发只有一次 claim 成功，SQL 失败连同计数回滚：
  - 保存档案：登录 10 秒/次（key=`uprof:<bid>`）
  - 上传头像：登录 30 秒/次（key=`uav:<bid>`）
  - 发留言：登录 10 秒/条（key=`bid:<bid>`）、匿名 30 秒/条（key=`ip:<HMAC>`）
  - 点赞：幂等设定状态，无限流
  - 拒绝响应带 `Retry-After`；过期清理移至维护接口，不再每次写入扫描表。
- **匿名留言**：不可自行删除（无身份凭证），违规内容由管理后台清理。
- **访客编号**：标准 HMAC-SHA256 + 独立 GUEST_SECRET，显示 10 位十六进制后缀；没有配置密钥则返回 503，不退化为固定密钥。
  新 Android 保存安装级 UUID，并通过 `X-Guest-Id` 发给服务端，因此同网络不同安装可区分、换网络编号保持。
  旧客户端按 IP 派生；限流始终按 IP，不能通过换 UUID 绕过。编号不是身份凭证，仍可能碰撞。
  旧留言保留原编号；轮换 GUEST_SECRET 会改变新留言编号。
- **请求校验**：JSON 实际读取上限 16KB；类型/格式错误 400，超限 413。

## 7. 客户端契约要点

- 字段命名（camelCase）：`bid` / `wikiUserId` / `customName` / `avatarUrl` /
  `bio` / `badge` / `updatedAt`。
- `SharedJson` 配置 `ignoreUnknownKeys = true`，新增字段向后兼容。
- 头像 URL：上传返回相对路径；读取/保存档案返回绝对 URL；D1 存相对路径。
- Android 上传管线：选图 → 采样解码 → 全屏方形裁切（拖动/双指缩放，
  `AvatarCropDialog.kt`）→ 限制 512px →
  WEBP 质量 85（API 30+ 用 `WEBP_LOSSY`）→ 上传。

## 8. 部署与运维

```powershell
# 必须先迁移（包含 0005、0006）、配置密钥，再部署新版 Worker
# 以下命令在 downloadPage 目录执行
cd downloadPage
npx wrangler@4.130.0 d1 migrations apply calabiyau-db --remote
npx wrangler@4.130.0 pages secret put GUEST_SECRET --project-name calabiyauwiki

# D1 查询 / 备份
npx wrangler@4.130.0 d1 execute calabiyau-db --remote --command "SELECT ..."
npx wrangler@4.130.0 d1 export calabiyau-db --remote --output backup.sql
```

注意：

- `npx wrangler` 可能拉到新版导致 OAuth 授权异常（7403），
  固定 `npx wrangler@4.130.0` 可复现已验证行为。
- 迁移和密钥准备好后，在项目根目录执行 `.\gradlew.bat webPush`。
- 测试：downloadPage 目录 `npm run test:worker`（Miniflare/workerd + 本地 D1/R2），`npm run check`、`npm run build`。
- `node -c` 只能验证语法，不能替代并发、鉴权、事务和 Range 回归测试。
- 维护调用不会自动调度；部署后定期使用管理凭据调用 `/api/admin/maintenance`。
  每次扫描 100 个 R2 对象、最多回收 100 个过期对象；旧对象扫描游标持久化，可重复执行。
  每次最多删除各 1000 条过期限流/幂等记录，积压时重复调用即可。
- APK 接口支持 GET/HEAD、单范围与 suffix Range、If-Range、ETag/304 和 416；HEAD 返回完整大小。

## 9. 快速验证

```powershell
# 读（公开）
curl.exe "https://wiki.nekolaska.vip/api/user/profile?bid=123456789"
curl.exe "https://wiki.nekolaska.vip/api/user/comments?bid=__public__"
curl.exe "https://wiki.nekolaska.vip/api/user/likes?bid=123456789"

# 写（应 401）
curl.exe -X PUT -H "Content-Type: application/json" -d "{}" "https://wiki.nekolaska.vip/api/user/profile"

# 管理（应 200）
curl.exe -H "X-Admin-Password: <密码>" "https://wiki.nekolaska.vip/api/admin/profiles?page=1"
```
