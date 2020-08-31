package conduit.http.routes.optsecured

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import org.http4s.server.Router
import conduit.algebras.Articles
import conduit.domain.article._
import conduit.domain.user._
import conduit.effects._
import conduit.http.json._
import conduit.http.params._
import conduit.ext.skunkx._

final class ArticlesRoutes[F[_]: Defer: JsonDecoder: MonadThrow](
    articles: Articles[F]
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
          .flatMap { _ =>
            Ok(())
          }
          .recoverWith {
            case _: CurrentUserNotAuthor => Forbidden("not author")
          }
      }

  }

  def routes(authMiddleware: AuthMiddleware[F, Option[User]]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(httpRoutes)
  )

}
