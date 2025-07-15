# 医生沟通执行报表 - Spring JPA 实现

这是一个基于Spring Boot和JPA的医生沟通执行报表实现，支持分页查询和参数过滤。

## 项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/example/
│   │       ├── DentistReportApplication.java           # 主应用类
│   │       ├── controller/
│   │       │   └── ReportController.java               # REST控制器
│   │       ├── service/
│   │       │   ├── ReportService.java                  # 服务接口
│   │       │   └── impl/
│   │       │       └── DentistCommunicationReportServiceImpl.java # 服务实现
│   │       ├── repository/
│   │       │   └── DentistCommunicationReportRepository.java # JPA Repository
│   │       ├── vm/
│   │       │   ├── BasePageVM.java                     # 分页基类
│   │       │   ├── DentistCommunicationReportQueryVM.java # 查询参数
│   │       │   └── DentistCommunicationReportVM.java   # 返回结果
│   │       ├── common/
│   │       │   └── RespResult.java                     # 响应封装
│   │       └── config/
│   │           └── JpaConfig.java                      # JPA配置
│   └── resources/
│       └── application.properties                      # 应用配置
└── pom.xml                                            # Maven依赖
```

## 功能特性

- ✅ 支持复杂SQL查询转换为EntityManager实现
- ✅ 支持分页查询 (手动分页实现)
- ✅ 支持参数化查询 (时间范围、设计组、医生ID)
- ✅ 原生SQL查询 (EntityManager.createNativeQuery)
- ✅ 多数据源支持 (主数据源 + Doris数据源)
- ✅ 自动结果映射到ViewModel
- ✅ 完整的错误处理和日志记录
- ✅ RESTful API设计

## 主要接口

### POST /api/report/dentistCommunicationReport

**请求参数 (DentistCommunicationReportQueryVM):**
```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "startTime": "2024-01-01T00:00:00Z",
  "endTime": "2024-12-31T23:59:59Z",
  "teamName": "设计组A",
  "dentistId": "123"
}
```

**响应结果:**
```json
{
  "success": true,
  "message": "Success",
  "code": "200",
  "data": {
    "content": [
      {
        "teamName": "设计组A",
        "dentistName": "张医生",
        "dentistCode": "DOC001",
        "allCasesNum": "100",
        "firstTagCasesNum": "5",
        "preDesignTagCasesNum": "80",
        "preCalledCasesNum": "75",
        "preCalledCasesRate": "93.75",
        // ... 更多字段
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "totalElements": 150,
    "totalPages": 8
  }
}
```

## 快速开始

### 1. 环境要求
- Java 8+
- Maven 3.6+
- MySQL 5.7+

### 2. 配置数据库
修改 `src/main/resources/application.properties`:
```properties
# 主数据源配置
spring.datasource.url=jdbc:mysql://localhost:3306/primary_database
spring.datasource.username=primary_username
spring.datasource.password=primary_password

# Doris数据源配置 (用于报表查询)
spring.datasource.doris.url=jdbc:mysql://doris-host:9030/doris_database
spring.datasource.doris.username=doris_username
spring.datasource.doris.password=doris_password
```

### 3. 运行应用
```bash
mvn spring-boot:run
```

### 4. 测试API
访问: http://localhost:8080/swagger-ui.html 查看API文档

或使用curl测试:
```bash
curl -X POST http://localhost:8080/api/report/dentistCommunicationReport \
  -H "Content-Type: application/json" \
  -d '{
    "pageNumber": 0,
    "pageSize": 10,
    "startTime": "2024-01-01T00:00:00Z",
    "endTime": "2024-12-31T23:59:59Z"
  }'
```

## 技术实现细节

### 1. EntityManager原生SQL查询
```java
@PersistenceContext(unitName = "doris")
private EntityManager entityManager;

Query query = entityManager.createNativeQuery(MAIN_QUERY);
query.setFirstResult(pageNumber * pageSize);
query.setMaxResults(pageSize);
return query.getResultList();
```

### 2. 多数据源配置
```java
@Bean(name = "dorisEntityManagerFactory")
public LocalContainerEntityManagerFactoryBean dorisEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("dorisDataSource") DataSource dataSource) {
    return builder
            .dataSource(dataSource)
            .persistenceUnit("doris")
            .build();
}
```

### 3. 结果映射
```java
private DentistCommunicationReportVM mapToViewModel(Object[] row) {
    return DentistCommunicationReportVM.builder()
        .teamName(getString(row[0]))
        .dentistName(getString(row[1]))
        // ... 按顺序映射所有字段
        .build();
}
```

### 4. 手动分页实现
```java
// 数据查询
List<Object[]> resultList = repository.findDentistCommunicationReport(..., pageNumber, pageSize);
// 总数查询
Long totalElements = repository.countDentistCommunicationReport(...);
// 构建分页结果
return new PageImpl<>(content, pageable, totalElements);
```

### 5. 参数绑定
```java
query.setParameter("startTime", startTime);
query.setParameter("endTime", endTime);
query.setParameter("teamName", teamName);
query.setParameter("dentistId", dentistId);
```

## 数据库表结构要求

此实现需要以下表存在于数据库中：
- `gms_order` - 订单表
- `gms_design` - 设计表  
- `gms_case` - 病例表
- `gms_dentist` - 医生表
- `gms_team` - 设计组表
- `gms_task` - 任务表
- `gms_task_type` - 任务类型表
- `gms_order_case_related_detail` - 订单病例关联表
- `gms_order_case_stakeholder_detail` - 订单病例利益相关者表
- `tt_order_call_log` - 通话记录表

## 注意事项

1. **多数据源配置**: 项目使用了主数据源和Doris数据源，确保两个数据源都配置正确
2. **EntityManager使用**: 使用了指定unitName为"doris"的EntityManager进行查询
3. **事务管理**: Repository使用dorisTransactionManager进行事务管理
4. **性能优化**: 复杂查询建议在数据库层面添加适当索引
5. **参数验证**: 可在ViewModel中添加 `@Valid` 注解进行参数校验
6. **异常处理**: 已包含基本异常处理，可根据需要扩展
7. **日志配置**: 开发环境启用了SQL日志，生产环境建议关闭

## 扩展功能

- 可添加导出Excel功能
- 可集成Redis缓存提升查询性能  
- 可添加查询结果统计功能
- 可集成定时任务生成报表

## 许可证

MIT License
