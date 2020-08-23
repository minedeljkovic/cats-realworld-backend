package conduit.http.routes.secured

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import org.http4s.server.Router
import conduit.algebras.Auth
import conduit.domain.user._
import conduit.effects._
import conduit.http.json._

final class UserRoutes[F[_]: Defer: JsonDecoder: MonadThrow](
    auth: Auth[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/user"

  private val httpRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {

    case GET -> Root as user =>
      Ok(UserResponse(user))

    case ar @ PUT -> Root as user =>
      ar.req.asJsonDecode[UpdateUserRequest].flatMap {
        case UpdateUserRequest(update) =>
          auth
            .updateUser(user)(
              update.email.map(_.toDomain),
              update.username.map(_.toDomain),
              update.password.map(_.toDomain),
              update.image.map(_.toDomain),
              update.bio.map(_.toDomain)
            )
            .flatMap { user =>
              Ok(UserResponse(user))
            }
      }

  }

  def routes(authMiddleware: AuthMiddleware[F, User]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(httpRoutes)
  )

}
