# Инструкция

Скачать надо будет только эти 2 файла и положить в одну папку:
- `docker-compose.yml`
- `db_init.sql`

Там же создать файл `.env` с переменными:
```
BOT_TOKEN
BOT_USERNAME
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
DB_NAME
```

Запуск: `sudo docker-compose up -d`

Образ бота - darinax/late-manager-java

Документация nyagram - https://nyagram.kaleert.pro/
