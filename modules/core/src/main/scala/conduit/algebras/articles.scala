package conduit.algebras

import cats.effect._
import cats.implicits._
import conduit.domain.article._
import conduit.domain.profile._
import conduit.domain.user._
import conduit.ext.skunkx._
import fs2._
import scala.concurrent.duration.MILLISECONDS
import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._
import conduit.domain.user.UserId
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneOffset

trait Articles[F[_]] {
  def find(userId: Option[UserId])(slug: Slug): F[Option[Article]]
  def create(userId: UserId)(article: NewArticle, tagList: List[ArticleTag]): F[Article]
  def delete(userId: UserId)(slug: Slug): F[Unit]
  def feed(userId: UserId)(limit: Option[Limit], offset: Option[Offset]): F[(List[Article], ArticlesCount)]
}

object LiveArticles {
  def make[F[_]: Sync: Clock](
      sessionPool: Resource[F, Session[F]]
  ): F[Articles[F]] =
    Sync[F].delay(
      new LiveArticles[F](sessionPool)
    )
}

final class LiveArticles[F[_]: Sync: GenUUID: Clock] private (
    sessionPool: Resource[F, Session[F]]
) extends Articles[F] {
  import ArticleQueries._

  private def joinedToList(s: Stream[F, (SelectArticleResult, Option[ArticleTag])]): F[List[Article]] =
    s.groupAdjacentBy(_._1)
      .map {
        case (
            _ ~ slug ~ title ~ description ~ body ~ createdAt ~ updatedAt ~ favorited ~ favoritesCount ~ username ~ bio ~ image ~ following,
            tags
            ) =>
          Article(
            slug,
            title,
            description,
            body,
            tags.collect { case (_, Some(tag)) => tag }.toList,
            createdAt,
            updatedAt,
            favorited,
            favoritesCount,
            Author(
              username,
              bio,
              image,
              following
            )
          )
      }
      .compile
      .toList

  def find(userId: Option[UserId])(slug: Slug): F[Option[Article]] =
    sessionPool.use { session =>
      userId match {
        case Some(id) =>
          session.prepare(selectArticleForUserBySlug).use { q =>
            joinedToList(q.stream(id ~ slug, 64)).map(_.headOption)
          }
        case None =>
          session.prepare(selectArticleBySlug).use { q =>
            joinedToList(q.stream(slug, 64)).map(_.headOption)
          }
      }
    }

  private val now: F[OffsetDateTime] =
    Clock[F]
      .realTime(MILLISECONDS)
      .map(epochMilli => OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC))

  def create(userId: UserId)(article: NewArticle, tagList: List[ArticleTag]): F[Article] =
    GenUUID[F].make[ArticleId].flatMap { id =>
      sessionPool.use { session =>
        session.transaction.use { _ => // have to use transaction because insertArticleTag is optional
          (
            session.prepare(insertArticle),
            session.prepare(selectUser)
          ).tupled.use {
            case (insertCmd, selectUserQuery) =>
              for {
                nowDateTime <- now
                slug      = article.title.toSlug()
                createdAt = CreateDateTime(nowDateTime)
                updatedAt = UpdateDateTime(nowDateTime)
                _ <- insertCmd.execute(id ~ slug ~ article ~ userId ~ createdAt ~ updatedAt).handleErrorWith {
                      case SqlState.UniqueViolation(e) if e.constraintName == Some("unq_slug") =>
                        SlugInUse(slug).raiseError[F, Completion]
                    }
                _ <- if (tagList.length > 0) {
                      val articleTags = tagList.map(id -> _)
                      session.prepare(insertArticleTag(articleTags)).use(_.execute(articleTags))
                    } else ().pure[F]
                username ~ bio ~ image <- selectUserQuery.unique(userId)
              } yield Article(
                slug,
                article.title,
                article.description,
                article.body,
                tagList,
                createdAt,
                updatedAt,
                FavoritedStatus(false), // created article cannot be favorited by user...
                FavoritesCount(0), // ... nor have any favorites at all
                Author(
                  username,
                  bio,
                  image,
                  FollowingStatus(false) // user does not follow himself
                )
              )
          }
        }
      }
    }

  def delete(userId: UserId)(slug: Slug): F[Unit] =
    sessionPool.use { session =>
      (
        session.prepare(selectArticleIdAndAuthorId),
        session.prepare(deleteArticleTags),
        session.prepare(deleteArticle)
      ).tupled.use {
        case (selectArticleQuery, deleteArticleTagsCmd, deleteArticleCmd) =>
          selectArticleQuery.unique(slug).flatMap {
            case id ~ authorId if authorId == userId =>
              for {
                _ <- deleteArticleTagsCmd.execute(id)
                _ <- deleteArticleCmd.execute(id)
                // TODO: handle favorites and comments existence
              } yield ()
            case _ ~ authorId =>
              CurrentUserNotAuthor(authorId).raiseError[F, Unit]
          }
      }
    }

  def feed(userId: UserId)(limit: Option[Limit], offset: Option[Offset]): F[(List[Article], ArticlesCount)] =
    sessionPool.use { session =>
      (
        // it's to complex for me to do this in one query
        session.prepare(selectArticleFollwedByUser),
        session.prepare(countArticleFollwedByUser)
      ).tupled.use {
        case (query, countQuery) =>
          for {
            list <- joinedToList(query.stream(userId ~ (limit ~ offset), 64))
            count <- countQuery.unique(userId)
          } yield (list, count)
      }
    }

}

