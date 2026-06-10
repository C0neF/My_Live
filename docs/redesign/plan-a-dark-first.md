# 重设计方案 A —— 深色优先（Dark-first）

> 把「深色」定为 MyLive 的品牌身份，浅色作为干净的副模式。
> 适用判断：用户多在傍晚 / 弱光、手机 + TV 盒子场景浏览直播，画面（封面、播放器）是主角。
> 主旨：**让位给画面**——屏幕上唯一饱和的东西是封面图和平台品牌色，外壳收进一条克制的中性灰阶。

本文件是设计规格（spec），不直接改 app 代码。落地时按「文件映射」逐处替换。

---

## 1. 配色系统（OKLCH 为准，hex 为约值需微调）

### 深色（主身份）

| token | OKLCH | ≈hex | 用途 |
|---|---|---|---|
| `bg` | `oklch(0.16 0.004 250)` | `#0D0E10` | 根背景、网格底、裸卡底 |
| `surface` | `oklch(0.21 0.005 250)` | `#16181B` | 输入框、sheet、抬升块 |
| `elevated` | `oklch(0.26 0.006 250)` | `#1E2024` | 底栏、悬浮菜单、对话框 |
| `hairline` | `白 8%` | `rgba(255,255,255,.08)` | **仅**在需要分隔处的 1px 线 |
| `ink` | `oklch(0.96 0.003 250)` | `#F1F2F4` | 主文字（标题、正文） |
| `muted` | `oklch(0.72 0.008 250)` | `#A4A9B0` | 次文字、图标 |
| `accent` | `oklch(0.70 0.12 245)` | `#4C92E6` | 选中态、状态指示 |
| `accent-strong` | `oklch(0.56 0.13 245)` | `#2A6FCB` | 主操作按钮**填充**（配白字） |

### 浅色（副模式，真中性，chroma≈0）

| token | OKLCH | ≈hex |
|---|---|---|
| `bg` | `oklch(0.99 0 0)` | `#FCFCFD` |
| `surface` | `oklch(0.97 0 0)` | `#F4F5F6` |
| `hairline` | `黑 8%` | `rgba(0,0,0,.08)` |
| `ink` | `oklch(0.22 0.004 250)` | `#16181B` |
| `muted` | `oklch(0.52 0.008 250)` | `#5C6168` |
| `accent` | `oklch(0.55 0.13 245)` | `#2A6FCB` |

### 平台色（保留原值，身份资产，不动）

斗鱼 `#FF5D23` · 虎牙 `#FFD736` · 哔哩哔哩 `#F07775`（见 `PlatformColors.kt`）。
**只作小块纯色标记使用，禁止再做渐变。**

### 对比度校验
- `ink` on `bg` ≈ 16:1 ✓；`muted` on `bg` ≈ 6:1 ✓（满足正文 4.5:1）。
- 主按钮：用 `accent-strong`（L≈0.56）打底 + 白字 ≈ 4.6:1 ✓。**不要**用浅 `accent`（L0.70）打底配白字（只 ~2.3:1，不合格）。
- 弹幕 / 聊天文字一律走 `ink`，发送者名走 `muted` 或单一 `accent`，禁止彩虹色名。

### 文件映射
- `ui/theme/Color.kt`：用上表替换全部 `md_theme_*` 常量；深色优先意味着深色这套是「主」，先调它。
- `ui/theme/Theme.kt`：
  - 默认身份走深色。`isSystemInDarkTheme()` 可保留为开关，但**深色是设计基准**，浅色按副模式对齐。
  - 删掉 `primaryContainer = seedColor.copy(alpha = …)` 这套半透明 hack（`Theme.kt:57-65`），它会生成发灰发脏的容器；改用上表里实色的 `surface / elevated`。

---

## 2. 结构性改造（A / B 两方案共享，布局前后对比见 `layout-changes.md`）

