# koukou：QQ 简易功能 Android App 开发设计文档

本文档以当前仓库真实代码为准，描述 `koukou` 项目的 Android 客户端、服务端接入方式、数据层设计、页面结构、主题系统以及当前联调状态，便于后续继续开发、联调与维护。

## 1. 项目概览

- 项目名称：`koukou`
- Android 包名：`com.example.koukou`
- Android 技术栈：`Java + XML + ViewBinding`
- 服务端技术栈：`Node.js + Express + ws + MySQL + JWT`
- 当前整体架构：`MVVM + Repository + Room + HTTP API + WebSocket + DataStore`

当前仓库包含完整的客户端与服务端：

- `app/`：Android 客户端
- `koukou-server/`：即时通讯服务端

## 2. Android 构建配置

### 2.1 基础构建

- AGP：`9.0.1`
- compileSdk：`36`
- minSdk：`24`
- targetSdk：`36`
- Java：`17`
- ViewBinding：已开启
- BuildConfig：已开启

### 2.2 当前版本

- `versionCode = 3`
- `versionName = 1.1.1`

### 2.3 当前网络地址

当前客户端配置为双线路：

```text
API_BASE_URL=https://zzj.abrdns.com
API_BASE_URL_BACKUP=https://120.26.247.39
WS_URL=wss://zzj.abrdns.com/ws
WS_URL_BACKUP=wss://120.26.247.39/ws
```

说明：

- 主线路优先使用域名
- 当 HTTPS / WSS 链路异常时，客户端会自动尝试备用 IP
- 该策略已接入注册、登录、联系人接口与 WebSocket

### 2.4 Android 权限

当前客户端已声明：

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.VIBRATE`

## 3. Android 客户端结构

```text
app/src/main/java/com/example/koukou/
├─ MainActivity.java
├─ MainPagerAdapter.java
├─ data/
│  ├─ local/
│  │  ├─ AppDatabase.java
│  │  ├─ dao/
│  │  └─ entity/
│  └─ repository/
│     ├─ ContactRepository.java
│     ├─ ConversationRepository.java
│     ├─ MessageRepository.java
│     ├─ SettingsRepository.java
│     └─ UserRepository.java
├─ network/
│  ├─ api/
│  │  └─ KoukouApiService.java
│  ├─ model/
│  │  └─ WebSocketMessage.java
│  ├─ websocket/
│  │  ├─ AppWebSocketListener.java
│  │  └─ WebSocketManager.java
│  ├─ ServerEndpointPolicy.java
│  └─ UnsafeTlsSupport.java
├─ theme/
├─ ui/
│  ├─ chat/
│  ├─ contacts/
│  ├─ conversations/
│  ├─ login/
│  ├─ settings/
│  └─ shared/
├─ utils/
└─ widget/
```

## 4. 服务端结构

```text
koukou-server/
├─ sql/
│  └─ init.sql
└─ src/
   ├─ auth.js
   ├─ config.js
   ├─ db.js
   ├─ index.js
   ├─ messages.js
   └─ sessions.js
