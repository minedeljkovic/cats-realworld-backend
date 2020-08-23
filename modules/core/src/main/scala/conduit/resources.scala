package conduit

import cats.effect._
import cats.implicits._
import config.data._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import dev.profunktor.redis4cats.log4cats._
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import natchez.Trace.Implicits.noop // needed for skunk
import skunk._

final case class AppResources[F[_]](
    psql: Resource[F, Session[F]],
    redis: RedisCommands[F, String, String]
)

object AppResources {

  def make[F[_]: ConcurrentEffect: ContextShift: Logger](
      cfg: AppConfig
  ): Resource[F, AppResources[F]] = {

    def mkPostgreSqlResource(c: PostgreSQLConfig): SessionPool[F] =
      Session
        .pooled[F](
          host = c.host.value,
          port = c.port.value,
          user = c.user.value,
          database = c.database.value,
          max = c.max.value
        )

    def mkRedisResource(c: RedisConfig): Resource[F, RedisCommands[F, String, String]] =
      Redis[F].utf8(c.uri.value)

    (
      mkPostgreSqlResource(cfg.postgreSQL),
      mkRedisResource(cfg.redis)
    ).mapN(AppResources.apply[F])

  }

}
