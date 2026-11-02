# Работа с базой данных. Часть 1

Домашнее задание по Java: переход от хранилища сообщений в памяти к JDBC и внешней PostgreSQL. Продолжение проекта [Hello Spring REST API](https://github.com/Vanusha61/hello-spring-rest-api/tree/main), новая версия находится в ветке `jdbc-database-part1`.

## Выполненные требования

| Требование | Реализация |
| --- | --- |
| JDBC | `JdbcTemplate`, SQL-запросы с параметрами, `GeneratedKeyHolder`, дата создания из БД |
| Внешняя БД | PostgreSQL по умолчанию, драйвер и настройки через переменные окружения |
| Ошибки | 404 для отсутствующего сообщения/пользователя, 400 для некорректного запроса, 409 при конфликте, 503 при сбое БД |
| Пагинация | `page` и `size`, общее количество, число страниц, сортировка `created_at DESC, id DESC` |
| Батч | `POST /api/messages/batch`, `JdbcTemplate.batchUpdate`, одна транзакция |
| Пользователи | `app_users`, `UserRepository`, внешний ключ `messages.user_id`, поиск по имени автора |

Стек: Java 17, Spring Boot 3.5.7, Gradle Wrapper 8.7. Для альтернативной сборки есть Maven `pom.xml` с теми же зависимостями. Используется только JDBC, без JPA и коллекции вместо базы данных.

## Сборка и быстрый просмотр

Для Gradle Wrapper выберите **JDK 17 или 21** (в том числе в настройках Gradle JVM в IntelliJ IDEA). Первый запуск загружает Gradle и зависимости. Для Maven нужен Maven 3.9+ и совместимый JDK; исходники компилируются с `release 17`.

Windows PowerShell:

```powershell
.\gradlew.bat clean build
java -jar .\build\libs\java-jdbc-homework-1.0.0.jar --spring.profiles.active=demo
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew clean build
java -jar build/libs/java-jdbc-homework-1.0.0.jar --spring.profiles.active=demo
```

Альтернатива через Maven:

```powershell
mvn clean verify
java -jar .\target\java-jdbc-homework-1.0.0.jar --spring.profiles.active=demo
```

Профиль `demo` использует настоящую реляционную H2 в памяти в режиме PostgreSQL и два стартовых сообщения. Данные demo исчезают после остановки. Этот профиль нужен только для быстрого просмотра без установки PostgreSQL. Проверка: [http://localhost:8080/api/messages](http://localhost:8080/api/messages). Остановка: Ctrl+C. Порт меняется параметром `--server.port=8081`.

## Основной режим: PostgreSQL

При наличии Docker запустите PostgreSQL 17 (данные сохраняются в именованном томе):

```powershell
docker compose up -d --wait
java -jar .\build\libs\java-jdbc-homework-1.0.0.jar
```

При сборке Maven замените путь JAR на `target/java-jdbc-homework-1.0.0.jar`. Если порт 5432 занят, используйте свою PostgreSQL и переменные окружения ниже либо измените порт в `compose.yaml` и `DB_URL`.

Для уже установленной PostgreSQL создайте отдельного пользователя и базу (SQL выполняется администратором БД):

```sql
CREATE USER messages WITH PASSWORD 'messages';
CREATE DATABASE messages OWNER messages;
```

Пример конфигурации PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/messages'
$env:DB_USERNAME = 'messages'
$env:DB_PASSWORD = 'messages'
java -jar .\build\libs\java-jdbc-homework-1.0.0.jar
```

`messages/messages` — учебные локальные настройки. Для своей БД передайте собственный пароль через `DB_PASSWORD`; не сохраняйте его в Git. В Compose эта же переменная задаёт пароль нового кластера. У существующего тома смена переменной не меняет уже установленный пароль.

Таблицы и индексы создаются автоматически из `schema.sql`. Обычный запуск не очищает БД и не добавляет повторно демонстрационные сообщения. После перезапуска данные PostgreSQL сохраняются. `docker compose stop` останавливает контейнер без удаления тома.

## REST API

| Метод | Путь | Ответ |
| --- | --- | --- |
| GET | `/api/messages?page=0&size=30` | 200, страница сообщений |
| GET | `/api/messages?author=Ivan&page=0&size=10` | 200, страница сообщений указанного автора |
| GET | `/api/messages/{id}` | 200 или 404 |
| POST | `/api/messages` | 201, сообщение с ID и временем БД |
| POST | `/api/messages/batch` | 201, `{"createdCount":2}` |
| DELETE | `/api/messages/{id}` | 204 или 404 |
| GET | `/api/users` | 200, пользователи |
| GET | `/api/users/{id}` | 200 или 404 |
| POST | `/api/users` | 201 или 409 для повторяющегося имени |

Создание сообщения:

```json
{"content":"Привет, JDBC!","author":"Ivan"}
```

Автор создаётся в `app_users` автоматически или используется существующий пользователь с этим именем. Если автор отсутствует, `null` или состоит из пробелов, используется `Anonymous`. Пробелы по краям имени удаляются. Вместо имени можно передать `userId`:

```json
{"content":"Сообщение существующего пользователя","userId":1}
```

Если одновременно указаны `userId` и непустой `author`, имя должно совпасть. Неизвестный `userId` даёт 404. Поиск по `author` — точное, регистрозависимое сравнение имени после удаления краевых пробелов.

Пример ответа POST:

```json
{"id":1,"content":"Привет, JDBC!","author":"Ivan","userId":1,"createdAt":"2026-11-02T12:00:00"}
```

Пример страницы (номера начинаются с нуля):

```json
{
  "content":[{"id":1,"content":"Привет, JDBC!","author":"Ivan","userId":1,"createdAt":"2026-11-02T12:00:00"}],
  "page":0,
  "size":30,
  "totalElements":1,
  "totalPages":1
}
```

Доступны все страницы, жёсткого ограничения в 30 записей больше нет. Допустимы `page >= 0`, `1 <= size <= 100`. Страница за пределами списка содержит пустой `content`. При одинаковом времени создания более высокий ID выводится первым. `createdAt` — локальное время БД без часового пояса.

Тело массового создания — массив из 1–1000 сообщений:

```json
[
  {"content":"Первое","author":"Ivan"},
  {"content":"Второе","author":"Anna"}
]
```

Контроллер проверяет все элементы, затем сервис выполняет SQL batch в одной транзакции. Если хотя бы один пользователь не найден или запись не проходит ограничение БД, изменения всего пакета, включая новых пользователей, откатываются. `content` обязателен, не может состоять только из пробелов и ограничен 255 символами. Имя пользователя/автора — до 100 символов; для создания пользователя `name` обязателен.

Пример PowerShell с UTF-8:

```powershell
$body = @{ content = 'Привет, JDBC!'; author = 'Ivan' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/messages -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
Invoke-RestMethod -Uri 'http://localhost:8080/api/messages?page=0&size=10&author=Ivan'
```

Создание пользователя: `POST /api/users` с `{"name":"Ivan"}`. Ошибки возвращаются как `application/problem+json` с полями `status`, `title`, `detail`, например:

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"Сообщение с ID 999 не найдено","instance":"/api/messages/999"}
```

## Тесты

```powershell
.\gradlew.bat test
# или
mvn test
```

19 тестовых запусков проверяют реальные SQL и ограничения БД: ID/дату из БД, связь с пользователем, Anonymous, пагинацию больше 30, стабильную сортировку, фильтр до пагинации, батч, откат новых пользователей, 404/409/400, неверный JSON и валидацию. По умолчанию тестовый профиль использует отдельную H2. Откат батча проверяется вне внешней тестовой транзакции.

Для выполнения того же набора на **отдельной пустой тестовой PostgreSQL**:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/messages_test'
$env:TEST_DB_DRIVER = 'org.postgresql.Driver'
$env:TEST_DB_USERNAME = 'messages'
$env:TEST_DB_PASSWORD = 'messages'
mvn test
```

Базу `messages_test` нужно создать заранее. Эти переменные действуют также при запуске тестов через Gradle. Не используйте рабочую БД: тесты предназначены для изолированной пустой базы. Отчёты: `build/reports/tests/test/index.html` (Gradle), `target/surefire-reports` (Maven).

## Устройство и ограничения

`Controller → Service → Repository → JdbcTemplate → PostgreSQL`. `Message` и `User` — неизменяемые Java records. Имя автора не дублируется в `messages`: оно читается через JOIN с `app_users`. Запросы параметризованы, связь защищена внешним ключом, пакет — `@Transactional`. Чтение страницы и общего количества выполняется в согласованном снимке REPEATABLE_READ.

Нет авторизации и редактирования сообщений: это учебный REST API. Имена уникальны и регистрозависимы. Одновременное первое создание одного автора может дать 409 одному из запросов; запрос можно повторить. `schema.sql` предназначен для создания чистой схемы, миграция произвольной старой схемы не реализована. Подключение к MySQL потребует замены драйвера, URL и проверки SQL; в этой работе выбрана PostgreSQL.

Документация: [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc.html), [Spring Boot: SQL databases](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html).
