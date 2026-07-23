# 01 — 开发环境搭建

> **前置要求**：会安装软件、会配环境变量、会用命令行
> **预计阅读**：20 分钟
> **难度**：⭐☆☆☆☆

---

## 这节解决什么问题

你要学 Lumina，第一件事当然是把开发环境搭好、把项目跑起来。但"搭环境"这三个字背后其实有不少坑——版本不对、路径有空格、环境变量没生效……这节带你一步步搞定，**踩过的坑都提前告诉你**。

环境搭好后，后面所有教程你都能边看边动手。

---

## 你需要装这些东西

先看清单，心里有个数。**每个都给出"为什么需要"**，你不是在盲目安装。

| 软件 | 版本要求 | 为什么需要 |
|------|----------|-----------|
| **JDK 21+** | 必须 ≥ 21 | 后端是 Java 21 写的，低版本编译不了 |
| **Maven 3.9+** | 必须 ≥ 3.9 | Java 项目的依赖管理和构建工具 |
| **Node.js 20+** | 必须 ≥ 18（推荐 20+） | 前端项目运行环境 |
| **pnpm 8+** | 必须 ≥ 8 | 前端包管理器（比 npm 快） |
| **Docker** | 推荐 | 一键起 MySQL + Redis，省去手动装数据库 |
| **IntelliJ IDEA** | 推荐（Community 免费） | Java 开发最好用的 IDE |
| **VS Code** | 推荐 | 前端开发用，轻量好用 |
| **Git** | 必须 | 你已经在用了对吧？ |

> 💡 **小提示**：如果你不想装一堆东西，最简路径是 **JDK + Maven + Docker**——用 Docker 跑 MySQL/Redis，用 standalone 模式跑项目。Node/pnpm 等要学前端时再装。

---

## 逐步安装指南

### 1. JDK 21（后端必备）

#### 为什么是 21 而不是 8 或 17？

Lumina 用了 Java 21 的特性（虚拟线程等），所以必须 21+。Java 21 是最新的 LTS（长期支持）版本。

#### Windows

