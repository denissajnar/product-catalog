# Product Catalog Service

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Gradle](https://img.shields.io/badge/Gradle-Latest-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![SpringDoc OpenAPI](https://img.shields.io/badge/SpringDoc-2.8.13-6DB33F?logo=swagger&logoColor=white)](https://springdoc.org/)
[![GraalVM](https://img.shields.io/badge/GraalVM-Native-FF6600?logo=oracle&logoColor=white)](https://www.graalvm.org/)

A reactive microservice for managing product catalog information in an e-commerce platform, built with **Spring Boot 3.x
** and **Kotlin**.

## 🚀 Features

- **Reactive REST API** with WebFlux
- **CRUD Operations** for product management
- **CSV Import** functionality
- **Pagination & Sorting** support
- **Redis Caching** for improved performance
- **PostgreSQL Database** with R2DBC
- **API Documentation** with SpringDoc OpenAPI (Swagger UI)
- **Security** with Spring Security
- **Docker Support** with multi-stage builds
- **GraalVM Native Image** support
- **Comprehensive Testing** with JUnit 5 and MockK
- **Database Migrations** with Flyway

## 🛠️ Technology Stack

| Technology            | Version | Purpose                  |
|-----------------------|---------|--------------------------|
| **Spring Boot**       | 3.5.5   | Main framework           |
| **Kotlin**            | 2.2.20  | Programming language     |
| **Java**              | 21      | Runtime environment      |
| **Spring WebFlux**    | Latest  | Reactive web framework   |
| **Spring Data R2DBC** | Latest  | Reactive database access |
| **PostgreSQL**        | Latest  | Primary database         |
| **Redis**             | Latest  | Caching layer            |
| **SpringDoc OpenAPI** | 2.8.13  | API documentation        |
| **Flyway**            | Latest  | Database migrations      |
| **JUnit 5**           | Latest  | Testing framework        |
| **MockK**             | 1.13.8  | Mocking for Kotlin       |
| **Testcontainers**    | Latest  | Integration testing      |
| **Docker**            | Latest  | Containerization         |
| **GraalVM**           | Latest  | Native compilation       |

## 📋 API Endpoints

| Method   | Endpoint                  | Description                       |
|----------|---------------------------|-----------------------------------|
| `GET`    | `/api/v1/products`        | List all products with pagination |
| `GET`    | `/api/v1/products/{id}`   | Get product by ID                 |
| `PUT`    | `/api/v1/products/{id}`   | Update existing product           |
| `DELETE` | `/api/v1/products/{id}`   | Delete product                    |
| `POST`   | `/api/v1/products/import` | Import products from CSV file     |

### Pagination Example

```
GET /api/v1/products?page=0&size=20&sort=name,asc
```

## 🚀 Getting Started

### Prerequisites

- **Java 21+**
- **Docker & Docker Compose**
- **Gradle** (wrapper included)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd product-catalog-main
   ```

2. **Start the infrastructure**
   ```bash
   docker-compose up -d
   ```

3. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

4. **Access the API Documentation**
    - Swagger UI: http://localhost:8080/swagger-ui.html
    - OpenAPI Spec: http://localhost:8080/v3/api-docs

### Development Setup

1. **Database Setup**
   ```bash
   # Start PostgreSQL and Redis
   docker-compose up -d postgres redis
   ```

2. **Run Tests**
   ```bash
   ./gradlew test
   ```

3. **Build Application**
   ```bash
   ./gradlew build
   ```

### Docker Deployment

1. **Build Docker Image**
   ```bash
   ./gradlew bootBuildImage
   ```

2. **Run with Docker Compose**
   ```bash
   docker-compose up
   ```

### Native Image (GraalVM)

1. **Build Native Image**
   ```bash
   ./gradlew nativeCompile
   ```

2. **Build Native Docker Image**
   ```bash
   ./gradlew bootBuildImage -Pnative
   ```

## 📊 Sample Data

The project includes sample CSV data for testing the import functionality:

- Location: `src/test/resources/product_import_anonymized.csv`
- Use the `/api/v1/products/import` endpoint to import this data

## 🔧 Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
server:
  port: 8080

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/catalog
    username: catalog_user
    password: catalog_pass

  data:
    redis:
      host: localhost
      port: 6379
```

### Environment Variables

| Variable      | Description       | Default      |
|---------------|-------------------|--------------|
| `DB_HOST`     | PostgreSQL host   | localhost    |
| `DB_PORT`     | PostgreSQL port   | 5432         |
| `DB_NAME`     | Database name     | catalog      |
| `DB_USERNAME` | Database username | catalog_user |
| `DB_PASSWORD` | Database password | catalog_pass |
| `REDIS_HOST`  | Redis host        | localhost    |
| `REDIS_PORT`  | Redis port        | 6379         |

## 📈 Monitoring & Health

- **Health Check**: `/actuator/health`
- **Metrics**: `/actuator/metrics`
- **Info**: `/actuator/info`

## 🧪 Testing

The project includes comprehensive test coverage:

- **Unit Tests**: Service layer and business logic
- **Integration Tests**: Controller and repository layers
- **Testcontainers**: Database integration testing

```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Write tests for new functionality
- Update documentation as needed

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🏗️ Architecture

This service follows a clean architecture pattern:

```
┌─────────────────┐
│   Controllers   │  ← REST API Layer
├─────────────────┤
│    Services     │  ← Business Logic Layer
├─────────────────┤
│  Repositories   │  ← Data Access Layer
├─────────────────┤
│    Database     │  ← PostgreSQL + Redis
└─────────────────┘
```

## 🔍 Performance Features

- **Reactive Programming**: Non-blocking I/O with WebFlux
- **Connection Pooling**: Optimized database connections
- **Caching**: Redis-based caching for frequently accessed data
- **Native Image**: Fast startup and low memory footprint with GraalVM

---

**Happy coding! 🚀**
