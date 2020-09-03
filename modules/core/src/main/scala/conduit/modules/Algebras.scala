package conduit.modules

import cats.Parallel
import cats.effect._
import cats.implicits._
import conduit.algebras._
import dev.profunktor.redis4cats.RedisCommands
import skunk._

object Algebras {
  def make[F[_]: Concurrent: Parallel: Timer](
      redis: RedisCommands[F, String, String],
      sessionPool: Resource[F, Session[F]]
  ): F[Algebras[F]] =
    for {
      health <- LiveHealthCheck.make[F](sessionPool, redis)
      profiles <- LiveProfiles.make[F](sessionPool)
      articles <- LiveArticles.make[F](sessionPool)
      tags <- LiveTags.make[F](sessionPool)
      comments <- LiveComments.make[F](sessionPool)
    } yield new Algebras[F](health, profiles, articles, tags, comments)
}

final class Algebras[F[_]] private (
    val healthCheck: HealthCheck[F],
    val profiles: Profiles[F],
    val articles: Articles[F],
    val tags: Tags[F],
    val comments: Comments[F]
)
