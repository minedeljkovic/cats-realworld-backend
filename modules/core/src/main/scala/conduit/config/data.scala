package conduit.config

import ciris._
import eu.timepit.refined.types.net.UserPortNumber
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import scala.concurrent.duration._

object data {

  @newtype case class JwtSecretKeyConfig(value: Secret[NonEmptyString])
  @newtype case class TokenExpiration(value: FiniteDuration)

  @newtype case class PasswordSalt(value: Secret[NonEmptyString])

  case class AppConfig(
      tokenConfig: JwtSecretKeyConfig,
      passwordSalt: PasswordSalt,
      tokenExpiration: TokenExpiration,
      postgreSQL: PostgreSQLConfig,
      redis: RedisConfig,
      httpServerConfig: HttpServerConfig
  )

  case class PostgreSQLConfig(
      host: NonEmptyString,
      port: UserPortNumber,
      user: NonEmptyString,
      database: NonEmptyString,
      max: PosInt
  )

  @newtype case class RedisURI(value: NonEmptyString)
  @newtype case class RedisConfig(uri: RedisURI)

  case class HttpServerConfig(
      host: NonEmptyString,
      port: UserPortNumber
  )

}
