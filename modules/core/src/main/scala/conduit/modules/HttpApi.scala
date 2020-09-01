package conduit.modules

import cats.effect._
import cats.implicits._
import dev.profunktor.auth.JwtAuthMiddleware
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware._
import scala.concurrent.duration._
import conduit.domain.user._
import conduit.http.routes._
import conduit.http.routes.secured._
import conduit.http.routes.optsecured._
import conduit.ext.OptionalJwtAuthMiddleware

object HttpApi {
  def make[F[_]: Concurrent: Timer](
      algebras: Algebras[F],
      security: Security[F]
  ): F[HttpApi[F]] =
    Sync[F].delay(
      new HttpApi[F](
        algebras,
        security
      )
    )
}

final class HttpApi[F[_]: Concurrent: Timer] private (
    algebras: Algebras[F],
    security: Security[F]
) {
  private val usersMiddleware =
    JwtAuthMiddleware[F, User](security.userJwtAuth.value, security.auth.findUser)

  private val optionUsersMiddleware =
    OptionalJwtAuthMiddleware[F, User](security.userJwtAuth.value, security.auth.findUser)

  // Auth routes
  private val loginRoutes = new LoginRoutes[F](security.auth).routes
  private val usersRoutes = new UsersRoutes[F](security.auth).routes

  // Open routes
  private val healthRoutes = new HealthRoutes[F](algebras.healthCheck).routes
  private val tagRoutes    = new TagRoutes[F](algebras.tags).routes

  // Secured routes
  private val userRoutes     = new UserRoutes[F](security.auth).routes(usersMiddleware)
  private val profilesRoutes = new ProfilesRoutes[F](algebras.profiles).routes(usersMiddleware)

  // Optionally secured routes
  private val articlesRoutes =
    new ArticlesRoutes[F](algebras.articles, algebras.comments).routes(optionUsersMiddleware)

  // Combining all the http routes
  private val routes: HttpRoutes[F] =
    healthRoutes <+> tagRoutes <+> loginRoutes <+> usersRoutes <+> userRoutes <+> profilesRoutes <+> articlesRoutes

  private val middleware: HttpRoutes[F] => HttpRoutes[F] = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    } andThen { http: HttpRoutes[F] =>
      CORS(http, CORS.DefaultCORSConfig)
    } andThen { http: HttpRoutes[F] =>
      Timeout(60.seconds)(http)
    }
  }

  private val loggers: HttpApp[F] => HttpApp[F] = {
    { http: HttpApp[F] =>
      RequestLogger.httpApp(true, true)(http)
    } andThen { http: HttpApp[F] =>
      ResponseLogger.httpApp(true, true)(http)
    }
  }

  val httpApp: HttpApp[F] = loggers(middleware(routes).orNotFound)

}
