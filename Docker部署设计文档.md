# Koukou 即时通讯 Docker 部署设计文档

## 0. 快速上手：基于当前云服务器切换 Docker

当前云服务器已经按《云服务器服务部署指南》完成部署，现有可用信息如下：

```text
服务器：阿里云 ECS Ubuntu 22.04
公网 IP：120.26.247.39
域名：zzj.abrdns.com
当前服务目录：/opt/koukou/koukou-server
当前公网 API：https://zzj.abrdns.com
当前公网 WebSocket：wss://zzj.abrdns.com/ws
当前 Nginx 反代目标：http://127.0.0.1:8080
当前 HTTPS 证书：/etc/letsencrypt/live/zzj.abrdns.com/
```

如果只是想在这台服务器上快速改成 Docker 部署，推荐走下面这条最短路径：

```bash
ssh root@120.26.247.39
cd /opt/koukou
git pull
cd /opt/koukou/koukou-server
```

第一次使用 Docker 前，先确认 Docker 所需文件已经存在：

```bash
ls -la Dockerfile .dockerignore docker-compose.yml
```

如果提示文件不存在，先按本文第 6、7、9 节把 `Dockerfile`、`.dockerignore`、`docker-compose.yml` 写入 `koukou-server/`，再继续执行下面步骤。

安装 Docker：

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo tee /etc/apt/keyrings/docker.asc > /dev/null
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
docker -v
docker compose version
```

写入 Docker 环境变量：

```bash
cd /opt/koukou/koukou-server
cp .env.example .env.docker

cat > .env.docker <<'EOF'
PORT=8080
HOST_API_PORT=127.0.0.1:8080

MYSQL_ROOT_PASSWORD=123456
MYSQL_DATABASE=koukou_im
MYSQL_USER=koukou
MYSQL_PASSWORD=123456

JWT_SECRET=replace_with_a_long_random_secret
DEV_AUTH_ALLOW_USER_ID=true
EOF

sed -i "s/replace_with_a_long_random_secret/$(openssl rand -hex 32)/" .env.docker
```

停掉当前 PM2 进程，释放 `8080` 端口：

```bash
pm2 stop koukou-server || true
pm2 delete koukou-server || true
```

启动 Docker 版本：

```bash
docker compose --env-file .env.docker up -d --build
docker compose --env-file .env.docker ps
curl http://127.0.0.1:8080/health
curl https://zzj.abrdns.com/health
```

如果 `curl https://zzj.abrdns.com/health` 正常，说明原来的 Nginx 和 HTTPS/WSS 配置可以继续复用，不需要重新申请证书。

WebSocket 生产地址仍然是：

```text
wss://zzj.abrdns.com/ws
```

如果要回滚到原来的 PM2 部署：

```bash
cd /opt/koukou/koukou-server
docker compose --env-file .env.docker down
pm2 start ecosystem.config.js --env production --update-env
pm2 save
curl http://127.0.0.1:8080/health
curl https://zzj.abrdns.com/health
```

如果需要保留当前宿主机 MySQL 中已有的用户和消息，先备份再切换：

```bash
mkdir -p /opt/koukou/backups
mysqldump -u koukou -p koukou_im > /opt/koukou/backups/koukou_im_before_docker_$(date +%F_%H%M%S).sql
```

Docker MySQL 首次启动完成后导入：

```bash
cd /opt/koukou/koukou-server
docker compose --env-file .env.docker exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < /opt/koukou/backups/备份文件名.sql
```

注意：如果 Docker MySQL 是空库，`sql/init.sql` 会先自动建表，再导入备份。如果备份里包含 `CREATE TABLE`，遇到表已存在时可能报错；这种情况可以先清空测试 volume 后再导入，或导出时只导数据。

## 1. 文档目标

本文档用于设计和落地 Koukou 即时通讯服务端的 Docker 化部署方案，覆盖本地开发、云服务器部署、数据库初始化、WebSocket 访问、Nginx 反向代理、HTTPS/WSS、安全策略、运维备份和常见问题排查。

本文档和《云服务器服务部署指南》的关系：

