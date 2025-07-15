# 医生沟通报表 - EntityManager + LIMIT OFFSET 实现

## 概述

本实现提供了一个完整的医生沟通报表查询解决方案，使用 Spring JPA 的 EntityManager 执行复杂 SQL 查询，支持 MySQL 的 LIMIT OFFSET 分页方式和 null 参数处理。

## 核心特性

### 🚀 EntityManager 实现
- 使用 `@PersistenceContext(unitName = "doris")` 指定数据源
- 直接执行原生 SQL 查询，保持复杂业务逻辑的完整性
- 支持事务管理 `@Transactional("dorisTransactionManager")`

### 📄 LIMIT OFFSET 分页
- 在 SQL 中使用 `LIMIT :limit OFFSET :offset` 进行分页
- 支持大数据量的高效分页查询
- 分离主查询和计数查询，优化性能

### 🔧 Null 参数支持
- 所有查询参数支持 null 值
- 智能处理空字符串和空格，自动转换为 null
- WHERE 子句中使用 `IS NULL` 条件进行安全过滤

### 🛡️ 安全特性
- 参数化查询防止 SQL 注入
- 全面的参数验证和错误处理
- 类型安全的结果映射

## 文件结构

```
src/
├── main/java/com/example/
│   ├── service/impl/
│   │   └── DentistCommunicationReportServiceImpl.java    # 核心实现
│   ├── vm/
│   │   ├── DentistCommunicationReportQueryVM.java        # 查询参数
│   │   └── DentistCommunicationReportVM.java            # 结果映射
│   └── example/
│       └── DentistCommunicationUsageExample.java        # 使用示例
└── test/java/com/example/
    └── DentistCommunicationReportServiceTest.java       # 测试类
```

## 关键实现细节

### 1. SQL 查询模板

```sql
-- 主查询 (MAIN_QUERY_TEMPLATE)
WITH ExpandedOrders AS (...)
...
ORDER BY gms_dentist.id
LIMIT :limit OFFSET :offset

-- 计数查询 (COUNT_QUERY)  
WITH ExpandedOrders AS (...)
...
SELECT COUNT(DISTINCT gms_dentist.id)
```

### 2. 分页参数计算

```java
// 计算LIMIT和OFFSET
int limit = param.getPageSize();
int offset = param.getPageNumber() * param.getPageSize();

// 设置分页参数
query.setParameter("limit", limit);
query.setParameter("offset", offset);
```

### 3. Null 参数处理

```java
private String normalizeStringParam(String param) {
    if (param == null) {
        return null;
    }
    String trimmed = param.trim();
    return trimmed.isEmpty() ? null : trimmed;
}
```

### 4. 结果映射

```java
// 按照SQL查询结果的字段顺序进行精确映射
private DentistCommunicationReportVM mapToViewModel(Object[] row) {
    return DentistCommunicationReportVM.builder()
        .teamName(safeToString(row[0]))        // team_name
        .dentistName(safeToString(row[1]))     // dentist_name  
        .dentistCode(safeToString(row[2]))     // dentist_code
        // ... 共23个字段的精确映射
        .build();
}
```

## API 使用

### 基础查询（所有参数为null）

```java
DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
    .pageNumber(0)
    .pageSize(10)
    // startTime, endTime, teamName, dentistId 都为null
    .build();

PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
```

### 时间范围查询

```java
DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
    .pageNumber(0)
    .pageSize(20)
    .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
    .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
    // teamName 和 dentistId 为null
    .build();
```

### 完整参数查询

```java
DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
    .pageNumber(0)
    .pageSize(10)
    .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
    .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
    .teamName("设计组A")
    .dentistId("123")
    .build();
```

## 查询结果字段

查询返回 23 个字段，包括：

### 基础信息
- `teamName` - 设计组
- `dentistName` - 医生姓名  
- `dentistCode` - 医生编号
- `allCasesNum` - 总病例数
- `firstTagCasesNum` - 新手首例病例数

### 设计前沟通指标
- `preDesignTagCasesNum` - 设计前沟通病例数
- `preCalledCasesNum` - 设计前沟通拨打电话病例数
- `preCalledCasesRate` - 设计前沟通拨打率(%)
- `preConnectedCasesNum` - 设计前沟通接通电话病例数
- `preConnectedCasesRate` - 设计前沟通接通率(%)
- `preTotalConnectedCallNum` - 设计前沟通接通电话通话数
- `preAverageConnectedCallNum` - 设计前沟通例均通话次数
- `preTotalDurationSec` - 设计前沟通接通电话时长(HH:MM:SS)
- `preAverageDurationSec` - 设计前沟通例均通话时长(HH:MM:SS)

