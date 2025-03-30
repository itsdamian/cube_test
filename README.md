# Bitcoin Currency Converter

This project is a Spring Boot application that retrieves Bitcoin price data from the Coindesk API, transforms it, and provides RESTful API endpoints for managing currency data.

## Features

- Query Bitcoin price data from Coindesk API
- Transform data to include Chinese names for currencies
- Currency management (CRUD operations)
- H2 in-memory database for data storage
- Unit tests for all functionality
- Docker deployment support (for both ARM and x86 architectures)
- Multi-language support for currency names (Chinese names included)

## Prerequisites

- Docker and Docker Compose
- JDK 17+ (if running outside Docker)
- Maven 3.6+ (if running outside Docker)

## Getting Started with Docker

This project supports both Apple Silicon (M1/M2/M3) and Intel/AMD processors through separate Docker configurations.

### 1. Clone the Repository

```bash
git clone <repository-url>
cd <project-directory>
```

### 2. Build and Run with Docker Compose

For Apple Silicon (M1/M2/M3) Macs:

```bash
docker-compose build app-silicon
docker-compose up -d app-silicon
```

For Intel/AMD processors:

```bash
docker-compose build app-amd64
docker-compose up -d app-amd64
```

### 3. Verify the Application is Running

```bash
# Check if the container is running
docker ps

# Check application logs
docker-compose logs app-silicon
# or
docker-compose logs app-amd64
```

The application will be available at:
- API Endpoints: http://localhost:8080
- H2 Console: http://localhost:8082/h2-console (JDBC URL: `jdbc:h2:mem:testdb`, Username: `sa`, Password: empty)

## API Documentation

### Currency API Endpoints

| Method | URL                          | Description                    |
|--------|------------------------------|--------------------------------|
| GET    | /api/currencies              | Get all currencies             |
| GET    | /api/currencies/{id}         | Get currency by ID             |
| GET    | /api/currencies/code/{code}  | Get currency by code           |
| POST   | /api/currencies              | Create a new currency          |
| PUT    | /api/currencies/{id}         | Update an existing currency    |
| DELETE | /api/currencies/{id}         | Delete a currency              |

### Coindesk API Endpoints

| Method | URL                          | Description                       |
|--------|------------------------------|-----------------------------------|
| GET    | /api/bitcoin/price/original  | Get original Coindesk API data    |
| GET    | /api/bitcoin/price           | Get transformed Bitcoin price data|

## Sample API Requests

### Create Currency

```bash
curl -X POST http://localhost:8080/api/currencies \
  -H "Content-Type: application/json" \
  -d '{"code":"TWD","name":"台幣"}'
```

### Get Original Coindesk API Data

```bash
curl http://localhost:8080/api/bitcoin/price/original
```

### Get Transformed Bitcoin Price Data

```bash
curl http://localhost:8080/api/bitcoin/price
```

Example response:
```json
{
  "updateTime": "2025/03/29 12:15:59",
  "currencies": {
    "EUR": {
      "code": "EUR",
      "rate": 49876.1232,
      "chineseName": "歐元"
    },
    "GBP": {
      "code": "GBP",
      "rate": 42345.8722,
      "chineseName": "英鎊"
    },
    "USD": {
      "code": "USD",
      "rate": 57231.4983,
      "chineseName": "美金"
    }
  }
}
```

## Testing the Application

The application includes comprehensive unit tests for all functionality. Docker is configured to support running tests in the container environment.

### Running Tests in Docker

```bash
# For Apple Silicon
docker-compose exec app-silicon bash -c 'cd /app && mvn test'

# For Intel/AMD
docker-compose exec app-amd64 bash -c 'cd /app && mvn test'
```

### Running Specific Tests

```bash
# Run only currency controller tests
docker-compose exec app-silicon bash -c 'cd /app && mvn test -Dtest=CurrencyControllerTest'
```

## Project Structure

```
src
├── main
│   ├── java
│   │   └── com
│   │       └── currency
│   │           └── demo
│   │               ├── config
│   │               ├── controller
│   │               ├── model
│   │               ├── repository
│   │               ├── service
│   │               └── DemoApplication.java
│   └── resources
│       └── application.properties
├── test
│   ├── java
│   │   └── com
│   │       └── currency
│   │           └── demo
│   │               ├── controller
│   │               │   ├── CoindeskControllerTest.java
│   │               │   └── CurrencyControllerTest.java
│   │               └── service
│   │                   └── CoindeskServiceTest.java
│   └── resources
│       └── application-test.properties
├── Dockerfile.amd64
├── Dockerfile.silicon
└── docker-compose.yml
```

## Building from Source (without Docker)

If you prefer to run the application without Docker:

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Multi-language Support

The application provides Chinese names for currencies in the transformed data response. The currency names are initialized with Chinese characters, and the API responses include these Chinese names when transforming Bitcoin price data.

Default currencies with Chinese names:
- USD: 美金
- EUR: 歐元
- JPY: 日圓
- GBP: 英鎊
- CNY: 人民幣
- HKD: 港幣
- etc.

## Stopping the Application

```bash
# If using Docker Compose
docker-compose down

# To also remove volumes
docker-compose down -v
```

## Troubleshooting

### Common Issues:

1. **Port Conflicts**: If ports 8080 or 8082 are already in use, modify the port mappings in `docker-compose.yml`.

2. **API Connection Issues**: The application uses mock data if it cannot connect to the Coindesk API.

3. **Container Not Starting**: Check Docker logs using `docker-compose logs app-silicon` or `docker-compose logs app-amd64`.

4. **H2 Console Access**: Make sure to use the correct JDBC URL (`jdbc:h2:mem:testdb`) when accessing the H2 console.

5. **Character Encoding**: If Chinese characters are not displaying correctly, ensure your system supports UTF-8 encoding.

## License

[Add your license information here]

## Acknowledgments

- [CoinDesk API](https://www.coindesk.com/coindesk-api) for providing Bitcoin price data
- Spring Boot framework
- H2 Database
- Docker 