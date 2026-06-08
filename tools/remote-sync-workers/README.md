# My Live Remote Sync - Cloudflare Worker

多设备远程同步服务，部署在 Cloudflare Workers 上。

## 部署步骤

```bash
# 1. 安装依赖
npm install

# 2. 登录 Cloudflare
npx wrangler login

# 3. 部署
npm run deploy
```

部署成功后会输出 Worker URL，格式类似：
```
https://mylive-sync.<你的子域>.workers.dev
```

## 本地调试

```bash
npm run dev
```

本地运行在 `ws://127.0.0.1:8787/sync`

## 同步的数据类型

| 类型 | 说明 |
|------|------|
| `sendFavorite` | 收藏列表 |
| `sendHistory` | 观看历史 |
| `sendShieldWord` | 弹幕屏蔽词 |
| `sendBiliAccount` | B站账号 |
| `sendDouyinAccount` | 抖音账号 |

## 配置

Android 客户端默认同步服务地址：

`app/src/main/java/com/mylive/app/service/RemoteSyncService.kt`：

```kotlin
const val K_DEFAULT_URL = "wss://mylive.conef.hidns.co/sync"
```