```text
云服务器服务部署指南：记录当前已经跑通的 PM2 + 宿主机 MySQL + Nginx + HTTPS 部署方式。
Docker部署设计文档：记录后续想改成 Docker Compose 部署时的设计、文件和操作步骤。
```

当前项目服务端目录：

```text
koukou-server/
```

当前服务端技术栈：

```text
Node.js
Express
ws WebSocket
MySQL 8
JWT
```

Docker 化后的目标：

- 后端服务和 MySQL 通过 Docker Compose 一键启动。
- MySQL 数据持久化，不因容器重建而丢失。
- 首次启动自动执行 `sql/init.sql` 初始化库表。
- 后端容器通过 Docker 内部网络访问 MySQL。
- 外部客户端通过 HTTP API 和 WebSocket 访问后端。
- 生产环境建议通过 Nginx 暴露 HTTPS 和 WSS。
- 敏感配置通过 `.env` 管理，不提交真实密码。

## 2. 当前项目分析

服务端入口：

```text
koukou-server/src/index.js
```

HTTP 健康检查接口：

```text
GET /health
```

登录注册接口：

```text
POST /api/register
POST /api/login
```

WebSocket 入口：

```text
/ws?token=JWT_TOKEN
```

数据库初始化脚本：

```text
koukou-server/sql/init.sql
```

数据库表：

```text
users
friends
messages
offline_messages
```

需要注意：当前服务端代码如果监听 `127.0.0.1`，在 Docker 容器中会导致宿主机端口映射后仍可能无法访问。容器内服务必须监听 `0.0.0.0`。

建议将 `koukou-server/src/index.js` 监听逻辑调整为：

```js
const host = process.env.HOST || '0.0.0.0';

server.listen(config.port, host, () => {
  console.log(`koukou-server listening on http://${host}:${config.port}`);
});
```

## 3. 总体架构设计

### 3.1 开发和测试架构

```text
Android App / Postman / WebSocket Client
        |
        | HTTP :8080
        | WS   :8080/ws
        v
宿主机 Docker 端口映射
        |
        v
koukou-server 容器
        |
        | Docker 内部网络
        | mysql:3306
        v
mysql 容器
        |
        v
Docker volume: koukou_mysql_data
```

适用场景：

- 本地开发。
- 局域网真机调试。
- 云服务器临时测试。
- 不配置域名和 HTTPS 的快速验证。

### 3.2 生产推荐架构

```text
Android App
        |
        | HTTPS / WSS
        v
Nginx 容器或宿主机 Nginx
        |
        | Docker 内部网络 HTTP :8080
        v
koukou-server 容器
        |
        | Docker 内部网络 mysql:3306
        v
mysql 容器
        |
        v
