# 03 — Maven 多模块项目管理

> **前置要求**：已完成 [02-项目结构导览](02-project-structure-tour.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

你打开 Lumina 项目，看到一堆 `pom.xml` 文件——根目录一个，每个子模块各一个。这些文件到底在说什么？为什么模块之间能找到彼此？为什么改了 common 模块，agent 模块也能用上新代码？

这节用大白话讲清 Maven 多模块项目怎么运作。**看懂 pom.xml 是后面所有后端教程的前提**——因为每加一个依赖、每改一个版本，你都要和它打交道。

---

## Maven 是什么？先建立直觉

如果你用过前端的 npm，那 Maven 就是 Java 版的 npm：

| 概念 | npm（前端） | Maven（Java） |
|------|------------|--------------|
| 配置文件 | `package.json` | `pom.xml` |
| 依赖仓库 | npm registry | Maven Central（中央仓库） |
| 安装依赖 | `npm install` | `mvn install` |
| 运行 | `npm run dev` | `mvn spring-boot:run` |
| 打包 | `npm run build` | `mvn package` |

**一句话**：Maven 帮你下载第三方库、管理版本、编译代码、打包成 jar。

---

## pom.xml 的核心结构

Lumina 根目录的 `pom.xml` 是"大总管"，管所有子模块。我们拆开看它最重要的几部分。

### 1. 项目坐标（它是谁）

```xml
<groupId>io.lumina</groupId>          <!-- 组织/团队名，类似 npm 的 @scope -->
<artifactId>lumina</artifactId>       <!-- 项目名 -->
<version>1.0.0-SNAPSHOT</version>     <!-- 版本号，SNAPSHOT 表示"开发中" -->
<packaging>pom</packaging>            <!-- 打包类型：pom=不打包，只管子模块 -->
```

这三个合起来（`io.lumina:lumina:1.0.0-SNAPSHOT`）就是项目的**唯一身份证**，Maven 靠它找到你的项目。

> 💡 `packaging` 有三种常见值：
> - `pom`：不产出代码，只管理子模块（根 pom 用这个）
> - `jar`：打成 jar 包（普通模块用这个）
> - `war`：打成 war 包（传统部署到 Tomcat 用，Lumina 不用）

### 2. 声明子模块（我管谁）

```xml
<!-- 文件：pom.xml（根），第 17-24 行 -->
<modules>
    <module>lumina-common</module>
    <module>lumina-agent-core</module>
    <module>lumina-framework</module>
    <module>lumina-gateway</module>
    <module>lumina-modules</module>
    <module>lumina-standalone</module>
</modules>
```

这告诉 Maven："这个项目有 6 个子模块，编译时请一起处理。"其中 `lumina-modules` 自己也是个 pom 容器，它下面还有 3 个子模块。

### 3. 版本统一定义（避免版本地狱）

```xml
<!-- 文件：pom.xml（根），第 26-89 行 -->
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.5</spring-boot.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <redisson.version>3.24.3</redisson.version>
    <!-- ... 所有版本号都集中在这里 -->
</properties>
```

**为什么统一放这里？** 想象一下，如果 10 个模块各自写 Spring Boot 版本，有人写 3.3.5 有人写 3.2.0——依赖冲突直接爆炸。集中定义、子模块只引用不写版本号，就能保证全项目版本一致。

### 4. dependencyManagement（版本管家）

```xml
<!-- 文件：pom.xml（根），第 91 行起 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.lumina</groupId>
            <artifactId>lumina-common</artifactId>
            <version>${project.version}</version>   <!-- 用上面的统一版本 -->
        </dependency>
        <!-- Spring Boot 全家桶版本由 BOM 统一管理 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>          <!-- import = 引入一整套版本定义 -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

> ⚠️ **dependencyManagement 和 dependencies 的区别**（最常混淆）：
> - `<dependencies>` = **我真的要用这个库**（会下载、会引入）
> - `<dependencyManagement>` = **如果子模块要用这个库，版本号必须用我这里定义的**（只管版本，不实际引入）
>
> 子模块只写 groupId + artifactId，**不写 version**，版本由这里统一控制。

---

## 子模块怎么引用父模块

打开 `lumina-business-agent/pom.xml`，看它怎么"认爸爸"：

```xml
<!-- 文件：lumina-modules/lumina-business-agent/pom.xml，第 8-13 行 -->
<parent>
    <groupId>io.lumina</groupId>
    <artifactId>lumina</artifactId>              <!-- 爸爸是根 pom -->
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>   <!-- 爸爸的 pom 在上两级目录 -->
</parent>

<artifactId>lumina-business-agent</artifactId>   <!-- 我自己叫这个 -->
<packaging>jar</packaging>                        <!-- 我要打成 jar -->
```

继承父 pom 后，子模块自动获得：
- 父 pom `<properties>` 里定义的所有版本号
- 父 pom `<dependencyManagement>` 的版本约束
- 父 pom 配置的插件（如编译器、JaCoCo）

---

## 子模块怎么声明依赖

再看 `lumina-business-agent` 依赖了哪些兄弟模块和第三方库：

```xml
<!-- 文件：lumina-modules/lumina-business-agent/pom.xml，第 20-43 行 -->
<dependencies>
    <!-- 依赖兄弟模块：common -->
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-common</artifactId>
        <!-- 注意：没有 <version>！由根 pom 的 dependencyManagement 控制 -->
    </dependency>

    <!-- 依赖兄弟模块：framework -->
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-framework</artifactId>
    </dependency>

    <!-- 依赖兄弟模块：agent-core -->
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-agent-core</artifactId>
    </dependency>

    <!-- 第三方库：Spring Boot Web（版本由 spring-boot-dependencies BOM 管） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <!-- 同样不写 version -->
    </dependency>
</dependencies>
```

**关键规律**：子模块的 `<dependency>` 里**几乎从不写 version**——全由根 pom 的 `dependencyManagement` 统一管。

---

## 依赖的传递性

这是 Maven 最强大的特性之一，也是最容易踩坑的地方。

**规则**：如果 A 依赖 B，B 依赖 C，那么 **A 自动也能用 C**（传递依赖）。

在 Lumina 里的实例：
```
lumina-business-agent 依赖 lumina-agent-core
    lumina-agent-core 依赖 lumina-framework
        lumina-framework 依赖 lumina-common
            lumina-common 依赖 jackson（JSON 库）
```

所以 `lumina-business-agent` **自动获得** jackson——不用自己声明，直接 `import com.fasterxml.jackson...` 就能用。

> ⚠️ **但有代价**：如果你依赖了 `lumina-common`，你自动获得了它所有的传递依赖。当传递依赖冲突时（两个模块引入了同一个库的不同版本），Maven 用"最短路径优先"规则选择。遇到冲突时用 IDEA 的 Maven Helper 插件排查。

---

## 最常用的 Maven 命令

打开命令行，在项目根目录执行：

| 命令 | 作用 | 什么时候用 |
|------|------|-----------|
| `mvn compile` | 编译所有模块 | 改完代码想确认能编译通过 |
| `mvn compile -pl lumina-common` | 只编译指定模块 | 只改了一个模块，快速验证 |
| `mvn compile -pl lumina-modules/lumina-business-agent -am` | 编译指定模块**及其依赖** | `-am` = also make，自动先编译它依赖的模块 |
| `mvn install -DskipTests` | 编译+打包+装到本地仓库 | 第一次拉项目或改了 common 模块 |
| `mvn test` | 运行测试 | 验证代码正确性 |
| `mvn package -DskipTests` | 打成 jar 包 | 准备部署 |
| `mvn clean` | 清理编译产物 | 遇到莫名其妙的问题时，先 clean |
| `mvn spring-boot:run -pl lumina-standalone` | 启动 Spring Boot 应用 | 本地运行项目 |

> 💡 **最常用的组合**：`mvn clean compile`（清理后重新编译，排查问题的第一步）

### `-pl` 和 `-am` 参数详解

```bash
# 只编译 business-agent 这一个模块
mvn compile -pl lumina-modules/lumina-business-agent
# ↑ 如果它依赖的 common/framework 没编译过，会报错

# 编译 business-agent + 它依赖的所有模块
mvn compile -pl lumina-modules/lumina-business-agent -am
# ↑ -am (also-make) 自动先编译依赖模块，推荐！
```

---

## 在 Lumina 里长啥样：完整依赖图

用实际项目验证一下你学到的：

```
lumina (根 pom, packaging=pom)
├── lumina-common          → 无 Lumina 依赖（最底层）
├── lumina-framework       → 依赖 common
├── lumina-agent-core      → 依赖 common
├── lumina-gateway         → 依赖 common, framework
├── lumina-modules (pom)
│   ├── lumina-business-base        → 依赖 common, framework
│   ├── lumina-business-agent       → 依赖 common, framework, agent-core, notification
│   └── lumina-business-notification → 依赖 common, framework
└── lumina-standalone      → 依赖 base, agent, notification（合并三合一）
```

---

## 动手试试

1. **在项目根目录执行**：
   ```bash
   mvn compile -pl lumina-common
   ```
   看看最底层模块能不能编译通过。

2. **执行全量编译**：
   ```bash
   mvn compile -DskipTests
   ```
   第一次会下载很多依赖（配了阿里云镜像应该很快）。如果成功，说明环境没问题。

3. **打开 IDEA 的 Maven 面板**（右侧边栏），你能看到所有模块的树形结构，点开每个模块能看到它的依赖。

---

## 小结

| 你现在应该知道 | 一句话记忆 |
|---------------|-----------|
| pom.xml 是什么 | Maven 的配置文件，管依赖、版本、打包 |
| 根 pom 和子 pom 的关系 | 继承：子 pom 获得父 pom 的版本定义 |
| dependencyManagement | 只管版本不引入；dependencies 才真正引入 |
| 依赖传递性 | A 依赖 B，B 依赖 C，A 自动能用 C |
| 最常用命令 | `mvn clean compile`（编译）+ `-pl -am`（指定模块） |

---

## 下一步

下一节进入重头戏：[Spring Boot 基础](04-spring-boot-basics.md)。IoC、AOP、自动配置——这些"听起来很高深"的概念，其实用类比讲明白了很简单。

> 🚀 **现在继续**：[04 — Spring Boot 基础 →](04-spring-boot-basics.md)

---

## 自测题

1. **根 pom.xml 的 `<packaging>pom</packaging>` 是什么意思？**
   <details><summary>答案</summary>表示这个模块不产出 jar/war，只作为"容器"管理子模块。</details>

2. **子模块的 `<dependency>` 里为什么不写 `<version>`？**
   <details><summary>答案</summary>版本由根 pom 的 `<dependencyManagement>` 统一控制，避免版本冲突。</details>

3. **`mvn compile -pl lumina-modules/lumina-business-agent -am` 中，`-am` 的作用是什么？**
   <details><summary>答案</summary>also-make，自动先编译 business-agent 依赖的模块（common/framework/agent-core 等），否则会报"找不到依赖"。</details>

4. **如果 lumina-agent-core 依赖了 jackson，lumina-business-agent 依赖了 lumina-agent-core，那 business-agent 能直接用 jackson 吗？**
   <details><summary>答案</summary>能。这是 Maven 的"传递依赖"特性——依赖的依赖自动可用。</details>

---

📝 **本篇撰写期间修正的代码**：无。
