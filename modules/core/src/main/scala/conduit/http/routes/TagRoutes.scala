package conduit.http.routes

import cats._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import conduit.algebras.Tags
import conduit.domain.tag._
import conduit.http.json._

final class TagRoutes[F[_]: Defer: Monad](
    tags: Tags[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/tags"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      Ok(tags.all.map(TagsResponse(_)))
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
