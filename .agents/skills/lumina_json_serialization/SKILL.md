---
name: lumina_json_serialization
description: Use this skill when working with JSON serialization, creating DTOs, or configuring Jackson. This skill enforces unified use of Jackson for JSON processing, including annotations, date formatting, and null handling.
---

# Lumina JSON 序列化规范

## 功能概述

本技能包用于确保 Lumina 框架项目统一使用 Jackson 进行 JSON 序列化和反序列化，包括注解使用、日期格式化、空值处理等。

## 核心原则

1. **统一使用 Jackson** - 所有 JSON 序列化/反序列化使用 Jackson
2. **禁止使用 Fastjson2** - 项目中不再使用 Fastjson2
3. **统一配置** - 使用 Spring Boot 默认的 Jackson 配置
4. **注解优先** - 使用 Jackson 注解进行字段控制

## Jackson 基础使用

### DTO 中的注解使用

```java
@Data
public class AgentVO {
    
    private Long agentId;
    
    private String agentName;
    
    // 日期格式化
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    // 忽略字段
    @JsonIgnore
    private String internalField;
    
    // 字段重命名
    @JsonProperty("agent_type")
    private AgentType agentType;
    
    // 空值处理
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String description;
}
```

### 全局配置

```java
@Configuration
public class JacksonConfig {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 日期格式
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        
        // 空值处理
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 美化输出（开发环境）
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        return mapper;
    }
}
```

## 常用注解

### @JsonFormat

用于日期时间格式化：

```java
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
private LocalDateTime createTime;

@JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
private LocalDate createDate;
```

### @JsonIgnore

忽略字段：

```java
@JsonIgnore
private String password;

@JsonIgnore
private String internalField;
```

### @JsonProperty

字段重命名：

```java
@JsonProperty("agent_id")
private Long agentId;

@JsonProperty("agent_name")
private String agentName;
```

### @JsonInclude

空值处理：

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
private String description;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
private List<String> tags;
```

### @JsonSerialize / @JsonDeserialize

自定义序列化/反序列化：

```java
@JsonSerialize(using = CustomSerializer.class)
private CustomType customField;

@JsonDeserialize(using = CustomDeserializer.class)
private CustomType customField;
```

## 集合和 Map 处理

```java
@Data
public class AgentListVO {
    
    // List 序列化
    private List<AgentVO> agents;
    
    // Map 序列化
    @JsonFormat(with = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
    private Map<String, Object> metadata;
    
    // 空集合处理
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> tags;
}
```

## 枚举处理

```java
public enum AgentStatus {
    ACTIVE(1, "启用"),
    INACTIVE(0, "禁用");
    
    private final Integer value;
    private final String desc;
    
    // 序列化为值
    @JsonValue
    public Integer getValue() {
        return value;
    }
    
    // 反序列化从值
    @JsonCreator
    public static AgentStatus fromValue(Integer value) {
        for (AgentStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
```

## 统一响应格式

```java
@Data
public class R<T> {
    
    private Integer code;
    
    private String msg;
    
    private T data;
    
    private Long timestamp;
    
    public static <T> R<T> success(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMsg("操作成功");
        r.setData(data);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
    
    public static <T> R<T> fail(Integer code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
}
```

## 禁止事项

### ❌ 禁止使用 Fastjson2

```java
// ❌ 错误：不要使用 Fastjson2
import com.alibaba.fastjson2.JSON;
String json = JSON.toJSONString(obj);

// ✅ 正确：使用 Jackson
import com.fasterxml.jackson.databind.ObjectMapper;
String json = objectMapper.writeValueAsString(obj);
```

### ❌ 禁止在 SQL 中处理 JSON

```sql
-- ❌ 错误：不要在 SQL 中使用 JSON 函数
SELECT JSON_EXTRACT(config, '$.key') FROM lumina_agent;

-- ✅ 正确：在 Java 代码中处理 JSON
```

## 使用场景

- 创建 DTO 时，使用 Jackson 注解
- 配置全局 Jackson 时，确保统一配置
- 处理日期时间时，使用 @JsonFormat
- 代码审查时，检查是否使用 Jackson

## 检查清单

- [ ] 是否统一使用 Jackson，未使用 Fastjson2
- [ ] 日期字段是否使用 @JsonFormat 格式化
- [ ] 敏感字段是否使用 @JsonIgnore 忽略
- [ ] 空值处理是否使用 @JsonInclude
- [ ] 枚举是否使用 @JsonValue 和 @JsonCreator
- [ ] 全局配置是否统一

## 可用资源

- `references/jackson-guide.md`: Jackson 详细使用指南
- `examples/dto-annotations.java`: DTO 注解示例
- `examples/enum-serialization.java`: 枚举序列化示例
- `examples/global-config.java`: 全局配置示例

