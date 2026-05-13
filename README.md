# Koukou

一个基于 Android 原生 `Java + XML` 的轻量即时通讯项目，当前仓库同时包含：

- Android 客户端 `app/`
- Node.js 即时通讯服务端 `koukou-server/`
- 云服务器与 Docker 部署文档

项目目标是围绕“扣扣号体系 + 联系人 + 会话 + 实时消息 + 主题化视觉”持续迭代，做成一套可本地运行、可连接云服务器、可继续扩展的完整 Demo。

## 当前能力

- 10 位扣扣号注册、登录、好友查找
- 历史账号下拉快捷登录
- 联系人管理、好友申请、好友资料页
- 会话列表、聊天页、未读数同步
- WebSocket 实时消息、ACK、离线同步
- 设置中心与二级设置页
- 多套主题背景与沉浸动效
- 应用内更新日志展示

## 技术栈

### Android 客户端

- Java 17
- XML + ViewBinding
- MVVM + Repository
- Room
- OkHttp WebSocket
- DataStore Preferences RxJava3
- Glide
- Material Components

### 服务端

- Node.js
- Express
- ws
- MySQL
- JWT

## 仓库结构

```text
koukou/
├─ app/                         Android 客户端
├─ koukou-server/               Node.js 即时通讯服务端
├─ README.md
├─ koukou：QQ简易功能 Android App 开发设计文档.md
├─ 云服务器服务部署指南.md
└─ Docker部署设计文档.md
```

## Android 客户端结构

```text
app/src/main/java/com/example/koukou
├─ MainActivity.java
├─ MainPagerAdapter.java
├─ data/
│  ├─ local/
│  └─ repository/
├─ network/
│  ├─ api/
│  ├─ model/
│  └─ websocket/
├─ ui/
│  ├─ login/
│  ├─ conversations/
│  ├─ contacts/
│  ├─ chat/
│  └─ settings/
├─ utils/
└─ widget/
```

## 服务端结构

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

## 登录与通讯链路

当前客户端已经不是纯本地单机模式，而是“远程鉴权 + 本地缓存 + WebSocket 实时通讯”的组合：

1. 登录页调用 `network/api/KoukouApiService`
2. 服务端 `/api/login` 或 `/api/register` 返回用户资料与 token
3. 客户端将账号资料持久化到 Room / SharedPreferences / DataStore
4. 进入 `MainActivity` 后启动 WebSocket，连接 `wss://.../ws`
5. `MessageRepository` 处理消息发送、ACK、离线同步与落库

## 网络配置

当前 `app/build.gradle.kts` 中 `debug` 和 `release` 都已指向云服务器：

```text
API_BASE_URL=https://zzj.abrdns.com
WS_URL=wss://zzj.abrdns.com/ws
```

客户端已补齐以下网络权限：

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

## 本地构建

Windows:

```powershell
.\gradlew.bat assembleDebug
```

输出 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 服务端本地启动

```bash
cd koukou-server
npm install
npm run start
```

健康检查：

```bash
curl http://127.0.0.1:8080/health
```

## 部署文档

- [Android 开发设计文档](./koukou：QQ简易功能%20Android%20App%20开发设计文档.md)
- [云服务器服务部署指南](./云服务器服务部署指南.md)
- [Docker 部署设计文档](./Docker部署设计文档.md)

## 最近重点更新

- 接入远程登录 / 注册 API
- 接入服务端好友申请与好友关系同步
- WebSocket 启动链路增加异常保护，避免登录成功后因服务器连接异常直接闪退
- 消息回调与离线同步落库逻辑补了防御性处理
- 主题系统、设置中心、更新日志、历史账号体系持续完善

## 后续建议

- 补充端到端联调日志与错误码规范
- 为 Room 增加显式 Migration
- 为登录态和 token 增加更清晰的刷新/失效策略
- 为服务端接口补充更完整的鉴权与风控策略