```

服务端当前提供：

- `GET /health`
- `POST /api/register`
- `POST /api/login`
- `GET /api/users/available-id`
- `GET /api/users/:id`
- `GET /api/friends`
- `GET /api/friends/requests`
- `POST /api/friends/requests`
- `POST /api/friends/requests/:requestId/accept`
- `POST /api/friends/requests/:requestId/reject`
- `DELETE /api/friends/:friendId`
- `WS /ws`

## 5. 页面结构

### 5.1 启动与登录链路

- 启动页：`ui.login.LoginActivity`
- 注册页：`ui.login.RegisterActivity`
- 登录成功后进入：`MainActivity`

### 5.2 一级页面

`MainActivity` 通过 `ViewPager2 + BottomNavigationView` 承载三个一级分页：

- 消息：`ConversationsFragment`
- 联系人：`ContactsFragment`
- 我的：`SettingsFragment`

### 5.3 二级页面

当前二级页面包括：

- `ChatActivity`
- `FriendProfileActivity`
- `SettingsDetailActivity`
- `VersionInfoActivity`

## 6. 架构分层

### 6.1 View 层

包含：

- Activity
- Fragment
- Adapter
- XML 布局与 drawable

职责：

- 展示 UI
- 接收用户操作
- 观察 ViewModel 状态
- 承载主题与动效

### 6.2 ViewModel 层

主要包含：

- `LoginViewModel`
- `ConversationsViewModel`
- `ContactsViewModel`
- `ChatViewModel`
- `SettingsViewModel`

职责：

- 组织页面状态
- 与 Repository 交互
- 暴露 LiveData
- 将底层数据转换为页面可直接消费的状态模型

### 6.3 Repository 层

主要包含：

- `UserRepository`
- `ContactRepository`
- `ConversationRepository`
- `MessageRepository`
- `SettingsRepository`

职责：

- 聚合 Room、远程 API、WebSocket、DataStore
- 屏蔽线程切换
- 对上层提供统一业务接口

## 7. 数据层设计

### 7.1 Room

数据库入口：

- `AppDatabase`

核心实体：

- `UserEntity`
- `FriendEntity`
- `FriendRequestEntity`
- `ConversationEntity`
- `MessageEntity`

当前数据库仍使用：

- `fallbackToDestructiveMigration`

说明：适合当前开发联调阶段，后续正式化时建议补显式 Migration。

### 7.2 DataStore

`SettingsRepository` 当前统一托管：

- 通知总开关
- 声音 / 震动 / 预览 / 免打扰
- 本地密码保护
- 好友验证方式
- 是否允许通过扣扣号搜索
- 主题模式
- 聊天背景
- 字体大小
- 沉浸流光动效
- 黑名单

### 7.3 SharedPreferences

`UserHelper` 当前负责缓存轻量登录态：

- 当前账号
- 当前密码
- 当前用户 ID
- 当前 JWT token
- 当前昵称
- 当前头像
- 当前签名
- 历史登录账号列表

## 8. 登录、注册与登录态

### 8.1 注册

注册页当前规则：

- 用户输入昵称
- 系统可随机生成唯一 10 位扣扣号
- 也支持手动输入 10 位扣扣号
- 通过 `KoukouApiService.register()` 调用服务端注册
- 注册成功后写入 Room，并把账号密码回填到登录页

### 8.2 登录

登录页当前规则：

- 使用扣扣号 + 密码登录
- 通过 `KoukouApiService.login()` 调用服务端
- 成功后同步到 Room
- 同时保存历史账号、昵称、头像、JWT token

### 8.3 登录后主链路

登录成功后：

1. 跳转 `MainActivity`
2. `UserHelper` 中保存当前 token
3. `MainActivity` 优先读取 token 建立 WebSocket
4. 若无 token，则回退到开发期 userId token 逻辑
5. `MessageRepository` 负责 ACK、离线同步和消息落库

## 9. 远程接口与通信设计

### 9.1 HTTP API

客户端远程接口入口：

- `network/api/KoukouApiService`

当前能力：

- 登录
- 注册
- 生成可用扣扣号
- 根据扣扣号查询用户
- 拉取好友列表
- 拉取好友申请
- 发起/接受/拒绝好友申请
- 删除好友

### 9.2 双线路兜底策略

`KoukouApiService` 当前已支持：

- 主域名请求失败后自动切换备用 IP
- 对备用 IP 启用宽松 TLS 兼容
- 区分网络异常、证书异常、超时、服务器不可达

这套机制的主要目的，是避免主域名链路异常时，注册/登录直接全部失效。

### 9.3 WebSocket

客户端 WebSocket 入口：

- `network/websocket/WebSocketManager`

消息模型：

- `network/model/WebSocketMessage`

当前主要消息类型：

- `chat_message`
- `message_ack`
- `sync_request`
- `sync_response`
- `profile_update`
- `heartbeat_ping`
- `heartbeat_pong`

### 9.4 WebSocket 稳定性修复

针对“服务端部署后登录成功立即闪退”和“主域名不稳定导致连接失败”的问题，当前代码已经补上：

- WebSocket 主域名 + 备用 IP 双线路连接
- URL 与 token 拼接安全处理
- 连接启动异常保护
- 回调分发异常保护
- 消息解析异常保护
- 自动重连与心跳保活

## 10. 会话、联系人与聊天

### 10.1 联系人模块

`ContactsFragment + ContactRepository` 当前负责：

- 拉取远程好友列表
- 展示好友申请
- 添加好友
- 通过/拒绝好友申请
- 删除好友
- 跳转好友资料页

说明：

- 当前联系人模块已具备在线操作能力
- 好友申请状态更多依赖页面刷新/返回时同步
- 客户端尚未完整消费 `friend_request` 和 `friend_request_status` 的实时推送

### 10.2 会话模块

`ConversationsFragment + ConversationRepository` 当前负责：

- 会话列表
- 未读数展示
- 会话摘要刷新

### 10.3 聊天模块

`ChatActivity + MessageRepository` 当前负责：

- 本地消息插入
- 通过 WebSocket 发送消息
- ACK 回执更新
- 接收实时消息
- 离线消息同步
- 会话摘要同步更新

说明：

- 已成为好友的两个账号当前可以在线单聊
- 聊天错误码的即时 UI 提示仍可继续加强

## 11. 设置中心

### 11.1 设置首页

`SettingsFragment` 当前提供：

- 头像、昵称、签名
- 账号与安全
- 隐私与联系人
- 新消息通知
- 外观与显示
- 通用与存储
- 关于
- 退出登录

### 11.2 设置二级页

由 `SettingsDetailActivity` 统一承载，页面配置来源于：

- `SettingsPage`
- `SettingsItem`
- `SettingsViewModel`

### 11.3 已实现的设置能力

- 修改密码
- 本地密码保护
- 设备信息展示
- 好友验证方式
- 黑名单管理
- 通知相关开关
- 主题模式 / 背景 / 字体 / 动效
- 缓存与聊天记录清理
- 版本信息与更新日志

## 12. 主题系统

### 12.1 当前背景主题

- 蝴蝶流光
- 全息晶尘
- 粉色兔兔
- 电子科幻
- 代码雨
- 互动雨滴
- 极简暗调
- 极简白色

### 12.2 主题核心类

- `AppearanceManager`
- `IridescenceAnimator`
- `ThemeAtmosphereView`
- `CodeRainView`
- `RaindropFxView`

### 12.3 覆盖范围

当前主题系统已覆盖：

- 登录页
- 注册页
- 消息页
- 联系人页
- 设置首页
- 设置二级页
- 聊天页
- 好友资料页
- 版本日志页
- 主要主题弹窗

## 13. 更新日志机制

应用内更新日志数据源：

- `app/src/main/res/raw/version_changelog.json`

展示页：

- `VersionInfoActivity`

版本展示依赖：

- `app/build.gradle.kts` 中的 `versionName`

## 14. 当前联调状态

### 14.1 已打通部分

- 服务器健康检查
- 注册 / 登录 API
- JWT 返回与客户端保存
- WebSocket 建连
- 消息发送、ACK、离线同步
- 远程好友列表与好友申请接口

### 14.2 当前真实状态说明

项目已经具备在线好友与在线聊天的主链路，但还不等于“全部实时闭环”：

- 在线聊天：基本可用
- 在线添加好友：可在线操作，但实时推送消费仍可继续补齐

## 15. 构建与调试

### 15.1 Android 构建

```powershell
.\gradlew.bat assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 15.2 服务端启动

```bash
cd koukou-server
npm install
npm run start
```

### 15.3 云服务器联调重点

优先检查：

- `API_BASE_URL` / `API_BASE_URL_BACKUP`
- `WS_URL` / `WS_URL_BACKUP`
- Android 网络权限
- `/api/login` 返回字段是否完整
- `/ws` 是否可连接
- 服务端 `sync_response / chat_message` 是否与客户端字段兼容

## 16. 后续建议

- 补齐好友申请相关 WebSocket 实时消费
- 为消息错误码增加即时 UI 提示
- 为 Room 增加显式 Migration
- 收敛旧的乱码文案资源与异常提示
- 继续统一主题组件尺寸、间距与浅/深色适配

---

文档更新时间：2026-05-17  
文档依据：当前仓库实际代码结构、现有页面、服务端接口、主题系统与最新联调修复内容
