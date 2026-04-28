# koukou：QQ 简易功能 Android App 开发设计文档

本文档依据当前仓库实际代码结构整理，用于说明 `koukou` 项目的模块划分、页面职责、数据流向与主题系统设计，方便后续维护、联调与继续扩展。

## 1. 项目定位

- 项目名称：`koukou`
- 包名：`com.example.koukou`
- 应用类型：本地可运行的 QQ 风格轻量即时通讯应用
- 主要语言：Java
- UI 技术：XML + ViewBinding
- 架构模式：MVVM + Repository + Room + WebSocket + DataStore

当前版本已覆盖以下主线能力：

- 账号注册、扣扣号登录、历史账号快捷切换
- 联系人管理、好友资料页、删除好友
- 会话列表、消息收发、多账号本地隔离
- 完整设置中心与二级设置页
- 多主题背景系统与沉浸动效
- 应用内更新日志展示

## 2. 构建与技术栈

### 2.1 Android 构建配置

- AGP：`9.0.1`
- Gradle Wrapper：`9.2.1`
- compileSdk：`36`
- minSdk：`24`
- targetSdk：`36`
- Java：`17`
- ViewBinding：开启
- BuildConfig：开启
- 当前版本：
  - `versionCode = 2`
  - `versionName = 1.1.0`

### 2.2 核心依赖

- AndroidX：`appcompat`、`activity`、`constraintlayout`、`recyclerview`、`swiperefreshlayout`
- Material Components：`1.12.0`
- Lifecycle：`ViewModel / LiveData / Runtime`
- Room：本地数据库
- OkHttp：WebSocket 实时消息
- Gson：JSON 解析
- Glide：头像与图片加载
- uCrop：头像裁剪
- DataStore Preferences RxJava3：设置项持久化
- RxJava3：DataStore 响应式读写
- DynamicAnimation：底部导航等物理动效扩展
- Permissions Dispatcher：权限处理

## 3. 当前工程结构

```text
app/src/main/java/com/example/koukou/
├─ MainActivity.java
├─ MainPagerAdapter.java
├─ data/
│  ├─ local/
│  │  ├─ AppDatabase.java
│  │  ├─ dao/
│  │  │  ├─ ConversationDao.java
│  │  │  ├─ FriendDao.java
│  │  │  ├─ MessageDao.java
│  │  │  └─ UserDao.java
│  │  └─ entity/
│  │     ├─ ConversationEntity.java
│  │     ├─ FriendEntity.java
│  │     ├─ MessageEntity.java
│  │     └─ UserEntity.java
│  └─ repository/
│     ├─ ContactRepository.java
│     ├─ ConversationRepository.java
│     ├─ MessageRepository.java
│     ├─ SettingsRepository.java
│     └─ UserRepository.java
├─ network/
│  ├─ model/
│  │  └─ WebSocketMessage.java
│  └─ websocket/
│     ├─ AppWebSocketListener.java
│     └─ WebSocketManager.java
├─ ui/
│  ├─ chat/
│  │  ├─ ChatActivity.java
│  │  ├─ ChatViewModel.java
│  │  ├─ ChatViewModelFactory.java
│  │  ├─ EmojiAdapter.java
│  │  └─ MessageAdapter.java
│  ├─ contacts/
│  │  ├─ ContactAdapter.java
│  │  ├─ ContactsFragment.java
│  │  ├─ ContactsViewModel.java
│  │  └─ FriendProfileActivity.java
│  ├─ conversations/
│  │  ├─ ConversationAdapter.java
│  │  ├─ ConversationsFragment.java
│  │  └─ ConversationsViewModel.java
│  ├─ login/
│  │  ├─ HistoryAdapter.java
│  │  ├─ LoginActivity.java
│  │  ├─ LoginViewModel.java
│  │  ├─ LoginViewModelFactory.java
│  │  └─ RegisterActivity.java
│  ├─ settings/
│  │  ├─ SettingsAdapter.java
│  │  ├─ SettingsDetailActivity.java
│  │  ├─ SettingsFragment.java
│  │  ├─ SettingsViewModel.java
│  │  ├─ SettingsViewModelFactory.java
│  │  ├─ VersionInfoActivity.java
│  │  └─ model/
│  │     ├─ SettingsItem.java
│  │     ├─ SettingsPage.java
│  │     ├─ SettingsState.java
│  │     └─ SettingsStorageStats.java
│  └─ shared/
│     └─ MainViewModelFactory.java
├─ utils/
│  ├─ AppearanceManager.java
│  ├─ AppExecutors.java
│  ├─ AvatarHelper.java
│  ├─ IridescenceAnimator.java
│  └─ UserHelper.java
└─ widget/
   ├─ CodeRainView.java
   └─ ThemeAtmosphereView.java
```

## 4. 页面入口与导航结构

### 4.1 启动链路

