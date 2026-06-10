# 重设计方案 A+B —— 跟随系统 · 深色为主角（Hybrid）

> 合并 `plan-a-dark-first.md`（深色优先）与 `plan-b-follow-system.md`（跟随系统）。
> 解开两者唯一的冲突点(深色是否为唯一身份):**机制跟随系统,但深色是被重点打磨的「主角」,浅色同样认真调。**
> 结构性布局改造见 `layout-changes.md`(三处:房间卡 / 平台选择器 / 我的页),本文件只定**配色与主题行为**。
> 仍为设计规格,不直接改 app 代码。

---

## 0. 合并后的取舍(冲突点如何解)

| 冲突点 | A 的做法 | B 的做法 | **A+B 最终** |
|---|---|---|---|
| 明暗驱动 | 深色为唯一身份 | 跟随系统 | **跟随系统 + 手动开关**(取 B) |
| 深色底色 | 近黑 `#0D0E10`(影院) | 略亮 `#121316` | **近黑 `#0D0E10`**(取 A,深色是主角) |
| 浅色底色 | `#FCFCFD`(副模式,近纯白) | `#FAFAFB`(非纯白,认真调) | **`#FAFAFB`**(取 B,浅色一等公民) |
| 浅色用心度 | 副模式 | 一等公民 | **一等公民**(取 B) |

一句话:**B 的骨架 + A 的深色灵魂。**

### 决策记录(已拍板 / 已核实)
- **强调色**:保留用户自选 `styleColor`(机制不变),我的 accent 仅作建议默认种子。
- **Material You 动态取色**:移除(`isDynamic` 关闭/隐藏)。
- **手动主题开关**:已存在(`MainActivity.kt:95-99` + `AppearanceSettingsScreen` 的 `themeMode` 0 跟随/1 浅/2 深),**无需新增**。
- **中性灰阶**:固定为本文件 token,不随种子色变化。

---

## 1. 主题行为

- 默认 `isSystemInDarkTheme()` 驱动(保留 `Theme.kt:46`);手动开关 `themeMode` **已存在**,直接复用,无需新增。
- **移除 Material You 动态取色**:`MainActivity.kt:103` 传 `dynamicColor = false`,并在 `AppearanceSettingsScreen` 去掉该开关项。
- **保留 `styleColor` 自选强调色**:它继续喂 `primary`;但**中性灰阶不再由种子派生**,固定为第 2 节 token。
- 删掉 `primaryContainer = seedColor.copy(alpha = …)` 半透明 hack(`Theme.kt:57-65`),改用实色 `elevated`;并把 `onPrimary` 改为按种子亮度取黑/白(修当前任意种子下按钮文字对比不保证的隐患)。
- 深色这套是设计基准、先调;浅色这套同等过对比度,不允许「顺手生成」。

---

## 2. 配色 token（OKLCH 为准，hex 为约值需微调）

### 深色（主角，近黑影院外壳）

| token | OKLCH | hex(精确) | 用途 |
|---|---|---|---|
| `bg` | `oklch(0.16 0.004 250)` | `#0C0D0F` | 根背景、网格底、裸卡底 |
| `surface` | `oklch(0.21 0.005 250)` | `#17181B` | 输入框、sheet、抬升块 |
| `elevated` | `oklch(0.26 0.006 250)` | `#222427` | 底栏、悬浮菜单、对话框 |
| `hairline` | `白 8%` | `rgba(255,255,255,.08)` | **仅**分隔处 1px 线 |
| `ink` | `oklch(0.96 0.003 250)` | `#F0F2F4` | 主文字 |
| `muted` | `oklch(0.72 0.008 250)` | `#A1A5A9` | 次文字、图标 |
| `accent` | `oklch(0.70 0.12 245)` | `#58A5E4` | 选中/状态;**=用户 styleColor,此为建议默认** |
| `accent-strong` | `oklch(0.56 0.13 245)` | `#1C7ABB` | 主操作填充(配白字);与 primary 同源 |

