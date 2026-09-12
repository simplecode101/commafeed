# Google Reader API

CommaFeed 实现了一套 Google Reader 兼容 API，供原生客户端使用。与 `/rest/*` 下的常规 REST API 不同，**它使用 api key 作为凭据，并允许用 POST 执行写操作**（把条目标记为已读/未读、加星、订阅等），因此很适合脚本和 agent 调用。

## 基础信息

| 项 | 值 |
| --- | --- |
| 路径前缀 | `/rest/googlereader` |
| 本地部署示例 | `http://192.168.1.138:8082/rest/googlereader` |
| 鉴权方式 | 请求头 `Authorization: GoogleLogin auth=<用户名>/<apiKey>` |
| 写操作 | `application/x-www-form-urlencoded` |
| 成功响应 | 写操作为纯文本 `OK` |
| OpenAPI 文档 | **不包含**这些端点（源码中标记为 `@Operation(hidden = true)`） |

> 常规 REST 接口的 Swagger UI 在 `/api-documentation`，OpenAPI JSON 在 `/openapi`；本 API 不在其中。

## 一、认证

### 方式 A：直接构造请求头（推荐，无需额外请求）

```
Authorization: GoogleLogin auth=<用户名>/<apiKey>
```

例如：

```
Authorization: GoogleLogin auth=admin/ab6a99adb46d9d9150625e79372358b7bae86913
```

apiKey 可在 CommaFeed 的「设置 - 个人资料」中查看或重置。请求头中最后一个 `/` 之后的部分会被当作 apiKey，因此 `auth=<apiKey>` 单独传也可用。

### 方式 B：ClientLogin

用 api key 换取 token（该端点免鉴权）：

```bash
curl -X POST 'http://192.168.1.138:8082/rest/googlereader/accounts/ClientLogin' \
  -d 'Passwd=<apiKey>'
```

成功返回（`Auth` 即为后续请求头所需的值）：

```
SID=<用户名>/<apiKey>
LSID=<用户名>/<apiKey>
Auth=<用户名>/<apiKey>
```

失败返回 `403`，响应体为 `Error=BadAuthentication`。

### Edit Token（`T` 参数）

Google Reader 协议中 POST 写操作需要携带 `T` token 以防 CSRF：

```bash
curl -H 'Authorization: GoogleLogin auth=<用户名>/<apiKey>' \
  'http://192.168.1.138:8082/rest/googlereader/reader/api/0/token'
```

值为 `md5(apiKey + ":" + userId)`。

**但 CommaFeed 接受留空的 `T`**：本 API 使用请求头鉴权而非 Cookie，不存在 CSRF 风险，因此 `T` 参数可以完全省略。多数客户端（如 RSSGuard）从不请求 token。

## 二、标识符格式

### Stream ID（流，即列表/分类）

| 形式 | 含义 |
| --- | --- |
| 留空、`reading-list`、`user/-/state/com.google/reading-list` | 全部条目 |
| `user/-/state/com.google/starred` | 星标条目 |
| `user/-/label/<分类名>` | 某个分类 |
| `feed/<subscriptionId>` | 单个订阅（`subscriptionId` 由订阅列表接口返回） |

无法识别的值会回退为「全部条目」。分类名（`label`）直接拼在 stream id 中，包含中文、空格或 `/` 等字符时需要 **URL 编码**。

### Item ID（条目）

- **输出格式**（`stream/contents` 返回）：`tag:google.com,2005:reader/item/00000000148b9369`
  后缀是条目数字 id 的 16 位零填充十六进制。
- **输入格式**（`edit-tag`、`items/contents` 的 `i` 参数）：上述格式，或**纯十进制数字**均可，两者都会被正确解析。
- 注意 `stream/items/ids` 返回的是**十进制**字符串，可原样回传给 `edit-tag`。

## 三、状态标签

用于 `edit-tag` 的 `a`（添加）和 `r`（移除）参数：

| 标签 | 含义 |
| --- | --- |
| `user/-/state/com.google/read` | 已读 |
| `user/-/state/com.google/starred` | 星标 |

## 四、端点

所有路径均以 `/rest/googlereader` 为前缀，且除 `ClientLogin` 外都需要认证头。

### 1. 用户信息

