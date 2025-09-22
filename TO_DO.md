# Albert Team Bootcamp Assignment

## Product Catalog Service

Welcome to Albert's development team bootcamp! Your task is to build a microservice from scratch that manages product
catalog information for our e-commerce platform.

## 🎯 Objective

Create a complete **Product Catalog Service** from the ground up, demonstrating your ability to:

- Design and implement a REST API
- Write tests
- Handle file uploads and data processing

## 📋 Requirements

### Core Functionality

You need to implement a REST API with the following endpoints:

| Method | Endpoint                  | Description                       |
|--------|---------------------------|-----------------------------------|
| GET    | `/api/v1/products`        | List all products with pagination |
| GET    | `/api/v1/products/{id}`   | Get product by ID                 |
| PUT    | `/api/v1/products/{id}`   | Update existing product           |
| DELETE | `/api/v1/products/{id}`   | Delete product                    |
| POST   | `/api/v1/products/import` | Import products from CSV file     |

### Technical Stack

- **Language**: Kotlin
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL (Docker container)
- **Build Tool**: Gradle
- **Java Version**: 21+
- **Testing**: JUnit 5, MockK (or similar)
- **Documentation**: SpringDoc OpenAPI

### Implementation Requirements

#### 1. Project Setup

- **Standard Maven/Gradle structure**: `src/main/kotlin`, `src/test/kotlin`
- **Docker Compose**: PostgreSQL database configuration

#### 2. Validation & Error Handling

- Create a global exception handler
- Return appropriate HTTP status codes:
    - 200 OK for successful GET/PUT
    - 201 Created for successful POST
    - 204 No Content for successful DELETE
    - 400 Bad Request for validation errors
    - 404 Not Found when resource doesn't exist
    - 500 Internal Server Error for unexpected errors

#### 3. Pagination

Implement pagination for the list endpoint:

```kotlin
GET / api / v1 / products?page = 0&size = 20&sort = name, asc
```

#### 4. Testing

Write tests:

- Cover happy day scenarios
- Use proper test naming conventions

#### 5. CSV Import Feature

Implement a CSV import endpoint that can process the provided
`product_import_anonymized.csv` [file](src/test/resources/product_import_anonymized.csv).

#### 6. Documentation & API

- **SpringDoc OpenAPI**: Configure Swagger UI for API documentation
- **Code Documentation**: Meaningful comments where necessary
- **API Endpoints**: All endpoints should be documented and accessible via Swagger

## 🚀 Getting Started

### Provided Resources

You'll have access to:

- **CSV Sample Data**: `product_import_anonymized.csv` [file](src/test/resources/product_import_anonymized.csv) with
  sample products

## 📝 Deliverables

Provide a link to your Git repository with the complete project

## 💡 Bonus Points

Impress us with these optional enhancements:

- **Docker**: Containerization with Dockerfile
- **Caching**: Implement Redis caching
- **Metrics**: Add application metrics and health checks
- **Security**: Basic authentication/authorization
- **Advanced Features**: Search, filtering, or sorting capabilities

## 🤝 Need Help?

- **Documentation**: Use Spring Boot and Kotlin documentation for reference
- **Questions**: Don't hesitate to ask clarifying questions about requirements
- **Focus**: Prioritize working functionality over perfect code
- **Team Mindset**: Part of the job is supporting other developers—ask for help when needed!

## 📋 Final Notes

- **Presentation**: You'll present your solution to us, so keep code well-organized
- **Maintainability**: Write code that other team members can easily understand and modify

---

**Good luck! We're excited to see what you build! 🚀**
