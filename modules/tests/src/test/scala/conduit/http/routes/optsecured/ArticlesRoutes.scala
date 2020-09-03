package conduit.http.routes.optsecured

import cats.data.Kleisli
import cats.effect._
import conduit.algebras.Articles
import conduit.algebras.Comments
import conduit.arbitraries._
import conduit.domain.article._
import conduit.domain.comment._
import conduit.domain.tag._
import conduit.domain.user._
import conduit.ext.skunkx._
import conduit.http.json._
import dev.profunktor.auth.jwt._
import java.util.UUID
import org.http4s._
import org.http4s.Method._
import org.http4s.client.dsl.io._
import org.http4s.server.AuthMiddleware
import suite._

class ArticlesRoutesSpec extends HttpTestSuite {

  val authUser = Some(
    User(
      UserId(UUID.randomUUID),
      Email("email"),
      JwtToken("token"),
      UserName("user"),
      None,
      None
    )
  )
  val authMiddleware: AuthMiddleware[IO, Option[User]] =
    AuthMiddleware(Kleisli.pure(authUser))

  val noopArticles = new TestArticles
  val noopComments = new TestComments

  test("GET all articles [OK]") {
    forAll { (as: List[Article], ac: ArticlesCount) =>
      val articles = new TestArticles {
        override def filter(userId: Option[UserId])(
            criteria: ArticleCriteria,
            limit: Option[Limit],
            offset: Option[Offset]
        ): IO[(List[Article], ArticlesCount)] = IO.pure((as, ac))
      }

      IOAssertion {
        GET(Uri.uri("/articles")).flatMap { req =>
          val routes = new ArticlesRoutes[IO](articles, noopComments).routes(authMiddleware)
          assertHttp(routes, req)(Status.Ok, ArticlesResponse(as, ac))
        }
      }
    }
  }

  test("GET article by slug [OK]") {
    forAll { (a: Article) =>
      val articles = new TestArticles {
        override def find(userId: Option[UserId])(slug: Slug): IO[Option[Article]] = IO.pure(Some(a))
      }

      IOAssertion {
        GET(Uri.uri("/articles/slug")).flatMap { req =>
          val routes = new ArticlesRoutes[IO](articles, noopComments).routes(authMiddleware)
          assertHttp(routes, req)(Status.Ok, ArticleResponse(a))
        }
      }
    }
  }

  test("GET article by slug [NotFound]") {
    forAll { (s: Slug) =>
      IOAssertion {
        GET(Uri.unsafeFromString(s"/articles/$s")).flatMap { req =>
          val routes = new ArticlesRoutes[IO](noopArticles, noopComments).routes(authMiddleware)
          assertHttp(routes, req)(Status.NotFound, s"Article not found for slug: $s")
        }
      }
    }
  }

}

protected class TestArticles extends Articles[IO] {
  def find(userId: Option[UserId])(slug: Slug): IO[Option[Article]] = IO.pure(None)
  def create(userId: UserId)(article: NewArticle, tagList: List[ArticleTag]): IO[Article] =
    IO.raiseError(new NotImplementedError)
  def delete(userId: UserId)(slug: Slug): IO[Option[Unit]] = IO.pure(None)
  def feed(userId: UserId)(limit: Option[Limit], offset: Option[Offset]): IO[(List[Article], ArticlesCount)] =
    IO.pure((Nil, ArticlesCount(0)))
  def filter(userId: Option[UserId])(
      criteria: ArticleCriteria,
      limit: Option[Limit],
      offset: Option[Offset]
  ): IO[(List[Article], ArticlesCount)]                                               = IO.pure((Nil, ArticlesCount(0)))
  def update(userId: UserId)(slug: Slug)(article: UpdateArticle): IO[Option[Article]] = IO.pure(None)
  def favorite(userId: UserId)(slug: Slug): IO[Option[Article]]                       = IO.pure(None)
  def unfavorite(userId: UserId)(slug: Slug): IO[Option[Article]]                     = IO.pure(None)
}

protected class TestComments extends Comments[IO] {
  def find(userId: Option[UserId])(slug: Slug): IO[List[Comment]]                = IO.pure(Nil)
  def create(userId: UserId)(slug: Slug)(body: CommentBody): IO[Option[Comment]] = IO.pure(None)
  def delete(userId: UserId)(slug: Slug, id: CommentId): IO[Option[Unit]]        = IO.pure(None)
}
