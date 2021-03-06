package conduit.algebras

import cats.Eq
import cats.effect._
import cats.implicits.{ catsSyntaxEq => _, _ }
import ciris.Secret
import conduit.arbitraries._
import conduit.config.data._
import conduit.domain._
import conduit.domain.user._
import conduit.logger.NoOp
import conduit.http.auth.users._
import dev.profunktor.auth.jwt._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import dev.profunktor.redis4cats.log4cats._
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString
import java.util.UUID
import pdi.jwt._
import scala.concurrent.duration._
import suite._

class RedisTest extends ResourceSuite[RedisCommands[IO, String, String]] {

  // For it:tests, one test is enough
  val MaxTests: PropertyCheckConfigParam = MinSuccessful(1)

  override def resources =
    Redis[IO].utf8("redis://localhost")

  lazy val tokenConfig = JwtSecretKeyConfig(Secret("bar": NonEmptyString))
  lazy val tokenExp    = TokenExpiration(30.seconds)
  lazy val jwtClaim    = JwtClaim("test")
  lazy val userJwtAuth = UserJwtAuth(JwtAuth.hmac("bar", JwtAlgorithm.HS256))

  withResources { cmd =>
    test("Authentication") {
      forAll(MaxTests) { (un1: UserName, em1: Email, em2: Email, pw: Password) =>
        IOAssertion {
          for {
            t <- LiveTokens.make[IO](tokenConfig, tokenExp)
            a <- LiveAuth.make(tokenExp, t, new TestUsers(em2), cmd)
            x <- a.findUser(JwtToken("invalid"))(jwtClaim)
            j <- a.registerUser(un1, em1, pw)
            e <- jwtDecode[IO](j.token, userJwtAuth.value).attempt
            k <- a.login(em2, pw)
            f <- jwtDecode[IO](k.token, userJwtAuth.value).attempt
            y <- a.findUser(k.token)(jwtClaim)
            w <- a.findUser(j.token)(jwtClaim)
          } yield assert(
            x.isEmpty && e.isRight && f.isRight && y.isDefined &&
              w.fold(false)(_.username === un1)
          )
        }
      }
    }
  }

}

protected class TestUsers(em: Email) extends Users[IO] {
  def find(email: Email, password: Password): IO[Option[UnauthenticatedUser]] =
    Eq[Email].eqv(email, em).guard[Option].as(UnauthenticatedUser(UserId(UUID.randomUUID), em, UserName(""), None, None)).pure[IO]
  def create(username: UserName, email: Email, password: Password): IO[UserId] =
    GenUUID[IO].make[UserId]
  def update(
      id: UserId,
      email: Option[Email],
      username: Option[UserName],
      password: Option[Password],
      image: Option[Image],
      bio: Option[Bio]
  ): IO[Unit] = ???
}
