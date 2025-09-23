# Product Catalog Service

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Gradle](https://img.shields.io/badge/Gradle-Latest-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![SpringDoc OpenAPI](https://img.shields.io/badge/SpringDoc-2.8.13-6DB33F?logo=swagger&logoColor=white)](https://springdoc.org/)
[![GraalVM](https://img.shields.io/badge/GraalVM-Native-FF6600?logo=oracle&logoColor=white)](https://www.graalvm.org/)

A REST API service for managing product catalog information in an e-commerce platform, built with **Spring Boot 3.x**
and **Kotlin**.

## 🚀 Features

- **REST API** with Spring MVC
- **CRUD Operations** for product management
- **CSV Import** functionality with batch processing
- **Pagination & Sorting** support
- **Redis Caching** for improved performance
- **PostgreSQL Database** with JPA/Hibernate
- **API Documentation** with SpringDoc OpenAPI (Swagger UI)
- **Security** with Spring Security and role-based access control
- **Docker Support** with multi-stage builds
- **GraalVM Native Image** support
- **Comprehensive Testing** with JUnit 5 and MockK
- **Database Migrations** with Flyway

## 🛠️ Technology Stack

| Technology            | Version | Purpose               |
|-----------------------|---------|-----------------------|
| **Spring Boot**       | 3.5.5   | Main framework        |
| **Kotlin**            | 2.2.20  | Programming language  |
| **Java**              | 21      | Runtime environment   |
| **Spring Web**        | Latest  | Web framework         |
| **Spring Data JPA**   | Latest  | Database access layer |
| **PostgreSQL**        | Latest  | Primary database      |
| **Redis**             | Latest  | Caching layer         |
| **SpringDoc OpenAPI** | 2.8.13  | API documentation     |
| **Flyway**            | Latest  | Database migrations   |
| **JUnit 5**           | Latest  | Testing framework     |
| **MockK**             | 1.13.8  | Mocking for Kotlin    |
| **Testcontainers**    | Latest  | Integration testing   |
| **Docker**            | Latest  | Containerization      |
| **GraalVM**           | Latest  | Native compilation    |

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
GET /api/v1/products?page=0&size=20&sort=longName,asc
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

2. **Run Native Executable**
   ```bash
   ./build/native/nativeCompile/com.albert.product-catalog-main
   ```

## 🔧 Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/catalog
    username: catalog_user
    password: catalog_pass

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration

  data:
    redis:
      host: localhost
      port: 6379

  cache:
    type: redis
    redis:
      time-to-live: 3600000 # 1 hour
```

### Environment Variables

| Variable      | Description       | Default Value  |
|---------------|-------------------|----------------|
| `SERVER_PORT` | Application port  | `8080`         |
| `DB_HOST`     | Database host     | `localhost`    |
| `DB_PORT`     | Database port     | `5432`         |
| `DB_NAME`     | Database name     | `catalog`      |
| `DB_USERNAME` | Database username | `catalog_user` |
| `DB_PASSWORD` | Database password | `catalog_pass` |
| `REDIS_HOST`  | Redis host        | `localhost`    |
| `REDIS_PORT`  | Redis port        | `6379`         |

## 📊 API Usage Examples

### Get All Products with Pagination

```bash
curl -X GET "http://localhost:8080/api/v1/products?page=0&size=10&sort=longName,asc" \
  -H "Authorization: Bearer <your-token>"
```

### Get Product by ID

```bash
curl -X GET "http://localhost:8080/api/v1/products/1" \
  -H "Authorization: Bearer <your-token>"
```

### Update Product

```bash
curl -X PUT "http://localhost:8080/api/v1/products/1" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "goldId": "GOLD123",
    "longName": "Updated Product Name",
    "shortName": "Updated Short",
    "iowUnitType": "EACH",
    "healthyCategory": "HEALTHY"
  }'
```

### Import Products from CSV

```bash
curl -X POST "http://localhost:8080/api/v1/products/import" \
  -H "Authorization: Bearer <your-token>" \
  -F "file=@products.csv"
```

### CSV Format Example

```csv
uuid,gold_id,long_name,short_name,iow_unit_type,healthy_category
550e8400-e29b-41d4-a716-446655440000,GOLD001,Product Long Name,Short Name,EACH,HEALTHY
550e8400-e29b-41d4-a716-446655440001,GOLD002,Another Product,Another,KG,UNHEALTHY
```

## 🧪 Testing

### Run All Tests

```bash
./gradlew test
```

### Run Specific Test Class

```bash
./gradlew test --tests "ProductControllerTest"
```

### Integration Tests

The project uses Testcontainers for integration testing with real PostgreSQL and Redis instances.

```bash
./gradlew integrationTest
```

## 📈 Performance

### Caching Strategy

- **Product queries** are cached using Redis
- **Cache TTL**: 1 hour (configurable)
- **Cache eviction** on product updates/deletes
- **Batch processing** for CSV imports (configurable batch size)

### Database Optimization

- **Connection pooling** with HikariCP
- **Database indexing** on frequently queried columns
- **Flyway migrations** for schema versioning
- **JPA optimization** with appropriate fetch strategies

## 🔐 Security

### Authentication & Authorization

- **Spring Security** integration
- **Role-based access control** (ADMIN role required)
- **JWT token** support (configuration required)
- **CORS** configuration for web clients

### Security Headers

The application includes security headers:

- Content Security Policy
- X-Frame-Options
- X-Content-Type-Options
- X-XSS-Protection

## 🐳 Docker

### Docker Compose Services

- **Application**: Spring Boot application
- **PostgreSQL**: Primary database
- **Redis**: Caching layer
- **Adminer**: Database administration tool

### Production Deployment

```bash
# Build and start all services
docker-compose -f docker-compose.prod.yml up -d

# Scale application instances
docker-compose -f docker-compose.prod.yml up -d --scale app=3
```

## 📝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support

For support and questions:

- Create an issue in the GitHub repository
- Check the [API documentation](http://localhost:8080/swagger-ui.html) when running locally

---

**Last Updated**: September 2025
