package conduit.config

import cats.effect._
import cats.implicits._
import ciris._
import ciris.refined._
import environments._
import environments.AppEnvironment._
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString
import scala.concurrent.duration._
import conduit.config.data._

object load {

  // Ciris promotes configuration as code
  def apply[F[_]: Async: ContextShift]: F[AppConfig] =
    env("CD_APP_ENV")
      .as[AppEnvironment]
      .flatMap {
        case Test =>
          default(
            redisUri = RedisURI("redis://localhost")
          )
        case Prod =>
          default(
            redisUri = RedisURI("redis://10.123.154.176")
          )
      }
      .load[F]

  private def default(
      redisUri: RedisURI
  ): ConfigValue[AppConfig] =
    (
      env("CD_ACCESS_TOKEN_SECRET_KEY").as[NonEmptyString].secret,
      env("CD_PASSWORD_SALT").as[NonEmptyString].secret
    ).parMapN { (tokenKey, salt) =>
      AppConfig(
        JwtSecretKeyConfig(tokenKey),
        PasswordSalt(salt),
        TokenExpiration(30.minutes),
        PostgreSQLConfig(
          host = "localhost",
          port = 5432,
          user = "postgres",
          database = "store",
          max = 10
        ),
        RedisConfig(redisUri),
        HttpServerConfig(
          host = "0.0.0.0",
          port = 8080
        )
      )
    }

}
