package conduit.http.routes

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.circe.JsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import conduit.algebras.Auth
import conduit.domain.user._
import conduit.effects._
import conduit.http.decoder._
import conduit.http.json._

final class LoginRoutes[F[_]: Defer: JsonDecoder: MonadThrow](
    auth: Auth[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/users"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ POST -> Root / "login" =>
      req.decodeR[LoginUserRequest] {
        case LoginUserRequest(user) =>
          auth
            .login(user.email.toDomain, user.password.toDomain)
            .flatMap { user =>
              Ok(UserResponse(user))
            }
            .recoverWith {
              case InvalidUserOrPassword(_) => Forbidden()
            }
      }

  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
