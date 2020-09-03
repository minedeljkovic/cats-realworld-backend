package conduit.ext

import cats.MonadError
import cats.data.{ Kleisli, OptionT }
import cats.implicits._
import dev.profunktor.auth.AuthHeaders
import dev.profunktor.auth.jwt._
import org.http4s.{ AuthedRoutes, Request }
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import pdi.jwt._
import pdi.jwt.exceptions.JwtException

object OptionalJwtAuthMiddleware {
  def apply[F[_]: MonadError[*[_], Throwable], A](
      jwtAuth: JwtAuth,
      authenticate: JwtToken => JwtClaim => F[Option[A]]
  ): AuthMiddleware[F, Option[A]] = {
    val dsl = new Http4sDsl[F] {}; import dsl._

    val onFailure: AuthedRoutes[String, F] =
      Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    val authUser: Kleisli[F, Request[F], Either[String, Option[A]]] =
      Kleisli { request =>
        AuthHeaders.getBearerToken(request).fold(none[A].asRight[String].pure[F]) { token =>
          jwtDecode[F](token, jwtAuth)
            .flatMap(authenticate(token))
            .map(_.fold("not found".asLeft[Option[A]])(_.some.asRight[String]))
            .recover {
              case _: JwtException => "Invalid access token".asLeft[Option[A]]
            }
        }
      }

    AuthMiddleware(authUser, onFailure)
  }
}
