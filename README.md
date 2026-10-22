# Hello Spring — REST API сообщений

Учебная работа по теме «Архитектура веб-приложения на Spring». Минимальный REST API для сообщений игроков: Java 17, Spring Boot 3.2.4, Gradle 8.7. Хранилище работает в памяти. В соответствии с подробной спецификацией задания реализован API сообщений; игровое поле и игровые ходы относятся к отдельной консольной работе.

## Сборка и запуск

Для сборки установите **JDK 17**, задайте `JAVA_HOME` и добавьте его `bin` в `PATH`. При первом запуске нужен интернет: Gradle Wrapper загрузит Gradle и зависимости из Maven Central. Отдельно устанавливать Gradle или Maven не требуется.

В Windows PowerShell из корня проекта:

```powershell
java -version
.\gradlew.bat clean build
java -jar .\build\libs\hello-spring-0.0.1-SNAPSHOT.jar
```

В Linux/macOS:

```sh
chmod +x gradlew
./gradlew clean build
java -jar build/libs/hello-spring-0.0.1-SNAPSHOT.jar
```

Для запуска из исходников можно использовать `.\gradlew.bat bootRun` или `./gradlew bootRun`. Главный класс — `com.example.hellospring.HelloSpringApplication`. В IntelliJ IDEA откройте проект как Gradle и выберите JDK 17 для Gradle JVM и проекта.

После запуска приложение доступно на порту 8080 с контекстом `/api`. Остановить сервер можно сочетанием `Ctrl+C`. При занятом порте используйте `java -jar build/libs/hello-spring-0.0.1-SNAPSHOT.jar --server.port=8081`.

## Проверка API

В Windows используйте `curl.exe`, чтобы не зависеть от одноимённого псевдонима PowerShell:

```powershell
curl.exe http://localhost:8080/api/
curl.exe http://localhost:8080/api/messages

# Через стандартный PowerShell HTTP-клиент:
$body = @{ content = 'Хорошая игра!'; author = 'Иван' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/messages `
    -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

Примеры для curl в Linux/macOS:

```sh
curl http://localhost:8080/api/
curl http://localhost:8080/api/messages
curl -i -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: application/json' \
  -d '{"content":"Good game!","author":"Ivan"}'
```

| Метод и путь | Результат |
| --- | --- |
| `GET /api/` | `200 OK`, текст `Hello, World!` |
| `GET /api/messages` | `200 OK`, JSON-массив последних 30 сообщений |
| `POST /api/messages` | `201 Created`, созданное сообщение с ID |

Сразу после запуска `GET /api/messages` возвращает:

```json
[
  {"id":1,"content":"Hello, Spring!","author":"System"},
  {"id":2,"content":"In-memory storage works","author":"System"}
]
```

После создания сообщение появится в конце выдачи. Последними считаются сообщения с наибольшими ID; внутри ответа они расположены по возрастанию ID. При 35 сообщениях выдаются сообщения с шестого по тридцать пятое. Ограничение применяется к ответу, а не к размеру хранилища.

Поля `content` и `author` обязательны и не могут состоять только из пробелов. Максимальная длина: 2000 и 100 символов соответственно. Пробелы по краям удаляются. Некорректные поля или повреждённый JSON дают `400 Bad Request` с полем `error`; для ошибок полей есть массив `details`. ID назначает сервер.

## Устройство проекта

- `HelloSpringApplication` запускает Spring Boot.
- `Message` — POJO с `id`, `content`, `author`, конструкторами, геттерами, сеттерами, `equals()` и `hashCode()`.
- `MessageRepository` хранит объекты в `ConcurrentHashMap`, выдаёт уникальные ID через `AtomicLong`, поддерживает `save`, `findById`, `findAll`, `deleteById`, `clear`. При сохранении существующего ID запись обновляется. Чтение и сохранение используют копии объектов, чтобы внешнее изменение POJO не меняло данные в обход репозитория.
- `MessageService` создаёт два начальных сообщения, ограничивает выдачу последними 30 и создаёт сообщения.
- `MessageController` отвечает за HTTP и проверку входных данных. Зависимости передаются через конструкторы.
- `ApiExceptionHandler` формирует понятные ответы об ошибках.

Запрос проходит через цепочку `Controller → Service → Repository`. Настройки находятся в `src/main/resources/application.properties`: контекст `/api` записан одной строкой, оба исключения автоконфигурации БД объединены в одном свойстве. JDBC/JPA зависимости не используются.

## Автоматические тесты

```powershell
.\gradlew.bat test
```

В Linux/macOS: `./gradlew test`. `clean build` также запускает тесты. HTML-отчёт: `build/reports/tests/test/index.html`.

Тесты проверяют главную страницу, JSON выдачи, POST с автором и назначением ID, сохранение, ошибочные поля и JSON, порядок последних 30 сообщений, начальные данные, CRUD репозитория, защитные копии, `equals/hashCode` и уникальность ID при параллельном создании. MockMvc-запросы явно задают `.contextPath("/api")`, учитывая реальную конфигурацию приложения.

## Ограничения

Данные теряются после перезапуска; каждый запуск создаёт два начальных сообщения. Размер хранилища не ограничен. Базы данных, авторизации, сохранения партий, распределённого хранилища и REST-методов удаления/редактирования нет: CRUD предусмотрен на уровне репозитория. `ConcurrentHashMap` обеспечивает безопасные отдельные операции, но выдача списка не является транзакционным снимком при одновременных запросах.
