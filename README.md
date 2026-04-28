# Koukou

一个基于 Android 原生 `Java + XML` 的轻量社交聊天 Demo，围绕登录注册、好友体系、会话聊天、主题化视觉和设置中心做了较完整的功能演示。

## 特色能力

- 扣扣号注册、登录和好友查找
- 会话列表、联系人列表、聊天详情页
- 历史账号下拉快速登录
- 设置中心与二级设置页
- 多套主题背景与动态氛围效果
- Room 本地数据存储
- WebSocket 实时消息链路
- DataStore 设置持久化

## 技术栈

- Android ViewBinding
- Java 17
- RecyclerView / ViewPager2 / Material Components
- Room
- OkHttp WebSocket
- Jetpack DataStore (RxJava3)
- Glide

## 运行环境

- Android Studio 最新稳定版
- JDK 17
- Android SDK 36
- 最低支持 Android 7.0 (`minSdk 24`)

## 本地运行

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

调试包输出路径：

`app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```text
app/src/main/java/com/example/koukou
├─ data
│  ├─ local
│  └─ repository
├─ network
│  ├─ model
│  └─ websocket
├─ ui
│  ├─ chat
│  ├─ contacts
│  ├─ conversations
│  ├─ login
│  └─ settings
├─ utils
└─ widget
```

## 主要页面

- 登录 / 注册
- 消息会话
- 联系人
- 聊天页
- 好友资料
- 设置首页
- 设置二级页
- 版本更新日志

## 主题系统

当前项目内已实现多套背景主题，包括：

- 蝴蝶流光
- 全息晶尘
- 电子科幻
- 代码雨
- 极简暗调
- 极简白色

## 设计与开发文档

项目内附带完整设计文档：

- [koukou：QQ简易功能 Android App 开发设计文档.md](./koukou：QQ简易功能%20Android%20App%20开发设计文档.md)

## 注意事项

- `local.properties`、构建产物、签名文件不会进入 Git 仓库
- 如果要发布到 GitHub，建议补充项目截图、License 和 issue / PR 模板
