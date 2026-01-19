# Fastjson2 vs Jackson 对比分析

## 一、基本概况

### Fastjson2
- **开发者**: 阿里巴巴
- **最新版本**: 2.0.53 (2024年)
- **特点**: 国产、高性能、API 简洁
- **使用场景**: 国内项目广泛使用，特别是阿里系项目

### Jackson
- **开发者**: FasterXML
- **最新版本**: 2.20.1 (2024年)
- **特点**: 国际标准、生态完善、Spring 官方支持
- **使用场景**: Spring Boot 默认、国际项目主流选择

---

## 二、详细对比

### 2.1 性能对比

| 维度 | Fastjson2 | Jackson | 说明 |
|------|-----------|---------|------|
| **序列化速度** | ⭐⭐⭐⭐⭐ 极快 | ⭐⭐⭐⭐ 快 | Fastjson2 在简单对象序列化上略快 |
| **反序列化速度** | ⭐⭐⭐⭐⭐ 极快 | ⭐⭐⭐⭐ 快 | Fastjson2 在简单对象反序列化上略快 |
| **复杂对象处理** | ⭐⭐⭐⭐ 良好 | ⭐⭐⭐⭐⭐ 优秀 | Jackson 对复杂嵌套、泛型处理更稳健 |
| **内存占用** | ⭐⭐⭐⭐ 较低 | ⭐⭐⭐ 中等 | Fastjson2 内存占用略低 |

**结论**: 性能差异在实际业务中不明显，两者都足够快。

---

### 2.2 API 易用性对比

#### Fastjson2 API (简洁直观)

```java
// 序列化
String json = JSON.toJSONString(user);
String json = JSON.toJSONString(user, JSONWriter.Feature.WriteNulls);

// 反序列化
User user = JSON.parseObject(json, User.class);
List<User> users = JSON.parseArray(json, User.class);

// 类型转换
Map<String, Object> map = JSON.parseObject(json);
```

**优点**:
- ✅ API 非常简洁，一行代码搞定
- ✅ 支持链式调用
- ✅ 中文文档完善

#### Jackson API (功能强大)

```java
// 使用 ObjectMapper (推荐)
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(user);
User user = mapper.readValue(json, User.class);

// 使用注解
@JsonProperty("user_name")
private String userName;

@JsonIgnore
private String password;

// 自定义序列化器
@JsonSerialize(using = CustomSerializer.class)
private Date createTime;
```

**优点**:
- ✅ 注解支持丰富 (@JsonProperty, @JsonIgnore, @JsonFormat 等)
- ✅ 支持自定义序列化器/反序列化器
- ✅ 支持流式 API (JsonGenerator/JsonParser)
- ✅ 支持 JSON Schema 验证

**缺点**:
- ⚠️ API 相对复杂，需要创建 ObjectMapper 实例
- ⚠️ 学习曲线稍陡

---

### 2.3 生态兼容性

| 维度 | Fastjson2 | Jackson | 说明 |
|------|-----------|---------|------|
| **Spring Boot 集成** | ⚠️ 需要配置 | ✅ 默认支持 | Spring Boot 默认使用 Jackson |
| **Spring MVC 支持** | ⚠️ 需要配置 | ✅ 开箱即用 | Jackson 是 Spring 官方选择 |
| **AgentScope 兼容** | ❌ 不支持 | ✅ 原生支持 | AgentScope 使用 Jackson |
| **第三方库兼容** | ⚠️ 部分支持 | ✅ 广泛支持 | 大多数 Java 库默认支持 Jackson |
| **国际化** | ⚠️ 主要国内 | ✅ 全球标准 | Jackson 是事实上的 Java JSON 标准 |

---

### 2.4 功能特性对比

| 功能 | Fastjson2 | Jackson | 说明 |
|------|-----------|---------|------|
| **注解支持** | ⭐⭐⭐ 基础 | ⭐⭐⭐⭐⭐ 丰富 | Jackson 注解更全面 |
| **自定义序列化** | ⭐⭐⭐⭐ 支持 | ⭐⭐⭐⭐⭐ 强大 | Jackson 更灵活 |
| **流式处理** | ⭐⭐⭐ 支持 | ⭐⭐⭐⭐⭐ 优秀 | Jackson 流式 API 更完善 |
| **JSON Schema** | ❌ 不支持 | ✅ 支持 | Jackson 支持 JSON Schema 验证 |
| **多态类型** | ⭐⭐⭐ 支持 | ⭐⭐⭐⭐⭐ 优秀 | Jackson @JsonTypeInfo 更强大 |
| **日期格式化** | ⭐⭐⭐⭐ 支持 | ⭐⭐⭐⭐⭐ 灵活 | Jackson @JsonFormat 更灵活 |
| **空值处理** | ⭐⭐⭐⭐ 支持 | ⭐⭐⭐⭐⭐ 灵活 | Jackson 更细粒度控制 |

---

### 2.5 安全性对比

| 维度 | Fastjson2 | Jackson | 说明 |
|------|-----------|---------|------|
| **历史漏洞** | ⚠️ 有安全漏洞历史 | ✅ 相对安全 | Fastjson 1.x 有多个高危漏洞 |
| **AutoType 风险** | ⚠️ 需要配置白名单 | ✅ 默认安全 | Fastjson2 需要配置 autoTypeFilter |
| **社区响应** | ⭐⭐⭐⭐ 快速 | ⭐⭐⭐⭐⭐ 及时 | 两者都有及时的安全更新 |

