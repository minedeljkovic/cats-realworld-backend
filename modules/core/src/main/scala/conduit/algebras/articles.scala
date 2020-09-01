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
  def delete(userId: UserId)(slug: Slug): F[Option[Unit]]
  def feed(userId: UserId)(limit: Option[Limit], offset: Option[Offset]): F[(List[Article], ArticlesCount)]
  def filter(userId: Option[UserId])(
      criteria: ArticleCriteria,
      limit: Option[Limit],
      offset: Option[Offset]
  ): F[(List[Article], ArticlesCount)]
  def update(userId: UserId)(slug: Slug)(article: UpdateArticle): F[Option[Article]]
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
            uuid ~ slug ~ title ~ description ~ body ~ createdAt ~ updatedAt ~ favorited ~ favoritesCount ~ userId ~ username ~ bio ~ image ~ following,
            tags
            ) =>
          Article(
            uuid,
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
              userId,
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
      session.prepare(selectArticleBySlug).use { q =>
        joinedToList(q.stream(userId ~ slug, 64)).map(_.headOption)
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
                id,
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
                  userId,
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

  def delete(userId: UserId)(slug: Slug): F[Option[Unit]] =
    sessionPool.use { session =>
      (
        session.prepare(selectArticleIdAndAuthorId),
        session.prepare(deleteArticleTags),
        session.prepare(deleteArticle)
      ).tupled.use {
        case (selectArticleQuery, deleteArticleTagsCmd, deleteArticleCmd) =>
          selectArticleQuery.option(slug).flatMap {
            case Some(id ~ authorId) if authorId == userId =>
              for {
                _ <- deleteArticleTagsCmd.execute(id)
                _ <- deleteArticleCmd.execute(id)
                // TODO: handle favorites and comments existence
              } yield Some(())
            case Some(_ ~ authorId) =>
              CurrentUserNotAuthor(authorId).raiseError[F, Option[Unit]]
            case None => none[Unit].pure[F]
          }
      }
    }

  def feed(userId: UserId)(limit: Option[Limit], offset: Option[Offset]): F[(List[Article], ArticlesCount)] =
    sessionPool.use { session =>
      (
        // it's to complex for me to do this in one query
        session.prepare(selectArticleFollowedByUser),
        session.prepare(countArticleFollowedByUser)
      ).tupled.use {
        case (query, countQuery) =>
          for {
            list <- joinedToList(query.stream(userId ~ limit ~ offset, 64))
            count <- countQuery.unique(userId)
          } yield (list, count)
      }
    }

  def filter(userId: Option[UserId])(
      criteria: ArticleCriteria,
      limit: Option[Limit],
      offset: Option[Offset]
  ): F[(List[Article], ArticlesCount)] =
    sessionPool.use { session =>
      (
        // it's to complex for me to do this in one query
        session.prepare(selectArticleByCriteria),
        session.prepare(countArticleByCriteria)
      ).tupled.use {
        case (query, countQuery) =>
          for {
            list <- joinedToList(query.stream(userId ~ criteria ~ limit ~ offset, 64))
            count <- countQuery.unique(criteria)
          } yield (list, count)
      }
    }

  def update(userId: UserId)(slug: Slug)(a: UpdateArticle): F[Option[Article]] =
    sessionPool.use { session =>
      (
        session.prepare(selectArticleBySlug),
        session.prepare(updateArticle)
      ).tupled.use {
        case (selectArticleQuery, updateArticleCmd) =>
          joinedToList(selectArticleQuery.stream(Some(userId) ~ slug, 64)).map(_.headOption).flatMap {
            case Some(article) if article.author.uuid == userId =>
              for {
                nowDateTime <- now
                // change slug if title is changed
                slug = a.title.map(_.toSlug())
                // change  updatedAt if anything is changed
                updatedAt = (a.title orElse a.description orElse a.body).map(_ => UpdateDateTime(nowDateTime))
                _ <- updateArticleCmd
                      .execute(slug ~ a.title ~ a.description ~ a.body ~ updatedAt ~ article.uuid)
                      .handleErrorWith {
                        case SqlState.UniqueViolation(e) if e.constraintName == Some("unq_slug") =>
                          SlugInUse(slug.get).raiseError[F, Completion]
                      }
              } yield Some(
                article.copy(
                  slug = slug.getOrElse(article.slug),
                  title = a.title.getOrElse(article.title),
                  description = a.description.getOrElse(article.description),
                  body = a.body.getOrElse(article.body),
                  updatedAt = updatedAt.getOrElse(article.updatedAt)
                )
              )
            case Some(article) =>
              CurrentUserNotAuthor(article.author.uuid).raiseError[F, Option[Article]]
            case None => none[Article].pure[F]
          }
      }
    }

}

private object ArticleQueries {

  private val selectArticle: Fragment[Option[UserId]] =
    sql"""
        SELECT a.uuid, a.slug, a.title, a.description, a.body, a.created_at, a.updated_at,
               CASE
                 WHEN (${uuid.cimap[UserId].opt} IS NOT NULL AND EXISTS (
                   SELECT *
                   FROM favorites
                   WHERE user_id = ${uuid.cimap[UserId].opt}
                 )) THEN true
                 ELSE false
               END as favorited,
               (SELECT COUNT(*) FROM favorites f WHERE f.article_id = a.uuid) as favorites_count,
               au.uuid, au.username, au.bio, au.image,
               CASE
                 WHEN (${uuid.cimap[UserId].opt} IS NOT NULL AND EXISTS (
                   SELECT * FROM followers
                   WHERE follower_id = ${uuid.cimap[UserId].opt}
                     AND followed_id = au.uuid
                 )) THEN true
                 ELSE false
               END as following,
               t.tag
       """.contramap(id => id ~ id ~ id ~ id)

  private val selectCount: Fragment[Void] = sql"""SELECT COUNT(DISTINCT a.uuid)"""

  private val fromArticle: Fragment[Void] =
    sql"""
        FROM Articles a
        JOIN users au on au.uuid = a.author_id
        LEFT JOIN article_tags t ON t.article_id = a.uuid
      """

  type SelectArticleResult =
    ArticleId ~ Slug ~ Title ~ Description ~ Body ~ CreateDateTime ~ UpdateDateTime ~ FavoritedStatus ~
        FavoritesCount ~ UserId ~ UserName ~ Option[Bio] ~ Option[Image] ~ FollowingStatus

  private val selectArticleResultCodec: Codec[SelectArticleResult] =
    uuid.cimap[ArticleId] ~ varchar.cimap[Slug] ~ varchar.cimap[Title] ~ varchar.cimap[Description] ~
        varchar.cimap[Body] ~ timestamptz(3).cimap[CreateDateTime] ~ timestamptz(3).cimap[UpdateDateTime] ~
        bool.cimap[FavoritedStatus] ~ int8.cimap[FavoritesCount] ~ uuid.cimap[UserId] ~ varchar.cimap[UserName] ~
        varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt ~ bool.cimap[FollowingStatus]

  private val slugEquals: Fragment[Slug] = sql"""slug = ${varchar.cimap[Slug]}"""

  val selectArticleBySlug: Query[Option[UserId] ~ Slug, SelectArticleResult ~ Option[ArticleTag]] =
    sql"""
      $selectArticle
      $fromArticle
      WHERE $slugEquals
    """.query(selectArticleResultCodec ~ varchar.cimap[ArticleTag].opt)

  private val followedByUser: Fragment[UserId] =
    sql"""
        EXISTS (
          SELECT * FROM followers
          WHERE follower_id = ${uuid.cimap[UserId]}
            AND followed_id = au.uuid
        )
      """

  private val limitOffsetOrderMostRecent: Fragment[Option[Limit] ~ Option[Offset]] =
    limitOffset(sql"""ORDER BY a.updated_at DESC""").contramap {
      case l ~ o => (Void ~ l.orElse(Some(Limit(20))) ~ o.orElse(Some(Offset(0))))
    }

  val selectArticleFollowedByUser
      : Query[UserId ~ Option[Limit] ~ Option[Offset], SelectArticleResult ~ Option[ArticleTag]] =
    sql"""
        $selectArticle
        $fromArticle
        WHERE $followedByUser
        $limitOffsetOrderMostRecent
        """
      .query(selectArticleResultCodec ~ varchar.cimap[ArticleTag].opt)
      .contramap {
        case id ~ l ~ o => Some(id) ~ id ~ (l ~ o)
      }

  val countArticleFollowedByUser: Query[UserId, ArticlesCount] =
    sql"""
        $selectCount
        FROM Articles a
        WHERE $followedByUser
        """
      .query(int8.cimap[ArticlesCount])

  private val byTag: Fragment[Option[ArticleTag]] =
    sql"""
      ${varchar.cimap[ArticleTag].opt} IS NULL OR EXISTS (
                                                    SELECT * FROM article_tags
                                                    WHERE tag = ${varchar.cimap[ArticleTag].opt}
                                                  )
    """.contramap { o =>
      o ~ o
    }

  private val byAuthor: Fragment[Option[UserName]] =
    sql"""
      ${varchar.cimap[UserName].opt} IS NULL OR au.username = ${varchar.cimap[UserName].opt}
    """.contramap { o =>
      o ~ o
    }

  private val byFavoritingUser: Fragment[Option[UserName]] =
    sql"""
      ${varchar.cimap[UserName].opt} IS NULL OR EXISTS (
                                                    SELECT *
                                                    FROM favorites f
                                                    JOIN users u ON u.uuid = f.user_id
                                                    WHERE u.username = ${varchar.cimap[UserName].opt}
                                                  )
    """.contramap { o =>
      o ~ o
    }

  val selectArticleByCriteria: Query[
    Option[UserId] ~ ArticleCriteria ~ Option[Limit] ~ Option[Offset],
    SelectArticleResult ~ Option[ArticleTag]
  ] =
    sql"""
      $selectArticle
      $fromArticle
      WHERE ($byTag)
      AND ($byAuthor)
      AND ($byFavoritingUser)
      $limitOffsetOrderMostRecent
    """
      .query(selectArticleResultCodec ~ varchar.cimap[ArticleTag].opt)
      .contramap {
        case i ~ ArticleCriteria(t, a, f) ~ o ~ l => i ~ t ~ a ~ f ~ (o ~ l)
      }

  val countArticleByCriteria: Query[ArticleCriteria, ArticlesCount] =
    sql"""
      $selectCount
      FROM Articles a
      JOIN users au on au.uuid = a.author_id
      WHERE ($byTag)
      AND ($byAuthor)
      AND ($byFavoritingUser)
    """
      .query(int8.cimap[ArticlesCount])
      .contramap {
        case ArticleCriteria(t, a, f) => t ~ a ~ f
      }

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

  val updateArticle: Command[
    Option[Slug] ~ Option[Title] ~ Option[Description] ~ Option[Body] ~ Option[UpdateDateTime] ~ ArticleId
  ] =
    sql"""
        UPDATE articles a
        SET slug           = coalesce(${varchar.cimap[Slug].opt}, a.slug),
            title          = coalesce(${varchar.cimap[Title].opt}, a.title),
            description    = coalesce(${varchar.cimap[Description].opt}, a.description),
            body           = coalesce(${varchar.cimap[Body].opt}, a.body),
            updated_at     = coalesce(${timestamptz(3).cimap[UpdateDateTime].opt}, a.updated_at)
        WHERE uuid = ${uuid.cimap[ArticleId]}
       """.command
}
