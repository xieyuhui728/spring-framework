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

- ✅ 支持复杂SQL查询转换为Spring JPA实现
- ✅ 支持分页查询 (PageRequest/Pageable)
- ✅ 支持参数化查询 (时间范围、设计组、医生ID)
- ✅ 原生SQL查询 (@Query with nativeQuery = true)
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
spring.datasource.url=jdbc:mysql://localhost:3306/your_database_name
spring.datasource.username=your_username
spring.datasource.password=your_password
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

### 1. 原生SQL查询
- 使用 `@Query(nativeQuery = true)` 支持复杂的WITH子句
- 包含完整的countQuery用于分页计算
- 参数化查询防止SQL注入

### 2. 结果映射
```java
private DentistCommunicationReportVM mapToViewModel(Object[] row) {
    return DentistCommunicationReportVM.builder()
        .teamName(getString(row[0]))
        .dentistName(getString(row[1]))
        // ... 按顺序映射所有字段
        .build();
}
```

### 3. 分页支持
```java
Pageable pageable = PageRequest.of(param.getPageNumber(), param.getPageSize());
Page<Object[]> resultPage = repository.findDentistCommunicationReport(..., pageable);
```

### 4. 参数绑定
```sql
WHERE (:startTime IS NULL OR gd.send_out >= :startTime)
  AND (:endTime IS NULL OR gd.send_out <= :endTime)
  AND (:teamName IS NULL OR gms_team.name = :teamName)
  AND (:dentistId IS NULL OR gms_dentist.id = :dentistId)
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

1. **性能优化**: 复杂查询建议在数据库层面添加适当索引
2. **参数验证**: 可在ViewModel中添加 `@Valid` 注解进行参数校验
3. **异常处理**: 已包含基本异常处理，可根据需要扩展
4. **日志配置**: 开发环境启用了SQL日志，生产环境建议关闭

## 扩展功能

- 可添加导出Excel功能
- 可集成Redis缓存提升查询性能  
- 可添加查询结果统计功能
- 可集成定时任务生成报表

## 许可证

MIT License
