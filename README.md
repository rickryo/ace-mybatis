![ace-mybatis](/ace-mybatis-logo.png?raw=true "ace-mybatis")

[![Build Status](https://travis-ci.org/vendigo/ace-mybatis.svg?branch=master)](https://travis-ci.org/vendigo/ace-mybatis)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.vendigo/ace-mybatis.svg)](http://search.maven.org/#search%7Cga%7C1%7Cace-mybatis)

This project can be described in few ways:

* Runtime generation of efficient implementation for myBatis mappers.
* Declarative support for batch operations in myBatis.
* Something like: Spring Data for myBatis.

## Contents

* [Why?](#why)
* [Features](#features)
* [Dependency](#dependency)
* [Configuration](#configuration)
* [Examples](#examples)

## Why?

Everyone who used myBatis should ask: MyBatis generates implementation for mappers out of the box,
why do we need ace-mybatis?

Short answer:

* ace-mybatis is Java 8 friendly. (It supports methods which returns Streams, Collectors, CompletableFuture)
* ace-mybatis adds declarative support for batch operations. (Standard myBatis implementations cannot be used for
inserting/updating big amount of data.)

[Read more...](docs/mybatis-batch-operations.md)

## Features

When using ace-mybatis all standard mybatis declarations are available as well as additional methods.

* [Stream select](#stream-select)
* [Batch insert/update/delete](#batch-insertupdatedelete)
* [Async batch insert/update/delete](#async-batch-insertupdatedelete)
* [Insert/update/delete collector](#insertupdatedelete-collector)

### Stream select

You can get stream directly from mapper.

```java
public interface UserMapper {
    Stream<User> selectUsers();
}
```

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.github.vendigo.acemybatis.test.app.UserMapper">
    <select id="selectUsers" resultType="com.github.vendigo.acemybatis.test.app.User">
        SELECT
        FIRST_NAME as firstName,
        LAST_NAME as lastName,
        EMAIL as email,
        PHONE_NUMBER as phoneNumber,
        CITY as city
        FROM USER
        ORDER BY EMAIL
    </select>
</mapper>
```

All data are obtained at once and stream is simply created from the result list. 

### Batch insert/update/delete

Declare insert method which accepts one parameter of type Collection (or subtypes)
or explicitly say what parameter should be batched.

```java
public interface UserMapper {
    //Parameter users will be batched. (It can be update/delete method).
    int insertUsers(List<User> users);
    //Return type can be void.
    void insertUsers2(List<User> users);
    //By default parameter with name "entities" will be batched.
    // (This param name is configurable via property listName.)
    int insertFlowUsers(@Param("entities")List<User> users, @Param("flowId")Integer flowId);
    //Explicitly say that collection should not be batched.
    @NonBatchMethod
    void insertUsers3(List<User> users);
}
```

Namespace in xml mapper should be equal to full interface name.
Method names in xml are matched with interface methods by name.

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.github.vendigo.acemybatis.test.app.UserMapper">
    <insert id="insertUsers" parameterType="com.github.vendigo.acemybatis.test.app.User">
        INSERT INTO USER (
        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        PHONE_NUMBER,
        CITY
        )
        VALUES (
        #{firstName},
        #{lastName},
        #{email},
        #{phoneNumber},
        #{city}
        )
    </insert>

    <!--By default batched element goes to map with name "entity". (It is configurable via property elementName)-->
    <insert id="insertFlowUsers" parameterType="map">
        INSERT INTO USER (
        FLOW_ID,
        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        PHONE_NUMBER,
        CITY
        )
        VALUES (
        #{flowId},
        #{entity.firstName},
        #{entity.lastName},
        #{entity.email},
        #{entity.phoneNumber}
        )
    </insert>

    <!--Without batching.-->
    <insert id="insert3" parameterType="java.util.List">
      INSERT INTO USER (
      FIRST_NAME,
      LAST_NAME,
      EMAIL,
      PHONE_NUMBER,
      CITY
      )
      VALUES (
      <foreach collection="list" item="element" separator="),(">
          #{element.firstName},
          #{element.lastName},
          #{element.email},
          #{element.phoneNumber},
          #{element.city}
      </foreach>
      )
    </insert>
</mapper>
```

### Async batch insert/update/delete

If you declare return type as CompletableFuture
method will immediately return and perform operations asynchronously.

```java
public interface UserMapper {
    //It can be CompletableFuture<Integer> as well
    CompletableFuture<Void> insertUsers(List<User> users);
    //The same works with updates and deletes
    CompletableFuture<Integer> updateUsers(List<User> users);
}

public class BusinessLogic {

    //Declarations.

    public void run() throws Exception {
        CompletableFuture<Integer> future = userMapper.insertUsers(users);
        //Some other business logic.
        future.get(); //At the end wait on all futures.
    }
}
```

### Insert/update/delete collector

This methods allow to process data in a fluent manner. It perform collection in one thread, so it can be less efficient
than regular batch insert. On the other hand it allows to process data as soon as it appears in the stream.

```java
public interface UserMapper {
    Stream<User> selectUsers();
    //It can be insert/delete methods.
    ChangeCollector<User> updateCollector();
}

public class BusinessLogic {

    //Declarations.

    public void run() throws Exception {
        List<User> updatedUser = userMapper.selectUsers()
          .map(UserProcessor::processUser)
          .filter(UserFilter::filterUser)
          .collect(userMapper.updateCollector()); //Perform update in database and collect objects to List.
        //Do something with updated users
    }
}
```

## Dependency

### Maven

```xml
<dependency>
 <groupId>com.github.vendigo</groupId>
 <artifactId>ace-mybatis</artifactId>
 <version>0.9.0</version>
</dependency>
```

### Gradle

```groovy
compile group: 'com.github.vendigo', name: 'ace-mybatis', version: '0.9.0'
```

## Configuration

There are two ways to configure ace-mybatis: declare each mapper explicitly or
setup auto discovering.

### Explicit mapper creation

```java
@Configuration
class SpringConfig {
@Bean
public UserMapper userMapper(SqlSessionFactory sqlSessionFactory) {
        return AceMapperFactory.<UserMapper>builder()
                .mapperInterface(UserMapper.class)
                .sqlSessionFactory(sqlSessionFactory)
                .build();
    }
}
```

### Auto discovering of annotated interfaces

```java
@AceMapper
public interface UserMapper {
    Stream<User> selectUsers();
}

@Configuration
class SpringConfig {
@Bean
    public AceMapperScannerConfigurer mapperScannerConfigurer() {
        return AceMapperScannerConfigurer.builder()
                .basePackage("com.github.vendigo.acemybatis.test.app")
                .build();
    }
}
```

When using more than one sqlSessionFactory, bean name should be specified in the AceMapper annotation.

```java
@AceMapper(sqlSessionFactoryBeanName = "firstSqlSessionFactory")
public interface UserMapper {
    Stream<User> selectUsers();
}

@AceMapper(sqlSessionFactoryBeanName = "secondSqlSessionFactory")
public interface ClientMapper {
    Stream<Client> selectClients();
}
```

### Additional settings

Behaviour is configurable via additional properties:

* changeChunkSize - chunk size for batch insert/update/delete methods. 2000 by default.
* threadCount - thread count for batch methods.
Computed based on available processors if not specified. Automatic set to 1 for small amount of data.
* listName - parameter name for batched list ("entities" by default).
* elementName - parameter name for batched item (entity by default).

All this properties can be specified independently on AceMapperFactory/AceMapperScannerConfigurer builders
or extracted to separate bean AceConfig

```java
@Configuration
class SpringConfig {
    //E.g. in scanner configurer
    @Bean
    public AceMapperScannerConfigurer mapperScannerConfigurer() {
        return AceMapperScannerConfigurer.builder()
                .basePackage("com.github.vendigo.acemybatis.test.app")
                .changeChunkSize(1000)
                .elementName("e")
                .build();
    }

    //Or for specific mapper
    @Bean
    public UserMapper userMapper(SqlSessionFactory sqlSessionFactory) {
            return AceMapperFactory.<UserMapper>builder()
                    .mapperInterface(UserMapper.class)
                    .sqlSessionFactory(sqlSessionFactory)
                    .changeChunkSize(2000)
                    .build();
        }

    //Config as separate bean
    @Bean
    public AceConfig aceConfig() {
        AceConfig aceConfig = new AceConfig();
        aceConfig.setThreadCount(2);
        aceConfig.setChangeChunkSize(3000);
    }

    @Bean
    public UserMapper userMapper(SqlSessionFactory sqlSessionFactory) {
            return AceMapperFactory.<UserMapper>builder()
                    .mapperInterface(UserMapper.class)
                    .sqlSessionFactory(sqlSessionFactory)
                    .config(aceConfig()) //use config bean
                    .elementName("e") //plus additional properties
                    .setChangeChunkSize(1500) //override some values from aceConfig() bean
                    .build();
        }
    }
```

## Examples

Examples can be found in [tests](src/test)
or in companion repository [ace-mybatis-examples](https://github.com/vendigo/ace-mybatis-examples).

Comments and pull requests are welcome.