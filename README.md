# Mirror App

Projeto base Spring Boot.

## Stack

- **Java 21**
- **Spring Boot 3.4.13** (Web, Data JPA, Validation, Actuator, DevTools)
- **Maven**
- **H2** (banco em memória, modo PostgreSQL)
- **springdoc-openapi 2.8.17** (Swagger UI)
- **Lombok**

> Atenção: Spring Boot 3.4.x atingiu **End of Life (OSS)** em 31/dez/2025.
> Para produção, considere migrar para `3.5.x` ou `4.0.x`.

## Estrutura

```
mirror-app/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/br/com/cezarcirqueira/mirror/app/
│   │   │   ├── MirrorAppApplication.java
│   │   │   └── config/
│   │   │       └── OpenApiConfig.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/br/com/cezarcirqueira/mirror/app/
│           └── MirrorAppApplicationTests.java
└── README.md
```

## Como rodar

```bash
mvn spring-boot:run
```

Ou empacotando:

```bash
mvn clean package
java -jar target/mirror-app-0.0.1-SNAPSHOT.jar
```

## URLs úteis

| Recurso        | URL                                            |
| -------------- | ---------------------------------------------- |
| Aplicação      | http://localhost:8080                          |
| Swagger UI     | http://localhost:8080/swagger-ui.html          |
| OpenAPI JSON   | http://localhost:8080/v3/api-docs              |
| H2 Console     | http://localhost:8080/h2-console               |
| Actuator       | http://localhost:8080/actuator                 |
| Health         | http://localhost:8080/actuator/health          |

### Conexão H2

- **JDBC URL:** `jdbc:h2:mem:mirrordb`
- **User:** `sa`
- **Password:** *(vazio)*

## Testes

```bash
mvn test
```
