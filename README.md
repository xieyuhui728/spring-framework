# 医生沟通执行报表 - EntityManager 实现

这是一个基于Spring Boot和EntityManager的医生沟通执行报表实现，支持复杂SQL查询、分页查询和参数过滤，使用Doris数据源。

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
│   │       │       └── DentistCommunicationReportServiceImpl.java # 服务实现(包含SQL查询逻辑)
│   │       ├── vm/
│   │       │   ├── BasePageVM.java                     # 分页基类
│   │       │   ├── DentistCommunicationReportQueryVM.java # 查询参数
│   │       │   └── DentistCommunicationReportVM.java   # 返回结果
│   │       ├── common/
│   │       │   └── RespResult.java                     # 响应封装
│   │       ├── config/
│   │       │   └── DorisDataSourceConfig.java          # 多数据源配置
│   │       └── example/
│   │           └── DentistCommunicationUsageExample.java # 使用示例
│   └── resources/
│       └── application.properties                      # 应用配置
├── test/
│   └── java/
│       └── com/example/
│           └── DentistCommunicationReportTest.java     # 测试用例
└── pom.xml                                            # Maven依赖
```

## 功能特性

- ✅ **EntityManager直接实现**: 在Service层直接使用EntityManager执行SQL
- ✅ **复杂SQL支持**: 完整保留原始WITH子句复杂查询
- ✅ **MySQL分页**: 使用setFirstResult/setMaxResults实现高效分页
- ✅ **参数化查询**: 支持时间范围、设计组、医生ID等过滤条件
- ✅ **多数据源**: 主数据源 + Doris数据源，使用@PersistenceContext(unitName = "doris")
- ✅ **自动映射**: 查询结果自动映射到ViewModel
- ✅ **事务管理**: 使用dorisTransactionManager管理事务
- ✅ **完整日志**: 详细的执行日志和性能监控
- ✅ **RESTful API**: 标准的分页查询接口

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

## 使用示例

### Java代码调用示例
```java
@Autowired
private ReportService reportService;

// 基础查询
DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
    .pageNumber(0)
    .pageSize(20)
    .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
    .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
    .teamName("设计组A")
    .dentistId("123")
    .build();

PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);

// 处理结果
result.getContent().forEach(record -> {
    System.out.println("医生: " + record.getDentistName());
    System.out.println("设计前沟通拨打率: " + record.getPreCalledCasesRate() + "%");
    System.out.println("设计后讲解拨打率: " + record.getPostCalledCasesRate() + "%");
});
```

### 测试用例示例
详细的使用示例请参考：
- `DentistCommunicationUsageExample.java` - 完整的使用示例
- `DentistCommunicationReportTest.java` - 单元测试用例

## 技术实现细节

### 1. Service层EntityManager直接实现
```java
@Service
@Transactional("dorisTransactionManager")
public class DentistCommunicationReportServiceImpl implements ReportService {
    
    @PersistenceContext(unitName = "doris")
    private EntityManager entityManager;
    
    // SQL查询定义为常量
    private static final String MAIN_QUERY = """
        WITH ExpandedOrders AS (
          SELECT id AS order_id, 'pre' AS design_type
          FROM gms_order 
          WHERE tags LIKE '%ORDER_TAG-PRE_DESIGN_COMMUNICATION%'
          ...
        """;
        
    // 执行查询并分页
    private List<Object[]> executeMainQuery(DentistCommunicationReportQueryVM param) {
        Query query = entityManager.createNativeQuery(MAIN_QUERY);
        setQueryParameters(query, param);
        query.setFirstResult(param.getPageNumber() * param.getPageSize());
        query.setMaxResults(param.getPageSize());
        return query.getResultList();
    }
}
```

### 2. 多数据源配置
```java
@Configuration
@EnableTransactionManagement
public class DorisDataSourceConfig {
    