private object ArticleQueries {

  private val selectArticle: Fragment[Void] =
    sql"""
        SELECT a.uuid, a.slug, a.title, a.description, a.body, a.created_at, a.updated_at,
               false as favorited,
               (SELECT COUNT(*) FROM favorites f WHERE f.article_id = a.uuid) as favorites_count,
               au.username, au.bio, au.image,
               false as following,
               t.tag
       """

  private val selectArticleForUser: Fragment[UserId] =
    sql"""
        SELECT a.uuid, a.slug, a.title, a.description, a.body, a.created_at, a.updated_at,
               CASE
                 WHEN EXISTS (
                   SELECT *
                   FROM favorites
                   WHERE user_id = ${uuid.cimap[UserId]}
                 ) THEN true
                 ELSE false
               END as favorited,
               (SELECT COUNT(*) FROM favorites f WHERE f.article_id = a.uuid) as favorites_count,
               au.username, au.bio, au.image,
               CASE
                 WHEN EXISTS (
                   SELECT * FROM followers
                   WHERE follower_id = ${uuid.cimap[UserId]}
                     AND followed_id = au.uuid
                 ) THEN true
                 ELSE false
               END as following,
               t.tag
       """.contramap(id => id ~ id)

  val selectCount: Fragment[Void] = sql"""SELECT COUNT(DISTINCT a.uuid)"""

  val fromArticle: Fragment[Void] =
    sql"""
        FROM Articles a
        JOIN users au on au.uuid = a.author_id
        LEFT JOIN article_tags t ON t.article_id = a.uuid
      """

  type SelectArticleResult =
    ArticleId ~ Slug ~ Title ~ Description ~ Body ~ CreateDateTime ~ UpdateDateTime ~ FavoritedStatus ~
        FavoritesCount ~ UserName ~ Option[Bio] ~ Option[Image] ~ FollowingStatus

  private val selectArticleResultCodec: Codec[SelectArticleResult] =
    uuid.cimap[ArticleId] ~ varchar.cimap[Slug] ~ varchar.cimap[Title] ~ varchar.cimap[Description] ~
        varchar.cimap[Body] ~ timestamptz(3).cimap[CreateDateTime] ~ timestamptz(3).cimap[UpdateDateTime] ~
        bool.cimap[FavoritedStatus] ~ int8.cimap[FavoritesCount] ~ varchar.cimap[UserName] ~
        varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt ~ bool.cimap[FollowingStatus]