- 启动页：`ui.login.LoginActivity`
- 注册页：`ui.login.RegisterActivity`
- 登录成功后进入：`MainActivity`

### 4.2 主容器页面

`MainActivity` 使用：

- `ViewPager2`
- `BottomNavigationView`
- `MainPagerAdapter`

承载三大核心一级分页：

- 消息：`ConversationsFragment`
- 联系人：`ContactsFragment`
- 我的：`SettingsFragment`

### 4.3 已注册 Activity

当前 `AndroidManifest.xml` 中已注册：

- `MainActivity`
- `ui.login.LoginActivity`
- `ui.login.RegisterActivity`
- `ui.chat.ChatActivity`
- `ui.contacts.FriendProfileActivity`
- `ui.settings.VersionInfoActivity`
- `ui.settings.SettingsDetailActivity`
- `com.yalantis.ucrop.UCropActivity`

## 5. 架构分层说明

### 5.1 View 层

包含：

- `Activity`
- `Fragment`
- `Adapter`
- XML 布局与 drawable 资源

职责：

- 用户交互入口
- 绑定 ViewModel 状态
- 主题与动效表现承载

### 5.2 ViewModel 层

当前主要 ViewModel：

- `LoginViewModel`
- `ConversationsViewModel`
- `ContactsViewModel`
- `ChatViewModel`
- `SettingsViewModel`

职责：

- 组织页面状态
- 调用 Repository
- 提供 `LiveData`
- 将复杂配置转换为可直接渲染的列表项模型

### 5.3 Repository 层

当前 Repository：

- `UserRepository`
- `ContactRepository`
- `ConversationRepository`
- `MessageRepository`
- `SettingsRepository`

职责：

- 聚合本地数据、用户状态、WebSocket 与 DataStore
- 屏蔽线程切换与底层实现
- 提供统一业务调用入口

## 6. 数据层设计

### 6.1 Room 数据库

数据库入口：

- `AppDatabase`

当前实体：

- `UserEntity`：用户账号、密码、昵称、头像、签名
- `FriendEntity`：好友关系
- `ConversationEntity`：会话摘要
- `MessageEntity`：消息明细

当前 DAO：

- `UserDao`
- `FriendDao`
- `ConversationDao`
- `MessageDao`

当前数据库版本：

- `version = 3`

迁移策略：

- `fallbackToDestructiveMigration`

说明：目前适合开发阶段快速迭代，若进入长期可维护阶段，建议逐步补齐显式 Migration。

### 6.2 DataStore 设置持久化

`SettingsRepository` 已使用 `Jetpack Preferences DataStore` 统一管理纯键值配置，主要包括：

- 通知总开关
- 通知声音
- 振动反馈
- 消息预览
- 免打扰
- 本地密码保护
- 允许通过扣扣号找到我
- 好友验证方式
- 主题模式
- 聊天背景主题
- 字体大小
- 沉浸流光动效开关
- 黑名单列表
- 本地资料快照

## 7. 账号体系与登录流程

### 7.1 注册规则

当前注册页逻辑：

- 用户创建的是昵称，不是登录账号名
- 系统会生成唯一 10 位扣扣号
- 也支持用户手动输入 10 位扣扣号
- 注册阶段会做唯一性校验

### 7.2 登录规则

- 登录统一使用：`扣扣号 + 密码`
- 添加好友查找统一使用扣扣号
- 昵称仅作为展示资料，不作为登录主键

### 7.3 历史账号能力

登录成功或注册成功后会自动写入历史账号列表，记录：

- 扣扣号
- 密码
- 昵称
- 头像

登录页支持：

- 历史账号下拉展示
- 仅通过头像与昵称快速识别账号
- 点击后自动填充扣扣号与密码

退出登录时仅清理当前会话，不清空历史账号记录。

## 8. 联系人、会话与聊天

### 8.1 联系人模块

`ContactsFragment + ContactRepository` 负责：

- 联系人列表展示
- 扣扣号添加好友
- 好友关系去重校验
- 删除好友
- 跳转好友资料页

### 8.2 会话模块

`ConversationsFragment + ConversationRepository` 负责：

- 会话列表渲染
- 会话同步刷新
- 未读状态展示
- 从好友关系自动预热会话卡片

### 8.3 聊天模块

`ChatActivity + MessageRepository` 负责：

- 本地消息插入
- 会话摘要更新
- WebSocket 消息发送
- 当前账号视角的消息接收与落库
- 多账号消息隔离

当前消息隔离策略：

- 本地消息主键带 `ownerId`
- 会话与消息严格基于当前登录账号视角存储
- 切换账号时不会串消息、串会话

## 9. 设置中心设计

### 9.1 设置首页

`SettingsFragment` 负责：

- 昵称、头像、签名资料卡
- 设置树首页入口
- 退出登录

首页一级分类：

- 账号与安全
- 隐私与联系人
- 新消息通知
- 外观与显示
- 通用与存储
- 关于
- 退出登录

### 9.2 二级设置页