Docker volume: koukou_mysql_data
```

生产环境推荐只开放：

```text
80/tcp
443/tcp
22/tcp
```

不建议开放：

```text
3306/tcp
6379/tcp
8080/tcp
```

如果为了调试临时开放 `8080`，调试完成后应关闭。

## 4. 容器划分设计

### 4.1 koukou-server 容器

职责：

- 提供登录、注册、健康检查等 HTTP API。
- 提供 WebSocket 长连接。
- 校验 JWT。
- 写入和查询 MySQL 消息数据。
- 处理离线消息同步。

镜像基础：

```text
node:20-bookworm-slim
```

暴露端口：

```text
8080
```

启动命令：

```text
node src/index.js
```

依赖：

```text
mysql
```

### 4.2 mysql 容器

职责：

- 存储用户、好友、消息和离线消息。
- 首次启动时执行 `sql/init.sql` 初始化库表。
- 使用 Docker volume 持久化数据。

镜像：

```text
mysql:8.4
```

内部端口：

```text
3306
```

持久化卷：

```text
koukou_mysql_data:/var/lib/mysql
```

初始化脚本挂载：

```text
./sql/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
```

注意：`/docker-entrypoint-initdb.d/` 只会在 MySQL 数据目录为空时执行。已经初始化过的 volume 不会重复执行 SQL。

### 4.3 nginx 容器或宿主机 Nginx

职责：

- 绑定域名。
- 终止 HTTPS。
- 将 `/` 和 `/api/*` 转发给后端 HTTP 服务。
- 将 `/ws` 转发给后端 WebSocket 服务。
- 隐藏后端 `8080` 端口。

生产环境可以二选一：

- 使用宿主机 Nginx，维护简单，证书申请方便。
- 使用 Nginx 容器，部署形态统一。

本文优先推荐宿主机 Nginx，因为云服务器上调试和续签证书更直观。

## 5. 目录结构设计

建议在 `koukou-server/` 下新增：

```text
koukou-server/
├── Dockerfile
├── .dockerignore
├── docker-compose.yml
├── .env.example
├── sql/
│   └── init.sql
└── src/
    ├── index.js
    ├── config.js
    ├── db.js
    ├── auth.js
    ├── messages.js
    └── sessions.js
```

生产服务器上建议部署到：

```text
/opt/koukou/koukou-server
```

## 6. Dockerfile 设计

文件路径：

```text
koukou-server/Dockerfile
```

内容：

```dockerfile
FROM node:20-bookworm-slim

WORKDIR /app

ENV NODE_ENV=production

COPY package*.json ./
RUN npm ci --omit=dev

COPY src ./src

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8080/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "src/index.js"]
```

设计说明：

- 使用 `node:20-bookworm-slim`，兼顾兼容性和镜像体积。
- 使用 `npm ci` 保证依赖安装和 `package-lock.json` 一致。
- 使用 `--omit=dev` 排除开发依赖。
- 只复制 `src` 和依赖文件，减少镜像无关内容。
- 增加 `HEALTHCHECK`，方便 Compose 和运维判断服务是否健康。

## 7. .dockerignore 设计

文件路径：

```text
koukou-server/.dockerignore
```

内容：

```gitignore
node_modules
npm-debug.log
.env
.git
.idea
.vscode
Dockerfile
docker-compose.yml
```

设计说明：

- 避免把本机 `node_modules` 打进镜像。
- 避免把 `.env` 中的真实密钥打进镜像。
- 避免把 IDE、Git 元数据放入镜像。

## 8. 环境变量设计

服务端已经通过 `dotenv` 读取环境变量。Docker 部署时推荐由 Compose 注入环境变量。

核心变量：

| 变量名 | 示例值 | 说明 |
| --- | --- | --- |
| `PORT` | `8080` | 服务端监听端口 |
| `HOST` | `0.0.0.0` | 容器内监听地址 |
| `MYSQL_HOST` | `mysql` | MySQL Compose 服务名 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_USER` | `koukou` | MySQL 应用账号 |
| `MYSQL_PASSWORD` | `change_me` | MySQL 应用账号密码 |
| `MYSQL_DB` | `koukou_im` | 数据库名 |
| `JWT_SECRET` | `long_random_secret` | JWT 签名密钥 |
| `DEV_AUTH_ALLOW_USER_ID` | `false` | 生产环境关闭开发鉴权 |
| `TZ` | `Asia/Shanghai` | 容器时区 |

生产环境要求：

- `MYSQL_PASSWORD` 必须换成强密码。
- `MYSQL_ROOT_PASSWORD` 必须换成强密码。
- `JWT_SECRET` 必须换成高强度随机字符串。
- `DEV_AUTH_ALLOW_USER_ID` 必须设置为 `false`。
- `.env` 不提交到 Git。

生成随机密钥示例：

```bash
openssl rand -hex 32
```

## 9. Docker Compose 设计

文件路径：

```text
koukou-server/docker-compose.yml
```

内容：

```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: koukou-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      TZ: Asia/Shanghai
    volumes:
      - koukou_mysql_data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u$${MYSQL_USER} -p$${MYSQL_PASSWORD} --silent"]
      interval: 10s
      timeout: 5s
      retries: 10

  koukou-server:
    build: .
    container_name: koukou-server
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      PORT: ${PORT}
      HOST: 0.0.0.0
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_DB: ${MYSQL_DATABASE}
      JWT_SECRET: ${JWT_SECRET}
      DEV_AUTH_ALLOW_USER_ID: ${DEV_AUTH_ALLOW_USER_ID}
      TZ: Asia/Shanghai
    ports:
      - "${HOST_API_PORT:-8080}:8080"

volumes:
  koukou_mysql_data:
```

设计说明：

- `mysql` 和 `koukou-server` 默认在同一个 Compose 网络中。
- 后端通过 `MYSQL_HOST=mysql` 访问数据库。
- `depends_on.condition: service_healthy` 确保 MySQL 健康后再启动后端。
- MySQL 不需要在生产环境暴露 `3306` 到公网。
- 后端 `8080` 可在测试时映射到宿主机；生产环境可只允许本机访问或交给 Nginx 代理。

如果生产环境不希望公网直接访问 `8080`，可以改成只监听宿主机本地：

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

这样公网无法直接访问 `8080`，只能通过宿主机 Nginx 转发。

## 10. .env 设计

文件路径：

```text
koukou-server/.env
```

示例：

```env
PORT=8080
HOST_API_PORT=8080

MYSQL_ROOT_PASSWORD=replace_with_mysql_root_password
MYSQL_DATABASE=koukou_im
MYSQL_USER=koukou
MYSQL_PASSWORD=replace_with_mysql_app_password

JWT_SECRET=replace_with_a_long_random_secret
DEV_AUTH_ALLOW_USER_ID=false
```

如果同一台服务器上保留原来的 PM2 部署配置，推荐 Docker 单独使用：

```text
koukou-server/.env.docker
```

这样 `.env` 仍然给 PM2/Node 直启使用，`.env.docker` 专门给 Docker Compose 使用。启动时显式指定：

```bash
docker compose --env-file .env.docker up -d --build
```

文件路径：

```text
koukou-server/.env.example
```

建议保留示例值，不写真实密码：

```env
PORT=8080
HOST_API_PORT=8080

MYSQL_ROOT_PASSWORD=change_me_root
MYSQL_DATABASE=koukou_im
MYSQL_USER=koukou
MYSQL_PASSWORD=change_me

JWT_SECRET=change_me_to_a_long_random_secret
DEV_AUTH_ALLOW_USER_ID=false
```

## 11. 本地部署步骤

### 11.1 前置条件

安装：

```bash
docker -v
docker compose version
```

进入服务端目录：

```bash
cd koukou-server
```

### 11.2 创建环境变量

```bash
cp .env.example .env
```

编辑 `.env`，替换：

```text
MYSQL_ROOT_PASSWORD
MYSQL_PASSWORD
JWT_SECRET
```

### 11.3 构建并启动

```bash
docker compose up -d --build
```

### 11.4 查看状态

```bash
docker compose ps
```

期望状态：

```text
koukou-mysql     healthy
koukou-server    running / healthy
```

### 11.5 查看日志

```bash
docker compose logs -f koukou-server
docker compose logs -f mysql
```

### 11.6 测试健康检查

```bash
curl http://127.0.0.1:8080/health
```

期望返回：

```json
{
  "ok": true,
  "service": "koukou-server",
  "time": 1234567890
}
```

### 11.7 测试注册登录

注册：

```bash
curl -X POST http://127.0.0.1:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"account":"10001","password":"123456","nickname":"Alice"}'
```

登录：

```bash
curl -X POST http://127.0.0.1:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"account":"10001","password":"123456"}'
```

登录返回中的 `token` 用于 WebSocket：

```text
ws://127.0.0.1:8080/ws?token=TOKEN
```

## 12. 云服务器部署步骤

### 12.1 安装 Docker

Ubuntu 22.04 示例：

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo tee /etc/apt/keyrings/docker.asc > /dev/null
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
docker -v
docker compose version
```

### 12.2 拉取代码

```bash
cd /opt
sudo git clone https://github.com/tankingTC/koukou.git koukou
sudo chown -R $USER:$USER /opt/koukou
cd /opt/koukou/koukou-server
```

如果已经存在项目：

```bash
cd /opt/koukou
git pull
cd koukou-server
```

### 12.3 配置环境变量

```bash
cp .env.example .env
nano .env
```

生产环境必须替换：

```text
MYSQL_ROOT_PASSWORD
MYSQL_PASSWORD
JWT_SECRET
```

### 12.4 启动服务

```bash
docker compose up -d --build
```

### 12.5 验证服务

```bash
docker compose ps
curl http://127.0.0.1:8080/health
```

如果临时开放了 `8080`，也可以从本机访问：

```bash
curl http://服务器公网IP:8080/health
```

### 12.6 从当前 PM2 部署切换到 Docker

当前云服务器已经有一套可用部署：

```text
PM2 管理 Node.js 服务
宿主机 MySQL
宿主机 Nginx
Let's Encrypt 证书
```

切换到 Docker 时，建议保留宿主机 Nginx 和证书，只替换 `127.0.0.1:8080` 后面的服务实现：

```text
切换前：Nginx -> PM2 Node.js -> 宿主机 MySQL
切换后：Nginx -> Docker koukou-server -> Docker MySQL
```

具体步骤：

```bash
cd /opt/koukou/koukou-server

# 1. 备份宿主机 MySQL
mkdir -p /opt/koukou/backups
mysqldump -u koukou -p koukou_im > /opt/koukou/backups/koukou_im_before_docker_$(date +%F_%H%M%S).sql

# 2. 停止 PM2，释放 8080
pm2 stop koukou-server || true
pm2 delete koukou-server || true

# 3. 启动 Docker
docker compose --env-file .env.docker up -d --build
docker compose --env-file .env.docker ps

# 4. 检查本机端口和公网域名
curl http://127.0.0.1:8080/health
curl https://zzj.abrdns.com/health
```

如果需要把旧数据导入 Docker MySQL：

```bash
docker compose --env-file .env.docker exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < /opt/koukou/backups/备份文件名.sql
```

回滚步骤：

```bash
cd /opt/koukou/koukou-server
docker compose --env-file .env.docker down
pm2 start ecosystem.config.js --env production --update-env
pm2 save
curl http://127.0.0.1:8080/health
curl https://zzj.abrdns.com/health
```

切换过程中 Nginx 配置通常不用改，因为它始终代理到：

```text
http://127.0.0.1:8080
```

## 13. Nginx HTTPS/WSS 设计

生产环境推荐外部使用：

```text
https://api.example.com
wss://api.example.com/ws
```

当前服务器已经可直接使用：

```text
https://zzj.abrdns.com
wss://zzj.abrdns.com/ws
```

后端容器仍然只运行 HTTP：

```text
http://127.0.0.1:8080
```

### 13.1 安装 Nginx 和 Certbot

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

### 13.2 Nginx 配置

文件路径：

```text
/etc/nginx/sites-available/koukou-api
```

内容：

```nginx
server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

启用配置：

```bash
sudo ln -s /etc/nginx/sites-available/koukou-api /etc/nginx/sites-enabled/koukou-api
sudo nginx -t
sudo systemctl reload nginx
```

### 13.3 申请 HTTPS 证书

```bash
sudo certbot --nginx -d api.example.com
```

当前服务器证书已经申请成功时，不需要重复申请；继续复用即可：

```text
/etc/letsencrypt/live/zzj.abrdns.com/fullchain.pem
/etc/letsencrypt/live/zzj.abrdns.com/privkey.pem
```

检查自动续期：

```bash
sudo certbot renew --dry-run
```

### 13.4 验证 HTTPS 和 WSS

```bash
curl https://api.example.com/health
```

WebSocket 地址：

```text
wss://api.example.com/ws?token=TOKEN
```

## 14. Android 客户端地址配置

### 14.1 Android 模拟器访问本机 Docker

```text
API_BASE_URL=http://10.0.2.2:8080
WS_BASE_URL=ws://10.0.2.2:8080/ws
```

### 14.2 真机访问局域网电脑

```text
API_BASE_URL=http://电脑局域网IP:8080
WS_BASE_URL=ws://电脑局域网IP:8080/ws
```

电脑和手机必须在同一个局域网，电脑防火墙需要允许 `8080`。

### 14.3 真机访问云服务器测试环境

```text
API_BASE_URL=http://服务器公网IP:8080
WS_BASE_URL=ws://服务器公网IP:8080/ws
```

### 14.4 真机访问生产环境

```text
API_BASE_URL=https://api.example.com
WS_BASE_URL=wss://api.example.com/ws
```

上线版本推荐只使用 HTTPS/WSS。

## 15. 数据持久化和备份设计

### 15.1 数据持久化

MySQL 数据保存在 Docker volume：

```text
koukou_mysql_data
```

查看 volume：

```bash
docker volume ls
docker volume inspect koukou-server_koukou_mysql_data
```

重建容器不会删除 volume：

```bash
docker compose down
docker compose up -d
```

删除 volume 会清空数据库：

```bash
docker compose down -v
```

生产环境不要随意执行 `docker compose down -v`。

### 15.2 数据库备份

备份：

```bash
mkdir -p backups
docker compose exec mysql mysqldump -u koukou -p koukou_im > backups/koukou_im_$(date +%F_%H%M%S).sql
```

如果需要非交互密码：

```bash
docker compose exec mysql sh -c 'mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' > backups/koukou_im_$(date +%F_%H%M%S).sql
```

恢复：

```bash
docker compose exec -T mysql mysql -u koukou -p koukou_im < backups/backup.sql
```

### 15.3 建议备份策略

测试环境：

```text
每天备份一次，保留 7 天。
```

生产环境：

```text
每天至少备份一次。
重要版本发布前手动备份一次。
备份文件同步到服务器外部存储。
定期演练恢复流程。
```

## 16. 发布和升级流程

### 16.1 常规发布

```bash
cd /opt/koukou
git pull
cd koukou-server
docker compose up -d --build
docker compose ps
docker compose logs -f koukou-server
```

### 16.2 只重启后端

```bash
docker compose restart koukou-server
```

### 16.3 重新构建后端镜像

```bash
docker compose build koukou-server
docker compose up -d koukou-server
```

### 16.4 查看当前镜像和容器

```bash
docker images
docker compose ps
```

### 16.5 清理无用镜像

```bash
docker image prune
```

不要在不理解影响的情况下执行：

```bash
docker system prune -a --volumes
```

该命令可能删除数据库 volume。

## 17. 安全设计

### 17.1 网络安全

云服务器安全组只开放：

```text
22
80
443
```

如果使用公网测试 `8080`，测试完成后关闭。

MySQL 不暴露公网：

```yaml
mysql:
  # 不配置 ports
```

### 17.2 密钥安全

不要提交：

```text
.env
真实数据库密码
真实 JWT_SECRET
服务器私钥
证书私钥
```

`.gitignore` 中应包含：

```gitignore
.env
```

### 17.3 应用安全

生产环境：

```env
DEV_AUTH_ALLOW_USER_ID=false
```

JWT 密钥要求：

```text
长度足够长。
随机生成。
不要使用 change_me、123456、项目名等弱密钥。
```

### 17.4 数据库账号

应用使用普通账号：

```text
koukou
```

不要让应用使用 `root` 账号连接数据库。

## 18. 日志和监控设计

### 18.1 查看容器日志

```bash
docker compose logs -f koukou-server
docker compose logs -f mysql
```

最近 200 行：

```bash
docker compose logs --tail=200 koukou-server
```

### 18.2 查看资源占用

```bash
docker stats
```

### 18.3 健康检查

后端健康检查：

```bash
curl http://127.0.0.1:8080/health
```

MySQL 健康检查由 Compose 内置：

```bash
docker compose ps
```

### 18.4 生产建议

后续可以扩展：

```text
Prometheus + Grafana
Loki 日志收集
告警机器人
接口错误率监控
WebSocket 在线人数监控
消息发送成功率监控
```

## 19. 故障排查

### 19.1 端口访问失败

检查容器：

```bash
docker compose ps
```

检查日志：

```bash
docker compose logs --tail=200 koukou-server
```

检查健康接口：

```bash
curl http://127.0.0.1:8080/health
```

常见原因：

- 服务监听了 `127.0.0.1` 而不是 `0.0.0.0`。
- 云服务器安全组没有开放端口。
- Nginx 没有正确转发。
- 容器没有启动成功。

### 19.2 后端连接不上 MySQL

检查 MySQL：

```bash
docker compose ps mysql
docker compose logs --tail=200 mysql
```

进入后端容器检查环境变量：

```bash
docker compose exec koukou-server env
```

常见原因：

- `MYSQL_HOST` 写成了 `127.0.0.1`，容器内应该写 `mysql`。
- 密码不一致。
- MySQL 还没有启动完成。
- 数据库 volume 已经存在，新的初始化 SQL 没有重新执行。

### 19.3 初始化 SQL 没有执行

原因：

```text
MySQL volume 已经存在时，/docker-entrypoint-initdb.d/ 下的 SQL 不会重复执行。
```

测试环境可以清空重建：

```bash
docker compose down -v
docker compose up -d
```

生产环境不要这样做。生产环境需要手动执行迁移 SQL。

### 19.4 WebSocket 连接失败

检查地址：

```text
ws://服务器IP:8080/ws?token=TOKEN
wss://api.example.com/ws?token=TOKEN
```

检查 Nginx 是否包含：

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_read_timeout 3600s;
```

常见原因：

- token 缺失或过期。
- Nginx 没有转发 Upgrade 头。
- Android 端使用了错误地址。
- HTTPS 页面或生产 App 中混用了 `ws://`，应使用 `wss://`。

### 19.5 Android 真机访问失败

检查：

- 真机不能使用 `127.0.0.1` 访问电脑服务。
- 模拟器访问电脑使用 `10.0.2.2`。
- 真机访问电脑使用电脑局域网 IP。
- 云服务器部署使用公网 IP 或域名。
- Android 9 以上如果使用 HTTP，需要允许明文流量；生产推荐 HTTPS。

## 20. 推荐落地顺序

第一阶段：本地 Docker 跑通。

```text
1. 修改 Node 监听地址为 0.0.0.0。
2. 新增 Dockerfile。
3. 新增 .dockerignore。
4. 新增 docker-compose.yml。
5. 配置 .env。
6. docker compose up -d --build。
7. curl /health 验证。
8. Android 模拟器使用 10.0.2.2 验证登录和 WebSocket。
```

第二阶段：云服务器 HTTP 测试。

```text
1. 安装 Docker。
2. 拉取项目。
3. 配置 .env。
4. docker compose up -d --build。
5. 临时开放 8080。
6. 真机访问 http://服务器公网IP:8080。
7. 验证完成后关闭 8080。
```

第三阶段：生产 HTTPS/WSS。

```text
1. 域名解析到服务器。
2. 安装 Nginx 和 Certbot。
3. 配置 Nginx 反向代理。
4. 申请 HTTPS 证书。
5. Android 切换到 https:// 和 wss://。
6. 安全组只保留 22、80、443。
```

第四阶段：运维增强。

```text
1. 增加数据库定时备份。
2. 增加日志保留策略。
3. 增加错误率和在线人数监控。
4. 建立发布前备份和回滚流程。
```

## 21. 最小可用文件清单

必须新增或调整：

```text
koukou-server/Dockerfile
koukou-server/.dockerignore
koukou-server/docker-compose.yml
koukou-server/.env
koukou-server/.env.docker
koukou-server/src/index.js
```

必须确认存在：

```text
koukou-server/package.json
koukou-server/package-lock.json
koukou-server/sql/init.sql
```

生产推荐额外配置：

```text
/etc/nginx/sites-available/koukou-api
HTTPS 证书
数据库备份脚本
```

## 22. 最终部署形态

本地测试：

```text
http://127.0.0.1:8080/health
ws://127.0.0.1:8080/ws?token=TOKEN
```

Android 模拟器：

```text
http://10.0.2.2:8080
ws://10.0.2.2:8080/ws
```

云服务器临时测试：

```text
http://服务器公网IP:8080
ws://服务器公网IP:8080/ws
```

生产环境：

```text
https://api.example.com
wss://api.example.com/ws
```

推荐结论：Koukou 即时通讯服务端可以并且应该 Docker 化。使用 Docker Compose 管理 `koukou-server + mysql` 是当前项目最合适的部署方式；生产环境再通过 Nginx 提供 HTTPS/WSS，可以兼顾部署效率、数据持久化、安全性和后续维护成本。