### 2.1 LiveRoomCard —— 收益最高的一处（`component/LiveRoomCard.kt` 整文件）
- **去掉 `Card` 容器、去掉 1px 描边、去掉 `surfaceVariant` 填色。** 封面图本身就是卡片，间距 + 圆角即边界。
- 结构：圆角封面（`12dp`）→ `8dp` 间距 → 裸 `bg` 上的两行信息。
- 封面叠层：保留底部压暗渐变（功能性，OK）。人气 + LIVE 标压在封面上。
- 平台徽章：改为平台品牌色的**纯色**小 chip（去掉 `horizontalGradient`，`LiveRoomCard.kt:98-106`）。
- **删掉头像那圈「premium border」**（`LiveRoomCard.kt:157`）。信息块改 YouTube 式:标题升为主行、占满宽、可 2 行（`ink`·SemiBold）;头像缩到 18–20dp（无环）移到次行与主播名（`muted`）同排。标题拿全宽,头像不再抢空间。
- 层级：标题 = 最强文字（`titleSmall`/15sp Medium，`ink`）；主播名 `bodySmall`/`muted`。

### 2.2 首页（`screen/home/HomeScreen.kt`）
- **wordmark 固定为 `ink` 色，停止随平台变色动画**（删 `HomeScreen.kt:70-77` 的 `titleColor` 逻辑——那是装饰）。
- 搜索：从描边幽灵药丸（`HomeScreen.kt:119-127`）改成安静的 `surface` 实底输入区，无边框。
- 平台选择器（`HomeScreen.kt:184-221`）：现在是「四个等宽 `weight(1f)` chip」，本质是伪装分段控件。改成**真正的可滚动 tab 行 / 分段控件**，选中平台用该平台品牌色做底色或 2dp 下划线。

### 2.3 我的 / 设置 —— 去卡片化（`screen/mine/MineScreen.kt`）
- **干掉 5 张堆叠描边卡**（`MineScreen.kt:104-127`）。换成**分组列表**：行内嵌图标 + 标题 + chevron，同组间用 `hairline` 分隔。复用已有的 `SettingsMenu` 行样式（`component/settings/SettingsMenu.kt`）。
- profile 英雄卡（`MineScreen.kt:53-100`）：这是聚合器、无真账户。建议整块删掉直接进列表，或缩成一行。**删掉「极致、纯粹的直播体验」营销文案**。若保留卡，圆角 `24dp → 12dp`（`MineScreen.kt:60`）。

### 2.4 空 / 错误状态
- `component/status/EmptyState.kt:26-28`：把 emoji `📭` 换成图标集里的真图标（`ui/theme/Icons.kt`），统一图标语言。
- 文案后补一个具体动作（如「换个平台试试」），错误态保留重试按钮（`ErrorState.kt` 已有）。

### 2.5 房间页（`screen/room/LiveRoomScreen.kt`，核心体验，布局不动）
- 只磨可读性：聊天文字 ≥4.5:1（走 `ink`）；Super Chat 卡用平台/身份色做**左对齐纯色块**，**禁止做成左侧 side-stripe 色条边框**（`SuperChatCard`，约 `:1937`）。
- 控件自动隐藏保持干净；横屏叠层聊天底用 `bg` 半透明压暗保证字可读。

### 2.6 动效（遵守「安静、直接、实用」）
- 过渡 150–250ms、ease-out、只表达状态。
- 保留底栏滚动隐藏（`IndexScreen.kt:124-132`）和 Cupertino 转场（状态性，保留）。
- 删掉首页 wordmark 随平台变色动画（装饰）。

---

## 3. A 方案的独有取舍

| 维度 | A：深色优先 |
|---|---|
| 身份 | 深色是品牌主身份，浅色是对齐过的副模式 |
| 底色 | 近黑「影院外壳」`#0D0E10`，让封面最跳 |
| 卡片分隔 | 深底上裸卡**无需任何边框/填色**，靠间距即可 |
| 氛围 | 沉浸、内容前置，贴近 Netflix / Twitch 夜间观感 |
| 风险 | 浅色模式要单独验一遍对比度，别让它沦为「顺手生成」 |

---

## 4. 落地顺序（建议）
1. `Color.kt` + `Theme.kt`：铺深色 token，去掉半透明容器 hack。
2. `LiveRoomCard.kt`：去卡片化（视觉变化最大、最快见效）。
3. `HomeScreen.kt`：固定 wordmark 色 + 平台 tab 选择器。
4. `MineScreen.kt`：分组列表替换卡片堆。
5. `EmptyState.kt` + Super Chat：图标 / 纯色块收尾。