```
GET /reader/api/0/user-info
```

```json
{
  "userId": "2",
  "userName": "admin",
  "userProfileId": "2",
  "userEmail": "admin@commafeed.com"
}
```

### 2. 订阅列表

```
GET /reader/api/0/subscription/list
```

```json
{
  "subscriptions": [
    {
      "id": "feed/12",
      "title": "Hacker News",
      "categories": [{ "id": "user/-/label/tech", "label": "tech" }],
      "url": "https://news.ycombinator.com/rss",
      "htmlUrl": "https://news.ycombinator.com",
      "iconUrl": "rest/feed/favicon/12"
    }
  ]
}
```

注意 `iconUrl` 是**相对路径**，需要自行拼到站点根地址上。

### 3. 订阅管理

```
POST /reader/api/0/subscription/edit
```

| 参数 | 说明 |
| --- | --- |
| `ac` | `subscribe` / `unsubscribe` / `edit` |
| `s` | streamId，订阅时可用 `feed/<rss地址>` 或直接填 RSS 地址 |
| `t` | 标题（可选） |
| `a` | 添加分类标签（可选） |
| `r` | 移除分类标签（可选） |
| `T` | edit token（可省略） |

### 4. 标签列表

```
GET /reader/api/0/tag/list
```

```json
{ "tags": [{ "id": "user/-/label/tech" }, { "id": "user/-/state/com.google/starred" }] }
```

### 5. 未读数

```
GET /reader/api/0/unread-count
```

```json
{
  "max": 10000,
  "unreadcounts": [
    { "id": "feed/12", "count": 3, "newestItemTimestampUsec": "1757000000000000" },
    { "id": "user/-/label/tech", "count": 5 },
    { "id": "user/-/state/com.google/reading-list", "count": 42 }
  ]
}
```

`max` 是「超过该值客户端应显示 `count+`」的阈值。

### 6. 条目列表

```
GET /reader/api/0/stream/contents/{streamId}
GET /reader/api/0/stream/contents?s={streamId}
```

| 参数 | 说明 |
| --- | --- |
| `s` / 路径 | streamId（见上文） |
| `n` | 返回条数，默认 `20`，最大 `1000` |
| `c` | 分页游标，使用上次响应中的 `continuation` |
| `r` | `o` 表示按时间升序（最旧优先），默认降序（最新优先） |
| `xt` | 排除目标；传 `user/-/state/com.google/read` 时只返回未读条目 |

```json
{
  "id": "user/-/state/com.google/reading-list",
  "title": "reading list",
  "updated": 1757000000,
  "items": [
    {
      "id": "tag:google.com,2005:reader/item/00000000148b9369",
      "crawlTimeMsec": "1757000000000",
      "timestampUsec": "1756999000000000",
      "published": 1756999000,
      "updated": 1756999000,
      "title": "...",
      "author": "...",
      "categories": ["user/-/state/com.google/reading-list", "user/-/state/com.google/read"],
      "canonical": [{ "href": "https://..." }],
      "alternate": [{ "href": "https://...", "type": "text/html" }],
      "summary": { "content": "<p>...</p>", "direction": "ltr" },
      "origin": { "streamId": "feed/12", "title": "Hacker News", "htmlUrl": "https://..." }
    }
  ],
  "continuation": "21982"
}
```

`continuation` 存在时，把它作为下一次请求的 `c` 参数即可翻页。它是**十进制**的条目 id（即本页最后一条），而不是 `items[].id` 那种 `tag:` 格式；星标流例外，使用偏移量。

### 7. 条目 ID 列表（轻量）

```
GET /reader/api/0/stream/items/ids
```

参数与 `stream/contents` 相同。返回**十进制** id：

```json
{ "itemRefs": [{ "id": "20222" }], "continuation": "20221" }
```

### 8. 按 ID 批量取条目

```
POST /reader/api/0/stream/items/contents   # form 参数 i
GET  /reader/api/0/stream/items/contents?i=..&i=..
```

`i` 可重复多次。返回结构同 `stream/contents`。

### 9. 标记条目（核心写接口）

```
POST /reader/api/0/edit-tag
Content-Type: application/x-www-form-urlencoded
```