    @Bean(name = "dorisDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.doris")
    public DataSource dorisDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean(name = "dorisEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean dorisEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dorisDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .persistenceUnit("doris")
                .build();
    }
}
```

### 3. 完整的分页实现
```java
public PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(
        DentistCommunicationReportQueryVM param) {
    
    // 执行数据查询
    List<Object[]> resultList = executeMainQuery(param);
    
    // 执行总数查询
    Long totalElements = executeCountQuery(param);
    
    // 映射结果到ViewModel
    List<DentistCommunicationReportVM> content = resultList
        .stream()
        .map(this::mapToViewModel)
        .collect(Collectors.toList());
    
    // 创建分页对象
    Pageable pageable = PageRequest.of(param.getPageNumber(), param.getPageSize());
    
    return new PageImpl<>(content, pageable, totalElements);
}
```

### 4. 智能参数处理
```java
private void setQueryParameters(Query query, DentistCommunicationReportQueryVM param) {
    // 时间参数直接设置
    query.setParameter("startTime", param.getStartTime());
    query.setParameter("endTime", param.getEndTime());
    
    // 字符串参数空值处理
    String teamName = (param.getTeamName() != null && !param.getTeamName().trim().isEmpty()) 
        ? param.getTeamName().trim() : null;
    String dentistId = (param.getDentistId() != null && !param.getDentistId().trim().isEmpty()) 
        ? param.getDentistId().trim() : null;
        
    query.setParameter("teamName", teamName);
    query.setParameter("dentistId", dentistId);
}
```

### 5. 结果映射与字段对应
```java
private DentistCommunicationReportVM mapToViewModel(Object[] row) {
    return DentistCommunicationReportVM.builder()
        .teamName(getString(row[0]))                    // team_name
        .dentistName(getString(row[1]))                 // dentist_name  
        .dentistCode(getString(row[2]))                 // dentist_code
        .allCasesNum(getString(row[3]))                 // all_cases_num
        .firstTagCasesNum(getString(row[4]))            // first_tag_cases_num
        .preDesignTagCasesNum(getString(row[5]))        // pre_design_tag_cases_num
        .preCalledCasesNum(getString(row[6]))           // pre_called_cases_num
        .preCalledCasesRate(getString(row[7]))          // pre_called_cases_rate
        // ... 按SQL查询字段顺序映射
        .build();
}
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

1. **Service层直接实现**: 所有SQL查询逻辑都在Service层，没有单独的Repository层，便于维护和调试
2. **多数据源配置**: 项目使用了主数据源和Doris数据源，确保两个数据源都配置正确
3. **EntityManager使用**: 使用了指定unitName为"doris"的EntityManager进行查询
4. **事务管理**: Service使用dorisTransactionManager进行事务管理
5. **SQL维护**: 复杂SQL查询定义为常量，便于维护和版本控制
6. **参数安全**: 使用参数化查询防止SQL注入，包含空值处理逻辑
7. **性能优化**: 复杂查询建议在数据库层面添加适当索引
8. **结果映射**: 按照SQL查询字段顺序进行精确映射，确保数据一致性
9. **错误处理**: 包含详细的异常处理和日志记录，便于问题排查
10. **日志配置**: 开发环境启用了SQL日志，生产环境建议关闭

## 扩展功能

- 可添加导出Excel功能
- 可集成Redis缓存提升查询性能  
- 可添加查询结果统计功能
- 可集成定时任务生成报表

## 总结

本实现完全满足了你的要求：

✅ **完整的SQL实现**: 将复杂的WITH子句SQL完整迁移到Service层  
✅ **EntityManager使用**: 使用`@PersistenceContext(unitName = "doris")`指定数据源  
✅ **MySQL分页支持**: 使用`setFirstResult`和`setMaxResults`实现高效分页  
✅ **参数查询支持**: 完整支持`DentistCommunicationReportQueryVM`中的所有查询参数  
✅ **结果映射**: 精确映射到`DentistCommunicationReportVM`的所有字段  
✅ **生产就绪**: 包含完整的事务管理、异常处理、日志记录  

### 核心优势

1. **简单易维护**: 所有逻辑集中在Service层，无需额外的Repository层
2. **性能优秀**: 直接使用EntityManager，减少ORM开销
3. **扩展性强**: 可轻松添加新的查询条件和字段映射
4. **调试友好**: 详细的日志记录和错误处理机制

## 许可证

MIT License