1. 去 [Adoptium（Eclipse Temurin）](https://adoptium.net/) 下载 JDK 21 的 Windows x64 `.msi` 安装包
2. 安装时勾选"设置 JAVA_HOME 环境变量"和"添加到 PATH"
3. 打开新的命令行窗口，验证：

```bash
java -version
# 应该输出类似：openjdk version "21.0.5" 2024-XX-XX
```

#### macOS

```bash
# 用 Homebrew 安装
brew install openjdk@21
# 按提示创建符号链接
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

#### Linux

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk
# 验证
java -version
```

#### ⚠️ 常见坑

- **输出版本不是 21**：说明 PATH 里有旧版 JDK 优先级更高。检查 `echo $JAVA_HOME`（Mac/Linux）或环境变量设置（Windows）
- **IDEA 里编译报错**：在 `File → Project Structure → Project SDK` 里选 21

---

### 2. Maven 3.9+（后端构建工具）

#### Maven 是什么？

一句话：**Java 世界的 npm/pip**。它帮你下载依赖库（如 Spring、MyBatis）、编译代码、打包、运行测试。项目根目录的 `pom.xml` 就是 Maven 的配置文件（相当于前端的 `package.json`）。

> 📖 Maven 的详细用法在[下一节 03-Maven 多模块](03-maven-modules.md)讲，这里先装好。

#### 安装

##### Windows

1. 去 [Maven 官网](https://maven.apache.org/download.cgi)下载 `apache-maven-3.9.x-bin.zip`
2. 解压到例如 `C:\maven`
3. 设置环境变量：
   - `MAVEN_HOME` = `C:\maven\apache-maven-3.9.x`
   - PATH 加上 `%MAVEN_HOME%\bin`
4. 验证：

```bash
mvn -version
# 应该输出 Maven 版本和 Java 版本
```

##### macOS / Linux

```bash
brew install maven    # Mac
# 或
sudo apt install maven  # Ubuntu
```

#### 配置国内镜像（强烈推荐）

在国内，Maven 默认从中央仓库下载依赖会**非常慢**。配阿里云镜像能快 10 倍：

编辑 `~/.m2/settings.xml`（没有就创建）：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun-maven</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

> ⚠️ 如果不配这个，后面 `mvn install` 可能要等几十分钟。

---

### 3. Node.js 20+ 和 pnpm（前端必备）

#### 为什么前端要装 Node？

现代前端（Vue/React）的开发模式是：用 JavaScript 写代码，然后**用 Node.js 把它编译/打包**成浏览器能跑的文件。所以开发机需要 Node 环境。

#### 安装 Node.js

去 [Node.js 官网](https://nodejs.org/) 下载 LTS 版（≥ 20），安装即可。

验证：
```bash
node -v   # v20.x.x
npm -v    # 10.x.x
```

#### 安装 pnpm

pnpm 是比 npm 更快、更省磁盘的包管理器。Lumina 前端用它。

```bash
npm install -g pnpm
pnpm -v   # 8.x.x 或 9.x.x
```

#### ⚠️ 常见坑

- **pnpm install 报权限错误**（Mac/Linux）：`sudo npm install -g pnpm`，或配置 npm 全局目录到用户空间
- **node-sass 等原生模块编译失败**：确保 Node 版本和模块兼容（Lumina 用纯 SCSS，一般没这问题）

---

### 4. Docker（推荐——能省很多事）

#### 为什么推荐 Docker？

Lumina 依赖 MySQL 和 Redis。你可以手动装这两个，但：
- MySQL 安装配置麻烦，版本可能不对
- Redis 在 Windows 上没有官方安装包

用 Docker 的话，**一条命令就能起一个 MySQL + Redis**，用完即走，不污染系统。

#### 安装

- **Windows/Mac**：安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- **Linux**：`curl -fsSL https://get.docker.com | sh`

验证：
```bash
docker --version
docker compose version
```

> 💡 **Docker 是什么？** 以后在教程里会讲。现在你只要知道：它能帮你"一键启动 MySQL 和 Redis"就行。

---

### 5. IDE 配置

#### IntelliJ IDEA（后端开发）

Community 版免费够用。Ultimate 版（学生免费）对 Spring 支持更好。

**必装插件**：
- Lombok（Lumina 大量使用 `@Data` 等 Lombok 注解）
- Maven Helper（分析依赖冲突）

**关键设置**：
- `File → Project Structure → Project SDK` 选 JDK 21
- `Settings → Build → Compiler → Java Compiler` 选 21

#### VS Code（前端开发）

安装以下扩展：
- Vue - Official（Vue 3 官方插件，原 Volar）
- ESLint
- Prettier

---

## 用 Docker 准备 MySQL 和 Redis

装好 Docker 后，起数据库就一条命令：

```bash
# MySQL 8（Lumina 要求 8.0+）
docker run -d --name lumina-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=lumina_dev \
  mysql:8.0

# Redis 7（Lumina 要求 7.0+）
docker run -d --name lumina-redis \
  -p 6379:6379 \
  redis:7-alpine redis-server --requirepass 123456
```

> ⚠️ **密码设成 123456** 是因为 Lumina 的开发默认配置用的就是 123456。后面改配置时会看到。

验证 MySQL 连通：
```bash
docker exec -it lumina-mysql mysql -uroot -p123456 -e "SHOW DATABASES;"
# 应该能看到 lumina_dev
```

验证 Redis 连通：
```bash
docker exec -it lumina-redis redis-cli -a 123456 ping
# 应该输出 PONG
```

---

## 获取一个 LLM API Key

Lumina 是 AI 平台，要调大模型 API，所以你需要一个 API Key。

**免费/低价选项**（任选一个）：

| 提供商 | 注册地址 | 免费额度 | 推荐模型 |
|--------|----------|----------|----------|
| **智谱 GLM** | https://open.bigmodel.cn | 新用户送代金券 | glm-4-flash（免费）/ glm-4 |
| **阿里 DashScope** | https://dashscope.console.aliyun.com | 新用户免费 | qwen-plus / qwen-turbo |
| **硅基流动** | https://cloud.siliconflow.cn | 新用户送额度 | 各种开源模型 |

注册后在控制台找到 API Key（形如 `sk-xxxxxxxx`），保存好。

---

## 环境验证清单

全部装完后，逐项验证：

```bash
java -version          # 21.x.x ✓
mvn -version           # 3.9.x  ✓
node -v                # 20.x.x ✓
pnpm -v                # 8.x.x  ✓
docker --version       # 任何版本 ✓
git --version          # 任何版本 ✓
```

数据库：
```bash
docker ps              # 应能看到 lumina-mysql 和 lumina-redis 在运行
```

---

## 下一步

环境全部就绪？太好了。下一节带你[逛一遍 Lumina 的项目目录结构](02-project-structure-tour.md)，先建立全局认识，再深入细节。

> 🚀 **现在继续**：[02 — 项目结构导览 →](02-project-structure-tour.md)

---

## 自测题

1. **Lumina 为什么要求 JDK 21 而不是 JDK 8？**
   <details><summary>答案</summary>用了 Java 21 的新特性（如虚拟线程），且 21 是最新 LTS 版本。</details>

2. **Maven 的 `settings.xml` 配阿里云镜像有什么用？**
   <details><summary>答案</summary>加速依赖下载。国内访问 Maven 中央仓库很慢，阿里云镜像是中央仓库的国内缓存。</details>

3. **为什么推荐用 Docker 装 MySQL 而不是手动装？**
   <details><summary>答案</summary>① 一条命令搞定，不用配置 ② 不污染系统 ③ 用完即删，随时重建 ④ 可以同时跑多个版本</details>

4. **pnpm 和 npm 有什么区别？为什么要用 pnpm？**
   <details><summary>答案</summary>pnpm 更快、更省磁盘（相同的包只存一份），Lumina 前端用 pnpm。</details>

---

📝 **本篇撰写期间修正的代码**：无（本篇为环境搭建指南，未引用项目代码）。
