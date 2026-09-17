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

所有响应带 CORS 头；错误统一为 `{"error": "..."}` + 非 200。
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

新客户端使用 `before=<createdAt>:<id>` 游标，首屏不传；响应含 `hasMore`、`nextCursor`。
旧客户端的 `page/size` 继续可用。新插入、删除不会使游标翻页偏移。
发帖请求可带 `Idempotency-Key`（8–128 位字母、数字、下划线或连字符）；网络重试必须复用该值。
相同身份及 key 重放原留言，不再次计数；修改内容复用 key 返回 409，原留言已删除返回 410。
旧客户端不传 key 仍可发帖，但无法保证跨请求重试去重。
成功响应直接包含作者昵称和头像，不再让客户端等待刷新后才显示。

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
# 必须先迁移、配置密钥，再部署新版 Worker
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
