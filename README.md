# RoomBook

Внутрішній сервіс бронювання переговорних кімнат для компанії (~300 працівників). Застосунок надає REST API (`/api/v1`) та веб-інтерфейс на базі Thymeleaf та HTMX.

## Системні вимоги

- **JDK 21** (Eclipse Adoptium Temurin 21.0.12.1+)
- **Apache Maven 3.9+** (перевірено на Maven 3.10.0)
- **Docker** (для локальної бази PostgreSQL 16 та запуску Testcontainers)

## Збірка та запуск тестів

Команда для запуску всіх тестів, включно з HTTP, PostgreSQL/Testcontainers і конкуренцією. Maven автоматично повторює набір у UTC та America/Los_Angeles:

```bash
mvn clean test
```

Повний пакетувальний білд:

```bash
mvn clean package
```

## Запуск у режимі розробки (`dev`)

### 1. Запуск локальної СУБД PostgreSQL 16

Для роботи профілю `dev` потрібен інстанс PostgreSQL 16. Можна запустити через Docker:

```bash
docker run -d \
  --name roombook-postgres \
  -e POSTGRES_DB=roombook \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 127.0.0.1:5432:5432 \
  postgres:16
```

Для цієї локальної БД виконайте `ops/db/init.sql` через `psql` від `postgres` у БД `roombook`. Він задає тестові паролі, узгоджені з `application-dev.properties`. Для робочого середовища використовується окремий `ops/db/init-production.sql` із секретами з оточення.

### 2. Запуск застосунку

Через Maven Spring Boot Plugin:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Або через збірковий JAR:

```bash
java -Dspring.profiles.active=dev -jar target/roombook-0.0.1-SNAPSHOT.jar
```

Після старту застосунок доступний за адресою:
- Веб-інтерфейс: `http://localhost:8080/`
- REST API: `http://localhost:8080/api/v1/`
- Порт керування (Actuator): `http://localhost:8081/actuator`

## Запуск у робочому середовищі (`prod` / без профілю)

У середовищі за замовчуванням (prod) відсутні вбудовані секрети (SEC-16). Запуск вимагає обов'язкової передачі змінних середовища:

```bash
export ROOMBOOK_DB_URL="jdbc:postgresql://db.internal:5432/roombook"
export ROOMBOOK_DB_USERNAME="roombook_app"
export ROOMBOOK_DB_PASSWORD="<секретний_пароль_app>"
export ROOMBOOK_FLYWAY_URL="jdbc:postgresql://db.internal:5432/roombook"
export ROOMBOOK_FLYWAY_USERNAME="roombook_owner"
export ROOMBOOK_FLYWAY_PASSWORD="<секретний_пароль_owner>"
export ROOMBOOK_ADMIN_USERNAME="admin"
export ROOMBOOK_ADMIN_PASSWORD="<секретний_пароль_admin>"

java -jar target/roombook-0.0.1-SNAPSHOT.jar
```

Подальші кроки: [експлуатація, резервні копії та відкат](docs/runbook.md), [стан реалізації](docs/implementation-progress.md), [результати перевірки](docs/verification-results.md), [індекс тестових доказів](docs/coverage-evidence.md).

Контейнерний smoke: `docker build -t roombook:verify .`, потім `python ops/container/smoke.py`. Навантаження: `python ops/load/run.py` (понад 90 хвилин; скорочений `--diagnostic` не є прийманням). Репетиція відновлення: `python ops/backup/rehearse.py`. Ці сценарії працюють лише зі створеними ними тимчасовими контейнерами.
