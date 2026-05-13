# koukou：QQ 简易功能 Android App 开发设计文档

本文档以当前仓库真实代码为准，描述 `koukou` 项目的 Android 客户端、服务端接口接入方式、数据层设计、页面结构以及主题系统。目标是方便后续继续开发、联调和维护。

## 1. 项目概览

- 项目名称：`koukou`
- Android 包名：`com.example.koukou`
- Android 技术栈：`Java + XML + ViewBinding`
- 服务端技术栈：`Node.js + Express + ws + MySQL + JWT`
- 当前整体架构：`MVVM + Repository + Room + HTTP API + WebSocket + DataStore`

当前仓库已经不是纯本地单机版本，而是包含完整“客户端 + 服务器”结构：

- `app/`：Android 客户端
- `koukou-server/`：即时通讯服务端

## 2. Android 构建配置

### 2.1 基础构建

- AGP：`9.0.1`
- Gradle：`9.2.1`
- compileSdk：`36`
- minSdk：`24`
- targetSdk：`36`
- Java：`17`
- ViewBinding：开启
- BuildConfig：开启

### 2.2 当前版本

- `versionCode = 2`
- `versionName = 1.1.0`

### 2.3 当前网络地址

当前 `debug / release` 都指向云服务器：

```text
API_BASE_URL=https://zzj.abrdns.com
WS_URL=wss://zzj.abrdns.com/ws
```

### 2.4 必要权限

客户端当前已声明：

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
│  └─ websocket/
│     ├─ AppWebSocketListener.java
│     └─ WebSocketManager.java
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

- 健康检查 `/health`
- 注册 `/api/register`
- 登录 `/api/login`
- 生成可用扣扣号 `/api/users/available-id`
- 用户查询 `/api/users/:id`
- 好友列表 `/api/friends`
- 好友申请 `/api/friends/requests`
- WebSocket `/ws`

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
- 承载主题与动效表现

### 6.2 ViewModel 层

主要包括：

- `LoginViewModel`
- `ConversationsViewModel`
- `ContactsViewModel`
- `ChatViewModel`
- `SettingsViewModel`

职责：

- 组织页面状态
- 与 Repository 交互
- 暴露 LiveData
- 将设置和数据转换成页面可直接消费的模型

### 6.3 Repository 层

主要包括：

- `UserRepository`
- `ContactRepository`
- `ConversationRepository`
- `MessageRepository`
- `SettingsRepository`

职责：

- 聚合本地数据库、远程 API、WebSocket、DataStore
- 屏蔽线程切换
- 对上层提供统一业务接口

## 7. 数据层设计

### 7.1 Room

数据库入口：

- `AppDatabase`

当前核心实体：

- `UserEntity`
- `FriendEntity`
- `FriendRequestEntity`
- `ConversationEntity`
- `MessageEntity`

当前数据库仍使用开发阶段友好的：

- `fallbackToDestructiveMigration`

### 7.2 DataStore

`SettingsRepository` 已统一托管以下键值配置：

- 通知开关
- 声音 / 振动 / 预览 / 免打扰
- 本地密码保护
- 好友验证方式
- 是否允许通过扣扣号搜索
- 主题模式
- 聊天背景
- 字体大小
- 沉浸动效开关
- 黑名单

### 7.3 SharedPreferences

`UserHelper` 当前仍负责缓存以下轻量登录态信息：

- 当前账号
- 当前密码
- 当前用户 ID
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

### 8.2 登录

登录页当前规则：

- 使用扣扣号 + 密码登录
- 通过 `KoukouApiService.login()` 调用服务端
- 登录成功后将用户资料同步为本地 `UserEntity`
- 同时保存历史账号、昵称、头像、签名

### 8.3 登录后主链路

登录成功后：

1. 跳转 `MainActivity`
2. `MessageRepository` 设置当前用户上下文
3. `MainActivity` 读取当前 `userId`
4. `WebSocketManager.connect(currentUserId)` 建立实时连接
5. 连接成功后触发：
   - 离线消息同步
   - 待发送消息重发

## 9. 服务器接入设计

### 9.1 HTTP API

客户端 API 入口：

- `network/api/KoukouApiService`

当前能力：

- 登录
- 注册
- 生成可用扣扣号
- 根据扣扣号查询用户
- 拉取好友列表
- 拉取好友申请列表
- 发送 / 接受 / 拒绝好友申请
- 删除好友

### 9.2 WebSocket

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

### 9.3 本次稳定性修复

针对“服务器部署后登录成功立即闪退”的问题，当前代码已经补上：

- Android 网络权限
- WebSocket 启动异常保护
- `WS_URL` / token 拼接安全处理
- 回调分发异常保护
- 消息落库异常保护
- 离线同步与重发逻辑保护

修复目标是：即使服务端数据不规范或连接失败，也不能再把客户端直接拖崩。

## 10. 会话、联系人与聊天

### 10.1 联系人模块

`ContactsFragment + ContactRepository` 负责：

- 拉取好友列表
- 展示好友申请
- 添加好友
- 处理好友申请
- 删除好友
- 跳转好友资料页

### 10.2 会话模块

`ConversationsFragment + ConversationRepository` 负责：

- 会话列表
- 未读数展示
- 会话状态更新

### 10.3 聊天模块

`ChatActivity + MessageRepository` 负责：

- 本地消息插入
- 通过 WebSocket 发送消息
- ACK 回执更新
- 接收服务端推送消息
- 离线消息同步后落库
- 会话摘要同步更新

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

统一由 `SettingsDetailActivity` 承载，页面配置来源于：

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

当前主题系统已经覆盖：

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

## 14. 构建与联调

### 14.1 Android 构建

```powershell
.\gradlew.bat assembleDebug
```

输出 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 14.2 服务端启动

```bash
cd koukou-server
npm install
npm run start
```

### 14.3 云服务器联调重点

当服务器已部署但客户端登录后出现问题时，优先检查：

- `API_BASE_URL`
- `WS_URL`
- Android 网络权限
- `/api/login` 返回字段是否完整
- `/ws` 是否可连接
- 服务端首帧 `sync_response / chat_message` 是否与客户端字段兼容

## 15. 当前阶段重点

当前项目已从“单机功能演示”进入“客户端 + 服务端联调 + 主题系统深化”阶段，后续重点应放在：

- 远程登录与本地缓存的一致性
- WebSocket 稳定性与离线同步
- 数据迁移与长期可维护性
- 主题统一性和页面布局稳定性

---

文档更新时间：2026-05-13  
文档依据：当前仓库实际代码结构、现有页面、服务端接口与已落地功能
