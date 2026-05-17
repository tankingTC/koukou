# Koukou

`Koukou` 是一个基于 Android 原生 `Java + XML` 开发的轻量即时通讯项目，当前仓库同时包含：

- Android 客户端：`app/`
- Node.js 实时服务端：`koukou-server/`
- 云服务器与 Docker 部署文档

项目目前已经从单机 Demo 演进为一套可本地运行、可连接云服务器、可持续扩展的 IM 样例，核心围绕：

- 10 位扣扣号注册、登录、好友查找
- 联系人、会话、聊天、消息同步
- HTTP API + WebSocket 实时通信
- 设置中心与二级设置页
- 多套主题背景与沉浸动效

## 当前能力

- 注册时支持随机生成或手动输入唯一 10 位扣扣号
- 账号历史支持头像、昵称识别与快捷登录
- 联系人支持远程好友列表、好友申请、通过/拒绝、删除好友
- 聊天支持 WebSocket 实时收发、ACK、离线同步、会话摘要更新
- 设置中心支持通知、隐私、安全、主题、字体、缓存、关于等模块
- 支持多套主题背景：蝴蝶流光、全息晶尘、电子科幻、代码雨、互动雨滴、极简暗调、极简白色、粉色兔兔
- 软件内置版本更新日志页

## 技术栈

### Android 客户端

- Java 17
- XML + ViewBinding
- MVVM + Repository
- Room
- OkHttp / WebSocket
- DataStore Preferences RxJava3
- Material Components
- Glide

### 服务端

- Node.js
- Express
- ws
- MySQL
- JWT
- PM2 + Nginx

## 仓库结构

```text
koukou/
├─ app/                          Android 客户端
├─ koukou-server/                Node.js 即时通讯服务端
├─ README.md
├─ koukou：QQ简易功能 Android App 开发设计文档.md
├─ 云服务器服务部署指南.md
└─ Docker部署设计文档.md
```

## Android 结构

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
├─ theme/
├─ ui/
│  ├─ login/
│  ├─ conversations/
│  ├─ contacts/
│  ├─ chat/
│  ├─ settings/
│  └─ shared/
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

## 登录与通信链路

当前客户端不是纯本地模式，而是“远程鉴权 + 本地缓存 + WebSocket 实时通信”的组合：

1. 登录/注册页通过 `KoukouApiService` 调用服务端 `/api/login`、`/api/register`
2. 服务端返回用户资料与 JWT token
3. 客户端将用户资料写入 Room，并将登录态写入 `UserHelper`
4. 进入 `MainActivity` 后优先使用 JWT token 连接 WebSocket
5. `MessageRepository` 处理消息发送、ACK、离线同步与本地落库

## 当前网络配置

`app/build.gradle.kts` 当前内置了主线路与备用线路：

```text
API_BASE_URL=https://zzj.abrdns.com
API_BASE_URL_BACKUP=https://120.26.247.39
WS_URL=wss://zzj.abrdns.com/ws
WS_URL_BACKUP=wss://120.26.247.39/ws
```

说明：

- 客户端会优先连接域名
- 当主域名 HTTPS / WSS 异常时，会自动尝试备用 IP 线路
- 这套兜底已经接入注册、登录、好友接口与 WebSocket

## 本地构建

Windows：

```powershell
.\gradlew.bat assembleDebug
```

调试包输出位置：

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

## 相关文档

- [开发设计文档](./koukou：QQ简易功能%20Android%20App%20开发设计文档.md)
- [云服务器服务部署指南](./云服务器服务部署指南.md)
- [Docker 部署设计文档](./Docker部署设计文档.md)

## 最近更新重点

- 接入远程注册、登录、好友与消息服务端
- 登录成功后优先使用 JWT token 建立 WebSocket
- 注册/登录/API/WebSocket 增加主域名 + 备用 IP 双线路兜底
- 修复登录后因 WebSocket 或服务器异常导致的闪退问题
- 完整设置中心与主题系统持续升级
- 应用内更新日志与关于页同步维护