### 浅色（一等公民，非纯白，chroma≈0）

| token | OKLCH | hex(精确) | 用途 |
|---|---|---|---|
| `bg` | `oklch(0.985 0 0)` | `#FAFAFA` | 根背景(非纯白,让白色封面能分离) |
| `surface` | `oklch(0.96 0 0)` | `#F2F2F2` | 输入框、sheet、抬升块 |
| `hairline` | `黑 10%` | `rgba(0,0,0,.10)` | 仅分隔处 |
| `ink` | `oklch(0.23 0.004 250)` | `#1B1D1F` | 主文字 |
| `muted` | `oklch(0.50 0.008 250)` | `#606468` | 次文字、图标 |
| `accent` | `oklch(0.55 0.13 245)` | `#1777B8` | 选中/主操作;**=用户 styleColor,此为建议默认** |

### 平台色（保留原值，不动）
斗鱼 `#FF5D23` · 虎牙 `#FFD736` · 哔哩哔哩 `#F07775`（`PlatformColors.kt`）。只作小块**纯色**标记,禁止渐变。

### 对比度校验(两套都过)
- 深色:`ink`/`bg` ≈ 16:1 ✓;`muted`/`bg` ≈ 6:1 ✓;按钮 `accent-strong`(L0.56)+白字 ≈ 4.6:1 ✓(**勿**用浅 `accent` L0.70 配白字)。
- 浅色:`ink`/`bg` ≈ 14:1 ✓;`muted`/`bg` ≈ 5.2:1 ✓;按钮 `accent`(L0.55)+白字 ≈ 4.6:1 ✓。
- 聊天/弹幕走 `ink`,发送者名 `muted` 或单一 `accent`,禁止彩虹名。

### 文件映射
- `ui/theme/Color.kt`:深色、浅色两组 `md_theme_*` 都按上表替换,深色先调、浅色同等用心。
- `ui/theme/Theme.kt`:保留系统驱动 + 接入手动开关,去半透明 hack。
- `ui/theme/PlatformColors.kt`:不动。

---

## 3. 结构性改造(布局)

与 A/B 完全一致,详见 **`layout-changes.md`**:
1. **LiveRoomCard**:去卡片外壳;标题占满宽可 2 行;18–20dp 小头像(无环)收次行;平台徽章纯色。
2. **首页平台选择器**:等宽药丸 → 可滚动 tab 行 + 平台色下划线。
3. **我的页**:移除 profile 英雄卡 + 5 张卡 → 分组列表。
4. 空状态 emoji → 图标;Super Chat 纯色块(非 side-stripe)。

房间页竖/横屏结构、底栏、骨架屏、网格列数、导航路由:**不动**。

---

## 4. 动效
150–250ms、ease-out、只表达状态。保留底栏滚动隐藏、Cupertino 转场;删首页 wordmark 随平台变色动画。

---

## 5. 落地顺序(建议)
1. `Color.kt` + `Theme.kt`:铺两套 token(深色先)、接手动开关、去半透明 hack。
2. `AppearanceSettingsScreen.kt`:加「主题模式:跟随系统/浅色/深色」。
3. `LiveRoomCard.kt`:去卡片化 + 信息块重排(视觉变化最大)。
4. `HomeScreen.kt`:固定 wordmark 色 + 平台 tab 选择器。
5. `MineScreen.kt`:分组列表替换卡片堆。
6. `EmptyState.kt` + Super Chat:图标 / 纯色块收尾。

---

## 6. 执行细节（turn-key 补全）

### 6.1 token → Material3 角色映射（核心：低改动落地）
全 app 约 **392 处**在读 `MaterialTheme.colorScheme.*`(44 个文件)。**不另起 token 层**,直接在 `Color.kt`/`Theme.kt` 把 M3 角色重映射到固定中性 token,绝大多数调用点零改动:

