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

## 2. 云资源与配置

| 资源 | 名称 / ID | 绑定 | 用途 |
|---|---|---|---|
| D1 | `calabiyau-db`<br>`cb8d4bdf-540c-420e-931e-6e6c1da88cd1` | `DB` | 档案 / 留言 / 点赞 / 限流表 |
| R2 | `calabiyau-releases` | `RELEASES` | APK 分发 |
| R2 | `calabiyau-user-assets` | `USER_ASSETS` | 用户头像 |
| Pages 密钥 | `ADMIN_PASSWORD` | `env.ADMIN_PASSWORD` | 管理后台密码 |
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

所有响应带 CORS 头；错误统一为 `{"error": "..."}` + 非 200。
头像字段在读取时拼成绝对 URL（`origin + 相对路径`）。

### 档案

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/user/profile?bid=` 或 `?wiki_id=` | 公开 | 单查；未建档返回 `{"profile": null}`，Cache 60s |
| PUT | `/api/user/profile` | Cookie | 更新自己的档案；`avatarUrl` 只接受本服务 `/api/user/avatar/` 路径（相对/绝对均可，落库存相对路径）；替换头像时删旧 R2 对象 |

PUT 请求体：

```json
{ "customName": "...", "avatarUrl": "...", "bio": "...", "badge": "..." }
```

字段校验：昵称 ≤30、签名 ≤200、徽章 ≤20；超长或地址非法返回 400。

### 头像

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/user/avatar` | Cookie | multipart 字段 `file`；≤2MB；PNG/JPEG/WEBP；魔数与声明 MIME 一致性校验 |
| GET | `/api/user/avatar/<key>` | 公开 | 流式读取 R2，Cache 1 天 |

- 返回 `{ "avatarUrl": "/api/user/avatar/avatars/<bid>_<hash>.<ext>", "objectKey": "..." }`
- Key = `avatars/{bid}_{sha256前16位}.{ext}`（内容寻址，同图不重复占空间）
- 服务端声明 `Cache-Control: public, max-age=31536000, immutable`

### 留言板

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/user/comments?bid=&page=&size=` | 公开 | 按时间倒序；带作者头像/昵称（档案 LEFT JOIN，匿名回退昵称/BID）；单页 ≤50 |
| POST | `/api/user/comments` | 可选 | 登录带身份（限流 10s）；未登录匿名（`authorName` 可选 ≤20 字，默认「访客」，按 IP 哈希限流 30s）；内容 ≤200 字 |
| DELETE | `/api/user/comments?id=` | Cookie | 仅作者本人可删；匿名留言只能走管理接口 |

公共留言板 `target_bid = "__public__"`；个人档案留言板 `target_bid = <该用户 bid>`。
发留言与点赞的 `targetBid` 为 `__public__` 之外时，要求 `user_profiles` 中该档案已存在
（防任意字符串刷垃圾板），否则 404。

### 点赞

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/user/likes?bid=` | 公开 | `{count, likedByMe}`；带有效 Cookie 才有 `likedByMe` |
| POST | `/api/user/likes` | Cookie | `{targetBid}`；已赞则取消，未赞则点赞；返回 `{liked, count}` |

## 5. 管理后台（/api/admin/*，/admin 页面）

鉴权：请求头 `X-Admin-Password` 与 `env.ADMIN_PASSWORD` 比较
（两侧 SHA-256 后比对，密钥值做 trim 防御）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/profiles?q=&page=&size=` | 搜索（BID/昵称模糊、WikiID 精确），按 updated_at 倒序 |
| PUT | `/api/admin/profile` | 编辑任意档案（`bid` 必填；头像校验与用户接口一致；清空头像删 R2 对象） |
| DELETE | `/api/admin/profile?bid=` | 删除档案 + R2 头像 + 该用户留言板与点赞 |
| GET | `/api/admin/comments?page=&size=&q=` | 全站留言列表（跨目标，含匿名昵称；按内容/作者/目标搜索） |
| DELETE | `/api/admin/comment?id=` | 删除任意留言 |

页面 `downloadPage/admin.html`（`/admin` 由 Pages pretty-URL 解析）：
密码存 sessionStorage；顶部切换「用户档案 / 留言板管理」两个区块；
档案：列表 / 搜索 / 分页 / 编辑 / 删除 / 头像预览；
留言：列表 / 搜索 / 分页 / 删除；已加 `noindex`。

## 6. 鉴权与限流模型

- **用户写操作**（改档案、传头像、发留言、点赞、删自己的留言）：
  请求头 `X-Wiki-Cookie`（或 `Cookie`）原样转发到
  `MediaWiki action=query&meta=userinfo`，取 `id != 0` 且非 anon 的
  `name`（即 BID）与 `id`（即 wiki_user_id）。
  请求带浏览器 UA / Referer / Origin，避免 EdgeOne WAF 拦截。
- **管理操作**：`X-Admin-Password` 头。
- **限流**：`write_throttle` 表，均为「成功完成后才计数」（失败不占窗口）：
  - 保存档案：登录 10 秒/次（key=`uprof:<bid>`）
  - 上传头像：登录 30 秒/次（key=`uav:<bid>`）
  - 发留言：登录 10 秒/条（key=`bid:<bid>`）、匿名 30 秒/条（key=`ip:<sha256>`）
  - 点赞：toggle 语义不膨胀计数，无限流
- **匿名留言**：不可自行删除（无身份凭证），违规内容由管理后台清理。

## 7. 客户端契约要点

- 字段命名（camelCase）：`bid` / `wikiUserId` / `customName` / `avatarUrl` /
  `bio` / `badge` / `updatedAt`。
- `SharedJson` 配置 `ignoreUnknownKeys = true`，新增字段向后兼容。
- 头像 URL：上传/保存接口返回绝对 URL；D1 只存相对路径；读取接口拼 origin。
- Android 上传管线：选图 → 全屏方形裁切（拖动/双指缩放，
  `AvatarCropDialog.kt`）→ 采样解码（最长边 2048）→ 限制 512px →
  WEBP 质量 85（API 30+ 用 `WEBP_LOSSY`）→ 上传。

## 8. 部署与运维

```powershell
# 部署（构建 + 上传 Pages，Worker 随之生效）
.\gradlew.bat webPush

# D1 迁移（新增迁移文件后）
cd downloadPage
npx wrangler@4.130.0 d1 migrations apply calabiyau-db --remote

# D1 查询 / 备份
npx wrangler@4.130.0 d1 execute calabiyau-db --remote --command "SELECT ..."
npx wrangler@4.130.0 d1 export calabiyau-db --remote --output backup.sql
```

注意：

- `npx wrangler` 可能拉到新版导致 OAuth 授权异常（7403），
  固定 `npx wrangler@4.130.0` 可复现已验证行为。
- Worker 语法可在部署前本地校验：`node -c downloadPage/src/api/_worker.js`。

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
