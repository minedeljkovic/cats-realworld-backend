package conduit.http.routes.secured

import cats._
import cats.implicits._
import conduit.algebras.Profiles
import conduit.domain.profile._
import conduit.domain.user._
import conduit.http.json._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import org.http4s.server.Router

final class ProfilesRoutes[F[_]: Defer: Monad](
    profiles: Profiles[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/profiles"

  private val httpRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {

    case GET -> Root / username as user =>
      profiles
        .find(user.id)(UserName(username))
        .flatMap {
          case Some(profile) => Ok(ProfileResponse(profile))
          case None          => NotFound(s"Profile not found for username: $username")
        }

    case POST -> Root / username / "follow" as user =>
      profiles
        .follow(user.id)(UserName(username))
        .flatMap { profile =>
          Ok(ProfileResponse(profile))
        }

    case DELETE -> Root / username / "follow" as user =>
      profiles
        .unfollow(user.id)(UserName(username))
        .flatMap { profile =>
          Ok(ProfileResponse(profile))
        }

  }

  def routes(authMiddleware: AuthMiddleware[F, User]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(httpRoutes)
  )

}
