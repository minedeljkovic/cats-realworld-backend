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
      forAll(MaxTests) { (username: UserName, email: Email, password: Password) =>
        IOAssertion {
          for {
            c <- LiveCrypto.make[IO](salt)
            u <- LiveUsers.make[IO](pool, c)
            d <- u.create(username, email, password)
            x <- u.find(email, password)
            y <- u.find(email, "foo".coerce[Password])
            z <- u.create(username, email, password).attempt
          } yield assert(
            x.count(_.id === d) === 1 && y.isEmpty && z.isLeft
          )
        }
      }
    }

  }
}
