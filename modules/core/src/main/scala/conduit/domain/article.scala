package conduit.domain

import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import java.util.UUID
import scala.util.control.NoStackTrace
import java.time.OffsetDateTime
import conduit.domain.user._
import conduit.domain.profile._
import cats.data.NonEmptyList
import cats.kernel.Eq

object article {

  @newtype case class ArticleId(value: UUID)
  @newtype case class Slug(value: String)
  @newtype case class Title(value: String) {
    def toSlug(): Slug = Slug(value.replace(' ', '-').toLowerCase())
  }
  @newtype case class Description(value: String)
  @newtype case class Body(value: String)

  @newtype case class CreateDateTime(value: OffsetDateTime)
  implicit val eqCreateDateTime = Eq.fromUniversalEquals[CreateDateTime]

  @newtype case class UpdateDateTime(value: OffsetDateTime)
  implicit val eqUpdateDateTime = Eq.fromUniversalEquals[UpdateDateTime]

  @newtype case class ArticleTag(value: String)

  @newtype case class FavoritedStatus(value: Boolean)
  implicit val eqFavoritedStatus = Eq.fromUniversalEquals[FavoritedStatus]

  @newtype case class FavoritesCount(value: Long)
  implicit val eqFavoritesCount = Eq.fromUniversalEquals[FavoritesCount]

  @newtype case class ArticlesCount(value: Long)
  implicit val eqArticlesCount = Eq.fromUniversalEquals[ArticlesCount]

  case class NewArticle(
      title: Title,
      description: Description,
      body: Body
  )

  case class Author(
      username: UserName,
      bio: Option[Bio],
      image: Option[Image],
      following: FollowingStatus
  )

  case class Article(
      slug: Slug,
      title: Title,
      description: Description,
      body: Body,
      tagList: List[ArticleTag],
      createdAt: CreateDateTime,
      updatedAt: UpdateDateTime,
      favorited: FavoritedStatus,
      favoritesCount: FavoritesCount,
      author: Author
  )
  case class ArticleResponse(article: Article)
  case class ArticlesResponse(articles: List[Article], articlesCount: ArticlesCount)

  // --------- create article -----------

  @newtype case class TitleParam(value: NonEmptyString)
  @newtype case class DescriptionParam(value: NonEmptyString)
  @newtype case class BodyParam(value: NonEmptyString)
  @newtype case class TagListParam(value: NonEmptyList[NonEmptyString])

  case class CreateArticleParam(
      title: TitleParam,
      description: DescriptionParam,
      body: BodyParam,
      tagList: Option[TagListParam]
  ) {
    def toDomain: CreateArticle =
      CreateArticle(
        NewArticle(
          Title(title.value.value),
          Description(description.value.value),
          Body(body.value.value)
        ),
        tagList
          .map {
            _.value.toList
              .map { t =>
                ArticleTag(t.value)
              }
          }
          .getOrElse(Nil)
      )
  }
  case class CreateArticleRequest(article: CreateArticleParam)

  case class CreateArticle(
      article: NewArticle,
      tagList: List[ArticleTag]
  )

  case class SlugInUse(slug: Slug) extends NoStackTrace

  // --------- update article -----------

  case class UpdateArticleParam(
      title: Option[TitleParam],
      description: Option[DescriptionParam],
      body: Option[BodyParam]
  ) {
    def toDomain: UpdateArticle =
      UpdateArticle(
        title.map(t => Title(t.value.value)),
        description.map(d => Description(d.value.value)),
        body.map(b => Body(b.value.value))
      )
  }
  case class UpdateArticleRequest(user: UpdateArticleParam)

  case class UpdateArticle(
      title: Option[Title],
      description: Option[Description],
      body: Option[Body]
  )

  // --------- delete article -----------

  case class CurrentUserNotAuthor(authorId: UserId) extends NoStackTrace

  // --------- filter articlea -----------

  @newtype case class ArticleTagParam(value: NonEmptyString) {
    def toDomain: ArticleTag = ArticleTag(value.value.toLowerCase)
  }

  case class ArticleCriteria(
      tag: Option[ArticleTag],
      author: Option[UserName],
      favoritingUser: Option[UserName]
  )
}
