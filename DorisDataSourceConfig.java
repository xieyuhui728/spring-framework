package com.example.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.example.repository",
    entityManagerFactoryRef = "dorisEntityManagerFactory",
    transactionManagerRef = "dorisTransactionManager"
)
public class DorisDataSourceConfig {

    /**
     * 主数据源配置
     */
    @Primary
    @Bean(name = "primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Doris数据源配置
     */
    @Bean(name = "dorisDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.doris")
    public DataSource dorisDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 主EntityManagerFactory
     */
    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");
        
        return builder
                .dataSource(dataSource)
                .packages("com.example.entity") // 如果有实体类的话
                .persistenceUnit("primary")
                .properties(properties)
                .build();
    }

    /**
     * Doris EntityManagerFactory
     */
    @Bean(name = "dorisEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean dorisEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dorisDataSource") DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");
        
        return builder
                .dataSource(dataSource)
                .packages("com.example.entity") // 如果有实体类的话
                .persistenceUnit("doris")
                .properties(properties)
                .build();
    }

    /**
     * 主事务管理器
     */
    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * Doris事务管理器
     */
    @Bean(name = "dorisTransactionManager")
    public PlatformTransactionManager dorisTransactionManager(
            @Qualifier("dorisEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}