package conduit.http.routes.optsecured

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import org.http4s.server.Router
import conduit.algebras.Articles
import conduit.algebras.Comments
import conduit.domain.article._
import conduit.domain.comment._
import conduit.domain.user._
import conduit.effects._
import conduit.http.json._
import conduit.http.params._
import conduit.ext.skunkx._

final class ArticlesRoutes[F[_]: Defer: JsonDecoder: MonadThrow](
    articles: Articles[F],
    comments: Comments[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/articles"

  object LimitQueryParam extends OptionalQueryParamDecoderMatcher[LimitParam]("limit")
  object OffsetQueryParam extends OptionalQueryParamDecoderMatcher[OffsetParam]("offset")

  object ArticleTagQueryParam extends OptionalQueryParamDecoderMatcher[ArticleTagParam]("tag")
  object AuthorQueryParam extends OptionalQueryParamDecoderMatcher[UserNameParam]("author")
  object FavoritedQueryParam extends OptionalQueryParamDecoderMatcher[UserNameParam]("favorited")

  private val httpRoutes: AuthedRoutes[Option[User], F] = AuthedRoutes.of {

    case GET -> Root :? ArticleTagQueryParam(tag) :? AuthorQueryParam(author) :?
            FavoritedQueryParam(favorited) :? LimitQueryParam(limit) :? OffsetQueryParam(offset) as optUser =>
      articles
        .filter(optUser.map(_.id))(
          ArticleCriteria(tag.map(_.toDomain), author.map(_.toDomain), favorited.map(_.toDomain)),
          limit.map(_.toDomain),
          offset.map(_.toDomain)
        )
        .flatMap {
          case (articles, count) =>
            Ok(ArticlesResponse(articles, count))
        }

    case GET -> Root / "feed" :? LimitQueryParam(limit) :? OffsetQueryParam(offset) as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        articles
          .feed(user.id)(limit.map(_.toDomain), offset.map(_.toDomain))
          .flatMap {
            case (articles, count) =>
              Ok(ArticlesResponse(articles, count))
          }
      }

    case GET -> Root / slug as optUser =>
      articles
        .find(optUser.map(_.id))(Slug(slug))
        .flatMap {
          case Some(article) => Ok(ArticleResponse(article))
          case None          => NotFound(s"Article not found for slug: $slug")
        }

    case ar @ POST -> Root as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        ar.req
          .asJsonDecode[CreateArticleRequest]
          .flatMap {
            case CreateArticleRequest(createArticle) =>
              val article = createArticle.toDomain.article
              val tagList = createArticle.toDomain.tagList
              articles
                .create(user.id)(article, tagList)
                .flatMap { article =>
                  Ok(ArticleResponse(article))
                }
          }
          .recoverWith {
            case SlugInUse(u) => Conflict(u.value)
          }
      }

    case DELETE -> Root / slug as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        articles
          .delete(user.id)(Slug(slug))
          .flatMap {
            case Some(unit) => Ok(unit)
            case None       => NotFound(s"Article not found for slug: $slug")
          }
          .recoverWith {
            case _: CurrentUserNotAuthor => Forbidden("not author")
          }
      }

    case ar @ PUT -> Root / slug as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        ar.req
          .asJsonDecode[UpdateArticleRequest]
          .flatMap {
            case UpdateArticleRequest(updateArticle) =>
              articles
                .update(user.id)(Slug(slug))(updateArticle.toDomain)
                .flatMap {
                  case Some(article) => Ok(ArticleResponse(article))
                  case None          => NotFound(s"Article not found for slug: $slug")
                }
          }
          .recoverWith {
            case _: CurrentUserNotAuthor => Forbidden("not author")
            case SlugInUse(u)            => Conflict(u.value)
          }
      }

    case POST -> Root / slug / "favorite" as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        articles
          .favorite(user.id)(Slug(slug))
          .flatMap {
            case Some(article) => Ok(ArticleResponse(article))
            case None          => NotFound(s"Article not found for slug: $slug")
          }
      }

    case DELETE -> Root / slug / "favorite" as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        articles
          .unfavorite(user.id)(Slug(slug))
          .flatMap {
            case Some(article) => Ok(ArticleResponse(article))
            case None          => NotFound(s"Article not found for slug: $slug")
          }
      }

    case ar @ POST -> Root / slug / "comments" as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        ar.req
          .asJsonDecode[CreateCommentRequest]
          .flatMap {
            case CreateCommentRequest(comment) =>
              comments
                .create(user.id)(Slug(slug))(comment.toBody)
                .flatMap {
                  case Some(comment) => Ok(CommentResponse(comment))
                  case None          => NotFound(s"Article not found for slug: $slug")
                }
          }
      }

    case GET -> Root / slug / "comments" as optUser =>
      comments
        .find(optUser.map(_.id))(Slug(slug))
        .flatMap { comments =>
          Ok(CommentsResponse(comments))
        }

    case DELETE -> Root / slug / "comments" / IntVar(id) as optUser =>
      optUser.fold(Forbidden("not authenticated")) { user =>
        comments
          .delete(user.id)(Slug(slug), CommentId(id))
          .flatMap {
            case Some(unit) => Ok(unit)
            case None       => NotFound(s"Comment with id: $id not found for slug: $slug")
          }
          .recoverWith {
            case _: CurrentUserNotCommentAuthor => Forbidden("not author")
          }
      }

  }

  def routes(authMiddleware: AuthMiddleware[F, Option[User]]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(httpRoutes)
  )

}