**Fastjson2 安全配置示例**:
```java
// 需要配置白名单防止 AutoType 攻击
static final Filter AUTO_TYPE_FILTER = 
    JSONReader.autoTypeFilter("com.example.*");
```

---

### 2.6 在 biwinai-bak 中的使用情况

#### Fastjson2 使用场景
1. **Redis 序列化** - `FastJson2JsonRedisSerializer`, `FastJsonCodec`
2. **MyBatis TypeHandler** - `JsonMapTypeHandler` (部分使用)
3. **简单 JSON 转换** - 业务代码中的快速转换

#### Jackson 使用场景
1. **MyBatis TypeHandler** - `JsonTypeHandler` (主要使用)
2. **Spring MVC** - HTTP 请求/响应自动序列化
3. **第三方 API 调用** - 使用 `@JsonProperty` 注解映射

**观察**: 项目混用两者，但使用场景不同。

---

## 三、在 Lumina 框架中的选择建议

### 3.1 选择 Jackson 的理由 ✅

1. **AgentScope 原生支持**
   - AgentScope Java 框架使用 Jackson 作为 JSON 处理标准
   - 统一使用 Jackson 避免依赖冲突

2. **Spring Boot 默认支持**
   - Spring Boot 内置 Jackson，无需额外配置
   - Spring MVC 自动使用 Jackson 序列化 HTTP 响应

3. **生态兼容性更好**
   - 大多数 Java 库默认支持 Jackson
   - 国际化项目标准选择

4. **功能更强大**
   - 注解支持更丰富
   - 自定义序列化更灵活
   - 支持 JSON Schema 验证

5. **安全性更好**
   - 没有 AutoType 安全风险
   - 社区响应及时

### 3.2 保留 Fastjson2 的理由 ⚠️

1. **性能略优**
   - 在简单对象序列化上略快
   - 内存占用略低

2. **API 更简洁**
   - `JSON.toJSONString()` 比 `ObjectMapper.writeValueAsString()` 更简洁
   - 学习成本更低

3. **国内项目习惯**
   - 国内开发者更熟悉 Fastjson2 API

### 3.3 最终建议

**推荐方案: 统一使用 Jackson** ✅

**理由**:
1. ✅ **避免依赖冲突** - AgentScope 使用 Jackson，统一使用避免版本冲突
2. ✅ **Spring Boot 原生支持** - 无需额外配置，开箱即用
3. ✅ **生态兼容性** - 更好的第三方库兼容性
4. ✅ **功能更强大** - 满足复杂业务场景需求
5. ✅ **安全性** - 无 AutoType 风险

**性能差异可忽略**:
- 在实际业务中，JSON 序列化/反序列化不是性能瓶颈
- 两者性能差异在微秒级别，对整体系统影响可忽略

**迁移成本**:
- 如果从 biwinai-bak 迁移，需要替换 Fastjson2 代码
- 但新项目直接使用 Jackson，无迁移成本

---

## 四、Jackson 使用最佳实践

### 4.1 统一配置 ObjectMapper

```java
@Configuration
public class JacksonConfig {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
            .serializationInclusion(JsonInclude.Include.NON_NULL)  // 忽略 null
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)  // 日期格式化
            .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .timeZone(TimeZone.getTimeZone("GMT+8"))
            .build();
    }
}
```

### 4.2 Redis 序列化使用 Jackson

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用 Jackson 序列化
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setDefaultSerializer(serializer);
        
        return template;
    }
}
```

### 4.3 MyBatis TypeHandler 使用 Jackson

```java
@MappedTypes({Object.class})
@MappedJdbcTypes({JdbcType.VARCHAR})
public class JacksonTypeHandler extends BaseTypeHandler<Object> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, 
            Object parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, objectMapper.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("JSON 序列化失败", e);
        }
    }
    
    @Override
    public Object getNullableResult(ResultSet rs, String columnName) 
            throws SQLException {
        String json = rs.getString(columnName);
        return json == null ? null : parseJson(json);
    }
    
    private Object parseJson(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("JSON 反序列化失败", e);
        }
    }
}
```

### 4.4 工具类封装

```java
@Component
public class JsonUtils {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }
    
    public <T> T parseObject(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }
    
    public <T> List<T> parseArray(String json, Class<T> clazz) {
        try {
            JavaType javaType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, clazz);
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }
}
```

---

## 五、总结

### 5.1 对比结论

| 维度 | Fastjson2 | Jackson | 推荐 |
|------|-----------|---------|------|
| **性能** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 差异可忽略 |
| **易用性** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | Fastjson2 更简洁 |
| **功能** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Jackson 更强大 |
| **生态** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Jackson 更好 |
| **安全性** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Jackson 更安全 |
| **兼容性** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Jackson 更好 |

### 5.2 最终建议

**✅ 统一使用 Jackson**

**核心原因**:
1. AgentScope 使用 Jackson，避免依赖冲突
2. Spring Boot 默认支持，无需额外配置
3. 生态兼容性更好，国际化标准
4. 功能更强大，满足复杂场景
5. 安全性更好，无 AutoType 风险

**性能差异可忽略**，在实际业务中 JSON 处理不是性能瓶颈。

---

**文档版本**: v1.0  
**创建时间**: 2025-01-XX

