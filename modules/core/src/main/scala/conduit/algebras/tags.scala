package conduit.algebras

import cats.effect._
import conduit.domain.tag._
import conduit.ext.skunkx._
import conduit.effects.BracketThrow
import skunk._
import skunk.codec.all._
import skunk.implicits._

trait Tags[F[_]] {
  def all: F[List[ArticleTag]]
}

object LiveTags {
  def make[F[_]: Sync](
      sessionPool: Resource[F, Session[F]]
  ): F[Tags[F]] =
    Sync[F].delay(
      new LiveTags[F](sessionPool)
    )
}

final class LiveTags[F[_]: BracketThrow] private (
    sessionPool: Resource[F, Session[F]]
) extends Tags[F] {
  import TagQueries._

  def all: F[List[ArticleTag]] =
    sessionPool.use(_.execute(selectAllTag))

}

private object TagQueries {

  val selectAllTag: Query[Void, ArticleTag] =
    sql"""
      SELECT DISTINCT tag
      FROM article_tags
    """.query(varchar.cimap[ArticleTag])

}
