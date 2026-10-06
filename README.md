# monolit

`monolit` — каркас Spring Boot-приложения с VK Long Poll, клавиатурами,
маршрутизацией действий по payload и отправкой текста и фотографий.
Сохранён новостной функционал Cherinfo: RSS, полный текст статей и изображения.

## Проверка и запуск

Требуются Java 21, Maven и PostgreSQL. Сборка с тестами: `mvn -B clean verify`.
Для запуска задайте `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `VK_GROUP_ID`, `VK_GROUP_TOKEN` и `VK_MY_ID`.
Запуск: `mvn spring-boot:run` или
`java -jar target/monolit-0.0.1-SNAPSHOT.jar`.

Long Poll включён свойством `monolit.vk.long-poll.enabled=true`. Входящие
сообщения принимаются только от `VK_MY_ID`. Payload кнопки содержит `type`,
`version` и `data`; `CallbackPayloadDispatcher` выбирает Spring bean
`CallbackPayloadHandler<T>` по типу и версии, преобразует данные в DTO и вызывает
обработчик. Конкретных обработчиков бизнес-действий и меню в каркасе нет.
Сообщения без payload не запускают действий; неизвестные payload-маршруты
логируются. Механизм построения постоянных и inline-клавиатур сохранён.

Автоматическая публикация Cherinfo отключена:
`monolit.news.cherinfo.enabled=false`. Это отключает и cron, и публикацию при
старте. Для включения задайте `MONOLIT_NEWS_CHERINFO_ENABLED=true`.
Расписание — `monolit.news.cherinfo.cron`, зона — `monolit.news.zone`
(`Europe/Moscow`). Состояние ленты хранится в `CherinfoNewsState` в PostgreSQL.

Подключение к БД и VK требуется даже при отключённых новостях. Hibernate
использует `ddl-auto=update`; удаление старых Java-сущностей не удаляет физические
таблицы и данные существующей БД.

## License

This repository is distributed under the **PolyForm Noncommercial License 1.0.0**.

- Personal and other noncommercial use is allowed.
- Commercial use is not allowed without prior written permission from the copyright holder.

Because of the noncommercial restriction, this is a source-available license model (not OSI open source).

Full license text: [LICENSE](LICENSE)
