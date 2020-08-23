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

final class UsersRoutes[F[_]: Defer: JsonDecoder: MonadThrow](
    auth: Auth[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/users"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ POST -> Root =>
      req
        .decodeR[RegisterUserRequest] {
          case RegisterUserRequest(user) =>
            auth
              .registerUser(user.username.toDomain, user.email.toDomain, user.password.toDomain)
              .flatMap { user =>
                Created(UserResponse(user))
              }
              .recoverWith {
                case UserNameInUse(u) => Conflict(u.value)
                case EmailInUse(e)    => Conflict(e.value)
              }
        }

  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