### 设计后讲解指标  
- `postDesignTagCasesNum` - 设计后讲解病例数
- `postCalledCasesNum` - 设计后讲解拨打电话病例数
- `postCalledCasesRate` - 设计后讲解拨打率(%)
- `postConnectedCasesNum` - 设计后讲解接通电话病例数
- `postConnectedCasesRate` - 设计后讲解接通率(%)
- `postTotalConnectedCallNum` - 设计后讲解接通电话通话数
- `postAverageConnectedCallNum` - 设计后讲解例均通话次数
- `postTotalDurationSec` - 设计后讲解接通电话时长(HH:MM:SS)
- `postAverageDurationSec` - 设计后讲解例均通话时长(HH:MM:SS)

## 性能特性

### LIMIT OFFSET 分页优势
- ✅ 直接在数据库层面限制返回记录数
- ✅ 减少网络传输和内存占用
- ✅ 支持大数据量分页查询
- ✅ MySQL 原生支持，性能优化

### 分页性能测试
```java
// 大OFFSET测试 - 页码20，页大小10 => OFFSET=200
DentistCommunicationReportQueryVM largeOffsetQuery = DentistCommunicationReportQueryVM.builder()
    .pageNumber(20)  // OFFSET = 20 * 10 = 200
    .pageSize(10)
    .build();
```

## 安全性保障

### SQL 注入防护
- 使用参数化查询 `query.setParameter()`
- 所有用户输入都经过参数绑定
- 字符串参数标准化处理

### 参数验证
```java
// 页码验证
if (param.getPageNumber() == null || param.getPageNumber() < 0) {
    throw new IllegalArgumentException("页码不能为空且不能小于0");
}

// 页大小验证  
if (param.getPageSize() == null || param.getPageSize() <= 0 || param.getPageSize() > 1000) {
    throw new IllegalArgumentException("页大小不能为空且必须在1-1000之间");
}

// 时间范围验证
if (param.getStartTime() != null && param.getEndTime() != null) {
    if (param.getStartTime().isAfter(param.getEndTime())) {
        throw new IllegalArgumentException("开始时间不能大于结束时间");
    }
}
```

## 测试覆盖

### 核心测试用例
- ✅ `testBasicQueryWithNullParams()` - 基础查询测试
- ✅ `testAllNullParametersQuery()` - 全null参数测试
- ✅ `testEmptyStringParametersQuery()` - 空字符串参数测试
- ✅ `testLimitOffsetPagination()` - LIMIT OFFSET分页测试
- ✅ `testLargeOffsetPagination()` - 大OFFSET性能测试
- ✅ `testParameterValidation()` - 参数验证测试
- ✅ `testTimeRangeValidation()` - 时间范围验证测试
- ✅ `testSqlInjectionProtection()` - SQL注入防护测试

### 运行测试
```bash
mvn test -Dtest=DentistCommunicationReportServiceTest
```

## 使用示例

### 运行所有示例
```java
@Autowired
private DentistCommunicationUsageExample usageExample;

public void demo() {
    usageExample.runAllExamples();
}
```

### 示例包含
- 基础查询示例（null参数支持）
- 时间范围查询示例  
- 空字符串参数测试
- LIMIT OFFSET分页测试
- 完整参数查询示例
- 大分页性能测试
- SQL注入防护测试

## 错误处理

### 常见异常
- `IllegalArgumentException` - 参数验证失败
- `RuntimeException` - 查询执行失败
- `PersistenceException` - 数据库连接或SQL执行异常

### 日志记录
```java
log.info("查询完成，总记录数: {}, 当前页记录数: {}", totalElements, resultList.size());
log.debug("分页参数 - LIMIT: {}, OFFSET: {}", limit, offset);
log.error("执行医生沟通报表查询失败，参数: {}", param, e);
```

## 配置要求

### 数据源配置
```java
@PersistenceContext(unitName = "doris")
private EntityManager entityManager;

@Transactional("dorisTransactionManager")
public PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(...)
```

### 依赖项
- Spring Boot Starter Data JPA
- MySQL Connector
- Spring Boot Starter Web
- Lombok
- JUnit 5

## 最佳实践

### 1. 分页参数设置
- 页大小建议控制在 10-100 之间
- 避免过大的 OFFSET 值（> 10000）
- 对于大数据量查询，考虑基于游标的分页

### 2. 参数处理
- 始终验证输入参数
- 支持 null 值查询，提高查询灵活性
- 标准化字符串参数，去除空格和空值

### 3. 性能优化
- 分离数据查询和计数查询
- 在关键字段上建立索引
- 监控慢查询并优化 SQL

### 4. 错误处理
- 提供详细的错误信息
- 记录完整的操作日志
- 区分业务异常和系统异常

## 总结

这个实现提供了：
- ✅ 完整的 EntityManager + LIMIT OFFSET 分页方案
- ✅ 全面的 null 参数支持
- ✅ 安全的 SQL 执行和参数绑定
- ✅ 详细的测试覆盖和使用示例
- ✅ 生产级别的错误处理和日志记录

适用于需要执行复杂 SQL 查询、支持灵活参数过滤、高性能分页的报表系统。