  private val slugEquals: Fragment[Slug] = sql"""slug = ${varchar.cimap[Slug]}"""

  private val orderMostRecent: Fragment[Void] = sql"""ORDER BY a.updated_at DESC"""

  val selectArticleBySlug: Query[Slug, SelectArticleResult ~ Option[ArticleTag]] =
    sql"""
      $selectArticle
      $fromArticle
      WHERE $slugEquals
    """.query(selectArticleResultCodec ~ varchar.cimap[ArticleTag].opt)

  val selectArticleForUserBySlug: Query[UserId ~ Slug, SelectArticleResult ~ Option[ArticleTag]] =
    sql"""
      $selectArticleForUser
      $fromArticle
      WHERE $slugEquals
    """.query(selectArticleResultCodec ~ varchar.cimap[ArticleTag].opt)

  val followedByUser: Fragment[UserId] =
    sql"""
        EXISTS (
          SELECT * FROM followers
          WHERE follower_id = ${uuid.cimap[UserId]}
            AND followed_id = au.uuid
        )
      """

  val selectArticleFollwedByUser
      : Query[UserId ~ (Option[Limit] ~ Option[Offset]), SelectArticleResult ~ Option[ArticleTag]] =
    sql"""
        $selectArticleForUser
        $fromArticle
        WHERE $followedByUser
        ${limitOffset(orderMostRecent)}
        """
      .query(selectArticleResultCodec ~ varchar.cimap[ArticleTag].opt)
      .contramap {
        case id ~ (l ~ o) => id ~ id ~ (Void ~ l ~ o)
      }

  val countArticleFollwedByUser: Query[UserId, ArticlesCount] =
    sql"""
        $selectCount
        $fromArticle
        WHERE $followedByUser
        """
      .query(int8.cimap[ArticlesCount])

  val insertArticle: Command[ArticleId ~ Slug ~ NewArticle ~ UserId ~ CreateDateTime ~ UpdateDateTime] =
    sql"""
        INSERT INTO articles (uuid, slug, title, description, body, author_id, created_at, updated_at)
        VALUES (${uuid.cimap[ArticleId]}, ${varchar.cimap[Slug]}, ${varchar.cimap[Title]}, ${varchar
      .cimap[Description]}, ${varchar
      .cimap[Body]}, ${uuid.cimap[UserId]}, ${timestamptz(3).cimap[CreateDateTime]}, ${timestamptz(3)
      .cimap[UpdateDateTime]})
       """.command.contramap {
      case id ~ s ~ a ~ us ~ c ~ u => id ~ s ~ a.title ~ a.description ~ a.body ~ us ~ c ~ u
    }

  def insertArticleTag(tags: List[(ArticleId, ArticleTag)]): Command[tags.type] =
    sql"""
        INSERT INTO article_tags (article_id, tag)
        VALUES ${(uuid.cimap[ArticleId] ~ varchar.cimap[ArticleTag]).values.list(tags)}
       """.command

  val selectUser: Query[UserId, UserName ~ Option[Bio] ~ Option[Image]] =
    sql"""
        SELECT username, bio, image
        FROM users
        WHERE uuid = ${uuid.cimap[UserId]}
       """.query(varchar.cimap[UserName] ~ varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt)

  val selectArticleIdAndAuthorId: Query[Slug, ArticleId ~ UserId] =
    sql"""
        SELECT uuid, author_id
        FROM articles
        WHERE slug = ${varchar.cimap[Slug]}
       """.query(uuid.cimap[ArticleId] ~ uuid.cimap[UserId])

  val deleteArticle: Command[ArticleId] =
    sql"""
        DELETE FROM articles
        WHERE uuid = ${uuid.cimap[ArticleId]}
       """.command

  val deleteArticleTags: Command[ArticleId] =
    sql"""
        DELETE FROM article_tags
        WHERE article_id = ${uuid.cimap[ArticleId]}
       """.command

}
