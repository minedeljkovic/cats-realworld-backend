package conduit.algebras

import cats.effect._
import cats.implicits.{ catsSyntaxEq => _, _ }
import ciris._
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.ops._
import natchez.Trace.Implicits.noop // needed for skunk
import conduit.arbitraries._
import conduit.config.data.PasswordSalt
import conduit.domain._
import conduit.domain.user._
import skunk._
import suite._

class PostgresTest extends ResourceSuite[Resource[IO, Session[IO]]] {

  // For it:tests, one test is enough
  val MaxTests: PropertyCheckConfigParam = MinSuccessful(1)

  lazy val salt = Secret("53kr3t": NonEmptyString).coerce[PasswordSalt]

  override def resources =
    Session.pooled[IO](
      host = "localhost",
      port = 5432,
      user = "postgres",
      database = "store",
      max = 10
    )

  withResources { pool =>
    test("Users") {
      forAll(MaxTests) { gens: (UserName, Email, UserName, Email, Password, Password, Image, Bio) =>
        {
          val (un1, em1, un2, em2, pw1, pw2, img, bio) = gens
          IOAssertion {
            for {
              c <- LiveCrypto.make[IO](salt)
              u <- LiveUsers.make[IO](pool, c)
              d <- u.create(un1, em1, pw1)
              x <- u.find(em1, pw1)
              y <- u.find(em1, "foo".coerce[Password])
              z <- u.create(un1, em2, pw1).attempt
              v <- u.create(un2, em1, pw1).attempt
              _ <- u.update(d, Some(em2), Some(un2), Some(pw2), Some(img), Some(bio))
              w <- u.find(em2, pw2)
            } yield assert(
              x.count(_.id === d) === 1 && y.isEmpty && z.isLeft && v.isLeft &&
              w.count { u =>
                u.username === un2 && u.image === Some(img) && u.bio === Some(bio)
              } === 1
            )
          }
        }
      }
    }

  }
}