| 参数 | 说明 |
| --- | --- |
| `i` | 条目 id，可重复传多个 |
| `a` | 要添加的标签，可重复 |
| `r` | 要移除的标签，可重复 |
| `T` | edit token（可省略） |

标记为已读：

```bash
curl -X POST 'http://192.168.1.138:8082/rest/googlereader/reader/api/0/edit-tag' \
  -H 'Authorization: GoogleLogin auth=admin/<apiKey>' \
  -d 'i=20222' \
  -d 'a=user/-/state/com.google/read'
```

标记为未读并取消星标：

```bash
curl -X POST 'http://192.168.1.138:8082/rest/googlereader/reader/api/0/edit-tag' \
  -H 'Authorization: GoogleLogin auth=admin/<apiKey>' \
  -d 'i=20222' -d 'i=20223' \
  -d 'r=user/-/state/com.google/read' \
  -d 'r=user/-/state/com.google/starred'
```

成功返回纯文本 `OK`。条目不属于当前用户时会被静默忽略（仍返回 `OK`）。

### 10. 全部标记为已读

```
POST /reader/api/0/mark-all-as-read
```

| 参数 | 说明 |
| --- | --- |
| `s` | streamId，决定作用范围（全部 / 某分类 / 某订阅 / 星标） |
| `ts` | 只标记该时间之前的条目，单位微秒（可选） |
| `T` | edit token（可省略） |

```bash
curl -X POST 'http://192.168.1.138:8082/rest/googlereader/reader/api/0/mark-all-as-read' \
  -H 'Authorization: GoogleLogin auth=admin/<apiKey>' \
  -d 's=user/-/state/com.google/reading-list'
```

## 五、错误处理

| 场景 | 响应 |
| --- | --- |
| 缺少或错误的认证头 | `401` |
| `T` token 不匹配 | `401` |
| ClientLogin 凭据错误 | `403`，响应体 `Error=BadAuthentication` |
| 缺少必要参数（如订阅管理） | `400` |
| 写操作成功 | `200`，响应体 `OK` |

## 六、脚本 / agent 调用示例

完整流程（取未读列表 → 批量标记已读）：

```bash
BASE='http://192.168.1.138:8082/rest/googlereader'
AUTH='Authorization: GoogleLogin auth=admin/<apiKey>'

# 1. 取最新 50 条未读
IDS=$(curl -s -H "$AUTH" \
  "$BASE/reader/api/0/stream/items/ids?s=user/-/state/com.google/reading-list&n=50&xt=user/-/state/com.google/read" \
  | grep -oE '"id":"[0-9]+"' | grep -oE '[0-9]+')

# 2. 批量标记已读（每个 id 一个 i 参数）
ARGS=()
for id in $IDS; do ARGS+=(-d "i=$id"); done
curl -s -X POST "$BASE/reader/api/0/edit-tag" -H "$AUTH" \
  "${ARGS[@]}" -d 'a=user/-/state/com.google/read'
```

TypeScript：

```ts
const BASE = "http://192.168.1.138:8082/rest/googlereader"

const headers = {
    Authorization: `GoogleLogin auth=${username}/${apiKey}`,
    "Content-Type": "application/x-www-form-urlencoded",
}

// 取未读条目 id
const res = await fetch(
    `${BASE}/reader/api/0/stream/items/ids?s=user/-/state/com.google/reading-list&n=100&xt=user/-/state/com.google/read`,
    { headers },
)
const { itemRefs } = await res.json()

// 批量标记已读
const body = new URLSearchParams()
for (const { id } of itemRefs) body.append("i", id)
body.append("a", "user/-/state/com.google/read")
await fetch(`${BASE}/reader/api/0/edit-tag`, { method: "POST", headers, body })
```

## 七、实现说明

- 端点实现在 `commafeed-server/src/main/java/com/commafeed/frontend/resource/googlereader/`
- 鉴权机制：`commafeed-server/src/main/java/com/commafeed/security/mechanism/GoogleReaderAuthenticationMechanism.java`，**只对 `/rest/googlereader` 前缀生效**
- 每条已读/星标操作最终调用 `FeedEntryService`，与前端界面使用的是同一套业务逻辑，因此无需担心数据不一致
- `?apiKey=` 查询参数的常规 REST API **只允许 GET**；需要 POST 写操作时请使用本 API
