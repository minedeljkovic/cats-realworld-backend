package conduit.modules

import cats.Parallel
import cats.effect._
import cats.implicits._
import dev.profunktor.redis4cats.RedisCommands
import conduit.algebras._
import skunk._

object Algebras {
  def make[F[_]: Concurrent: Parallel: Timer](
      redis: RedisCommands[F, String, String],
      sessionPool: Resource[F, Session[F]]
  ): F[Algebras[F]] =
    for {
      health <- LiveHealthCheck.make[F](sessionPool, redis)
      profiles <- LiveProfiles.make[F](sessionPool)
    } yield new Algebras[F](health, profiles)
}

final class Algebras[F[_]] private (
    val healthCheck: HealthCheck[F],
    val profiles: Profiles[F]
)
