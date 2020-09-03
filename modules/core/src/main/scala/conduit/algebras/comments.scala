package conduit.algebras

import cats.effect._
import cats.implicits._
import conduit.domain.comment._
import conduit.domain.article.{ ArticleId, Slug }
import conduit.domain.profile._
import conduit.domain.user._
import conduit.ext.skunkx._
import scala.concurrent.duration.MILLISECONDS
import skunk._
import skunk.codec.all._
import skunk.implicits._
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneOffset

trait Comments[F[_]] {
  def find(userId: Option[UserId])(slug: Slug): F[List[Comment]]
  def create(userId: UserId)(slug: Slug)(body: CommentBody): F[Option[Comment]]
  def delete(userId: UserId)(slug: Slug, id: CommentId): F[Option[Unit]]
}

object LiveComments {
  def make[F[_]: Sync: Clock](
      sessionPool: Resource[F, Session[F]]
  ): F[Comments[F]] =
    Sync[F].delay(
      new LiveComments[F](sessionPool)
    )
}

final class LiveComments[F[_]: Sync: Clock] private (
    sessionPool: Resource[F, Session[F]]
) extends Comments[F] {
  import CommentQueries._

  def find(userId: Option[UserId])(slug: Slug): F[List[Comment]] =
    sessionPool.use { session =>
      session.prepare(selectCommentsBySlug).use(_.stream(userId ~ slug, 64).compile.toList)
    }

  def create(userId: UserId)(slug: Slug)(body: CommentBody): F[Option[Comment]] =
    now.flatMap { nowDateTime =>
      sessionPool.use { session =>
        (
          session.prepare(selectNewCommentIdBySlug),
          session.prepare(insertComment),
          session.prepare(selectUser)
        ).tupled.use {
          case (selectNewCommentIdQuery, insertCmd, selectUserQuery) =>
            selectNewCommentIdQuery
              .option(slug)
              .flatMap {
                case Some(articleId ~ id) =>
                  val createdAt = CommentCreateDateTime(nowDateTime)
                  val updatedAt = CommentUpdateDateTime(nowDateTime)

                  for {
                    _ <- insertCmd.execute(articleId ~ id ~ body ~ userId ~ createdAt ~ updatedAt)
                    username ~ bio ~ image <- selectUserQuery.unique(userId)
                  } yield Comment(
                    id,
                    body,
                    createdAt,
                    updatedAt,
                    CommentAuthor(
                      userId,
                      username,
                      bio,
                      image,
                      FollowingStatus(false) // user does not follow himself
                    )
                  ).some
                case None =>
                  none[Comment].pure[F]
              }
        }
      }
    }

  def delete(userId: UserId)(slug: Slug, id: CommentId): F[Option[Unit]] =
    sessionPool.use { session =>
      (
        session.prepare(selectArticleIdAndAuthorId),
        session.prepare(deleteComment)
      ).tupled.use {
        case (selectCommentQuery, deleteCommentCmd) =>
          selectCommentQuery.option(slug ~ id).flatMap {
            case Some(articleId ~ authorId) if authorId == userId =>
              deleteCommentCmd.execute(articleId ~ id) *> ().some.pure[F]
            case Some(_ ~ authorId) =>
              CurrentUserNotCommentAuthor(authorId).raiseError[F, Option[Unit]]
            case None =>
              none[Unit].pure[F]
          }
      }
    }

  private val now: F[OffsetDateTime] =
    Clock[F]
      .realTime(MILLISECONDS)
      .map(epochMilli => OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC))

}

private object CommentQueries {

  val selectUser: Query[UserId, UserName ~ Option[Bio] ~ Option[Image]] =
    sql"""
        SELECT username, bio, image
        FROM users
        WHERE uuid = ${uuid.cimap[UserId]}
       """.query(varchar.cimap[UserName] ~ varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt)

  private val commentDecoder: Decoder[Comment] =
    (int4.cimap[CommentId] ~ varchar.cimap[CommentBody] ~
        timestamptz(3).cimap[CommentCreateDateTime] ~ timestamptz(3).cimap[CommentUpdateDateTime] ~
        uuid.cimap[UserId] ~ varchar.cimap[UserName] ~ varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt ~
        bool.cimap[FollowingStatus]).map {
      case id ~ b ~ cr ~ up ~ uid ~ un ~ bi ~ im ~ fl =>
        Comment(id, b, cr, up, CommentAuthor(uid, un, bi, im, fl))
    }

  val selectCommentsBySlug: Query[Option[UserId] ~ Slug, Comment] =
    sql"""
        SELECT
          c.id, c.body, c.created_at, c.updated_at,
          au.uuid, au.username, au.bio, au.image,
          CASE
            WHEN (${uuid.cimap[UserId].opt} IS NOT NULL AND EXISTS (
              SELECT * FROM followers
              WHERE follower_id = ${uuid.cimap[UserId].opt}
                AND followed_id = au.uuid
            )) THEN true
            ELSE false
          END as following
        FROM comments c
        JOIN articles a ON a.uuid = c.article_id
        JOIN users au on au.uuid = c.author_id
        WHERE a.slug = ${varchar.cimap[Slug]}
      """.query(commentDecoder).contramap {
      case uid ~ s => uid ~ uid ~ s
    }

  val selectNewCommentIdBySlug: Query[Slug, ArticleId ~ CommentId] =
    sql"""
        SELECT
          a.uuid,
          CASE
            WHEN MAX(c.id) IS NULL THEN 1
            ELSE MAX(c.id) + 1
          END as id
        FROM articles a
        LEFT JOIN comments c ON c.article_id = a.uuid
        WHERE a.slug = ${varchar.cimap[Slug]}
        GROUP BY a.uuid
      """.query(uuid.cimap[ArticleId] ~ int4.cimap[CommentId])

  val insertComment
      : Command[ArticleId ~ CommentId ~ CommentBody ~ UserId ~ CommentCreateDateTime ~ CommentUpdateDateTime] =
    sql"""
        INSERT INTO comments (article_id, id, body, author_id, created_at, updated_at)
        VALUES (${uuid.cimap[ArticleId]}, ${int4.cimap[CommentId]}, ${varchar.cimap[CommentBody]},
          ${uuid.cimap[UserId]}, ${timestamptz(3).cimap[CommentCreateDateTime]}, ${timestamptz(3)
      .cimap[CommentUpdateDateTime]})
       """.command

  val selectArticleIdAndAuthorId: Query[Slug ~ CommentId, ArticleId ~ UserId] =
    sql"""
        SELECT article_id, author_id
        FROM comments
        WHERE article_id = (SELECT article_id FROM articles WHERE slug = ${varchar.cimap[Slug]})
          AND id         = ${int4.cimap[CommentId]}
       """.query(uuid.cimap[ArticleId] ~ uuid.cimap[UserId])

  val deleteComment: Command[ArticleId ~ CommentId] =
    sql"""
        DELETE FROM comments
        WHERE article_id = ${uuid.cimap[ArticleId]}
          AND id         = ${int4.cimap[CommentId]}
       """.command

}