| M3 角色（代码在用） | → token | 备注 |
|---|---|---|
| `background` | `bg` | |
| `surface` | `surface` | |
| `surfaceVariant` | `elevated` | 见 6.2「重载」 |
| `onBackground` / `onSurface` | `ink` | 主文字(40 处) |
| `onSurfaceVariant` | `muted` | 次文字/图标(91 处) |
| `outline` / `outlineVariant` | `hairline` 同值 | 描边自动降为发丝级(49 处) |
| `primary` | **用户 styleColor** | 机制不变,accent 仅作默认种子(86 处) |
| `onPrimary` | 按种子亮度取黑/白 | 修对比隐患 |
| `primaryContainer` | `elevated`(去 alpha hack) | |
| `error` / `onError` | 保留语义色 | |

### 6.2 surfaceVariant「重载」问题（必须处理）
`surfaceVariant` 被两种用途共用,一个映射值满足不了:
- **幽灵卡填充**(要去掉):如 `surfaceVariant.copy(alpha=.25)` 当卡底。
- **正经面板**(要保留):如底栏 `surfaceVariant.copy(alpha=.9)`(`IndexScreen.kt:143`)。

解法:`surfaceVariant → elevated`(让面板可见),**卡片填充的调用点显式去掉**(本就在重设计范围内)。逐文件动作见 6.3。

### 6.3 配色迁移清单（幽灵卡配方实测 15 个文件）
| 文件 | 动作 |
|---|---|
| `component/LiveRoomCard.kt` | 去卡壳/填充/描边(结构改造,已在范围) |
| `screen/mine/MineScreen.kt` | 卡 → 分组列表(已在范围) |
| `screen/home/HomeScreen.kt` | 搜索框/平台 chip 去描边填充(已在范围) |
| `screen/IndexScreen.kt` | 底栏**保留**面板填充(→ elevated) |
| `component/status/SkeletonScreen.kt` | 骨架形状对齐新扁平卡 |
| `screen/category/CategoryScreen.kt` | 逐项审:卡 → 扁平/列表 |
| `screen/follow/FollowScreen.kt` | 同上 |
| `screen/search/SearchScreen.kt` | 同上 |
| `screen/other/HistoryScreen.kt` | 同上 |
| `screen/mine/ParseScreen.kt` | 同上 |
| `screen/settings/AccountScreen.kt` | 行 → 列表 / 保留面板 |
| `screen/settings/SettingsScreen.kt` | 同上 |
| `screen/sync/SyncHubScreen.kt` | 同上 |
| `screen/room/quickaccess/QuickAccessPanel.kt` | 面板**保留**(→ elevated) |
| `screen/room/LiveRoomScreen.kt` | 去装饰描边;Super Chat 纯色块 |

「逐项审」6 屏每个约 5 分钟,确认每处填充是「卡」(去)还是「面板」(留),实现时一并处理。

### 6.4 还需在实现时定的零碎
- LiveRoomCard 精确值:封面圆角 12dp、封面↔标题间距 8dp、标题 15sp/SemiBold/maxLines 2、头像 18–20dp、平台 chip 内边距。
- 我的页各行图标 + 空状态图标:从 `ui/theme/Icons.kt` 选(需先确认该集合是否含 history/account/sync/tools/settings 对应图标,缺则补)。
- 首页平台 tab 行:复用现有 `pagerState` 同步 + `AppMotion.preJumpPageForTarget` 预跳逻辑,只换外观(下划线指示),**不动翻页行为**。

### 6.5 完成度评估
- **配色 / 主题:可定稿** —— 精确 hex + M3 映射 + 决策齐全,动态取色已剔除,自选色保留。
- **三处布局:可执行** —— 前后对比 + 范围明确,零碎尺寸实现时定。
- **全 app 迁移:清单齐全** —— 15 个文件,其中 6 屏需逐项 5 分钟审。

结论:**配色与主题已是 turn-key;布局与全 app 迁移是「清单齐全、边写边定零碎值」的可执行状态。**
