RealWorld example
=============

![Build](https://github.com/minedeljkovic/cats-realworld-backend/workflows/Build/badge.svg)

> Scala + Cats implementation of the [RealWorld](https://github.com/gothinkster/realworld) example [api](https://github.com/gothinkster/realworld/tree/master/api), heavily based on the architecture given in the book ["Practical FP in Scala: A hands-on approach"](https://leanpub.com/pfp-scala) and its [example repo](https://github.com/gvolpe/pfps-shopping-cart).

Disclaimer: The project was primarily built for my personal hands-on experience with the architecture given in the book.

## Environment

The following environment variables are needed in order to run the app:
- CD_ACCESS_TOKEN_SECRET_KEY
- CD_PASSWORD_SALT
- CD_APP_ENV

See the [docker-compose.yml](app/docker-compose.yml) file for more details.

## Start local databases

```
docker-compose up
```

## Run with Docker

### Build an image

```
sbt docker:publishLocal
```

### Start container
```
cd /app
docker-compose up
```

## Tests

### Run unit tests

```
sbt test
```

### Run Integration Tests with Postgres and Redis

```
docker-compose up
sbt it:test
docker-compose down
```

## Postman collection

Collection of example API requests is given [here](Conduit.postman_collection.json).