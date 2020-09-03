package conduit.algebras

import cats.effect.Sync
import cats.implicits._
import conduit.config.data.TokenExpiration
import conduit.domain.user._
import conduit.effects._
import conduit.http.json._
import dev.profunktor.auth.jwt.JwtToken
import dev.profunktor.redis4cats.RedisCommands
import io.circe.syntax._
import io.circe.parser.decode
import pdi.jwt.JwtClaim

trait Auth[F[_]] {
  def login(email: Email, password: Password): F[User]
  def registerUser(username: UserName, email: Email, password: Password): F[User]
  def findUser(token: JwtToken)(claim: JwtClaim): F[Option[User]]
  def updateUser(user: User)(
      email: Option[Email],
      username: Option[UserName],
      password: Option[Password],
      image: Option[Image],
      bio: Option[Bio]
  ): F[User]
}

object LiveAuth {
  def make[F[_]: Sync](
      tokenExpiration: TokenExpiration,
      tokens: Tokens[F],
      users: Users[F],
      redis: RedisCommands[F, String, String]
  ): F[Auth[F]] =
    Sync[F].delay(
      new LiveAuth(tokenExpiration, tokens, users, redis)
    )
}

final class LiveAuth[F[_]: MonadThrow] private (
    tokenExpiration: TokenExpiration,
    tokens: Tokens[F],
    users: Users[F],
    redis: RedisCommands[F, String, String]
) extends Auth[F] {

  private val TokenExpiration = tokenExpiration.value

  def login(email: Email, password: Password): F[User] =
    users.find(email, password).flatMap {
      case None => InvalidUserOrPassword(email).raiseError[F, User]
      case Some(UnauthenticatedUser(id, _, username, bio, image)) =>
        redis.get(email.value).flatMap {
          case Some(t) => redis.get(t)
          case n       => n.pure[F]
        } flatMap {
            case Some(userJson) => decode[User](userJson).pure[F].rethrow
            case None =>
              tokens.create.map(User(id, email, _, username, bio, image)).flatTap { user =>
                redis.setEx(
                  user.token.value,
                  user.asJson.noSpaces,
                  TokenExpiration
                ) *>
                  redis.setEx(email.value, user.token.value, TokenExpiration)
              }
          }
    }

  def registerUser(username: UserName, email: Email, password: Password): F[User] =
    for {
      id <- users.create(username, email, password)
      t <- tokens.create
      user = User(id, email, t, username, None, None)
      u    = user.asJson.noSpaces
      _ <- redis.setEx(t.value, u, TokenExpiration)
      _ <- redis.setEx(email.value, t.value, TokenExpiration)
    } yield user

  def findUser(token: JwtToken)(claim: JwtClaim): F[Option[User]] =
    redis
      .get(token.value)
      .map(_.flatMap { u =>
        decode[User](u).toOption
      })

  def updateUser(user: User)(
      email: Option[Email],
      username: Option[UserName],
      password: Option[Password],
      image: Option[Image],
      bio: Option[Bio]
  ): F[User] =
    for {
      _ <- users.update(user.id, email, username, password, image, bio)
      updatedUser <- user
                      .copy(
                        email = email.getOrElse(user.email),
                        username = username.getOrElse(user.username),
                        image = image.orElse(user.image),
                        bio = bio.orElse(user.bio)
                      )
                      .pure[F]
      _ <- redis.setEx(updatedUser.token.value, updatedUser.asJson.noSpaces, TokenExpiration)
      _ <- redis.setEx(updatedUser.email.value, updatedUser.token.value, TokenExpiration)
    } yield updatedUser

}