统一由 `SettingsDetailActivity` 承载。

`SettingsViewModel` 结合：

- `SettingsPage`
- `SettingsItem`
- `SettingsState`

动态生成列表型设置项。

### 9.3 已落地的设置能力

#### 账号与安全

- 当前账号展示
- 修改登录密码
- 本地密码保护
- 设备管理

#### 隐私与联系人

- 允许通过扣扣号找到我
- 好友验证方式
- 黑名单管理

#### 新消息通知

- 接收通知
- 声音
- 振动
- 消息预览
- 免打扰

#### 外观与显示

- 主题模式
- 聊天背景
- 字体大小
- 沉浸流光动效

#### 通用与存储

- 总占用统计
- 数据库占用
- 缓存占用
- 聊天附件占用
- 一键清理缓存
- 清空聊天记录

#### 关于

- 当前版本
- 版本信息与更新日志
- 当前运行环境信息

## 10. 主题系统与视觉组件

### 10.1 当前支持的背景主题

`外观与显示` 中当前支持以下背景主题：

- 蝴蝶流光
- 全息晶尘
- 极简暗调
- 极简白色
- 电子科幻
- 代码雨

### 10.2 外观适配核心类

- `AppearanceManager`
  - 主题背景切换
  - 页面级明暗样式控制
  - 文本缩放
  - 动效组件开关协调
- `IridescenceAnimator`
  - 流光、漂浮、按钮与环境动效启动入口

### 10.3 自定义背景组件

#### `ThemeAtmosphereView`

负责非代码雨主题的动态环境层，包含：

- 蝴蝶流光环境场
- 极简暗调微网格与失焦星尘
- 电子科幻 HUD 空间网格与数据链路
- 全息晶尘粒子景深场

#### `CodeRainView`

负责代码雨主题的独立背景渲染，当前已具备：

- 多层景深代码雨
- 等宽字符矩阵
- 领头字符高亮
- 渐隐拖尾
- 触底数字粉碎粒子
- 地面能量涟漪
- 底部透视地网

### 10.4 主题覆盖范围

当前已接入统一主题系统的界面：

- 登录页
- 注册页
- 消息页
- 联系人页
- 设置首页
- 设置二级页
- 聊天页
- 好友资料页
- 版本日志页
- 添加好友弹框
- 编辑资料弹框
- 修改密码弹框
- 设置选择/确认弹框

## 11. 底部导航与交互设计

`MainActivity` 中底部导航当前具备：

- 悬浮玻璃卡片底栏
- 页签切换回弹与轻微上浮
- 透明 ripple 替代系统默认波纹
- 主题相关的点击特效脉冲
- 会话未读 `BadgeDrawable`

如果主题启用特殊动效（如全息晶尘），导航栏会配合显示短时环境爆光反馈。

## 12. 用户资料与本地缓存

### 12.1 `UserHelper`

当前负责缓存：

- 当前登录账号
- 扣扣号
- 昵称
- 头像
- 个性签名
- 登录历史列表

### 12.2 资料同步

设置页修改资料后会同步到：

- Room 用户表
- `UserHelper` 本地缓存
- 历史账号列表中的昵称/头像
- WebSocket 资料广播消息

## 13. 版本日志机制

应用内更新日志使用本地 JSON 驱动：

- 数据源：`app/src/main/res/raw/version_changelog.json`
- 展示页：`VersionInfoActivity`
- 渲染方式：读取 JSON 后转为纯文本列表

该方案的优点是：

- Activity 代码无需频繁改动
- 更新日志与版本号更容易同步维护
- 适合快速增量迭代

## 14. 构建与调试

### 14.1 常用构建命令

```powershell
.\gradlew.bat assembleDebug
```

### 14.2 日常验证建议

- 注册至少两个账号进行本地多账号联调
- 优先验证好友添加、聊天会话预热与消息隔离
- 测试主题时重点检查：
  - 消息页
  - 联系人页
  - 设置首页
  - 设置二级页
  - 登录页与注册页
- 修改 `version_changelog.json` 后检查 JSON 格式是否合法

## 15. 当前阶段的设计重点

当前项目的设计重点已经从“功能打通”进入“功能稳定 + 主题系统统一”阶段，主要表现为：

- 页面结构基本稳定
- 数据分层已经成型
- 设置中心已完整树化
- 主题与背景系统已成为项目重要特色模块
- 文档、版本号与软件内更新日志需要同步维护

## 16. 后续建议方向

- 为 Room 补充显式 Migration
- 对登录密码与本地密码保护做更安全的加密存储
- 增强 WebSocket ACK、重试、重连和状态回执
- 让字体大小、主题背景对更多页面组件做更细粒度适配
- 补充 UI 自动化测试与构建检查流程
- 对主题切换后的布局统一性继续做专项优化

---

文档更新时间：2026-04-28  
文档依据：当前仓库实际代码结构与已落地页面能力
