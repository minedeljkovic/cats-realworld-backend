package conduit.algebras

import cats.effect._
import cats.implicits._
import conduit.domain.user._
import conduit.effects._
import conduit.ext.skunkx._
import skunk._
import skunk.codec.all._
import skunk.implicits._

trait Users[F[_]] {
  def find(email: Email, password: Password): F[Option[UnauthenticatedUser]]
  def create(username: UserName, email: Email, password: Password): F[UserId]
  def update(
      id: UserId,
      email: Option[Email],
      username: Option[UserName],
      password: Option[Password],
      image: Option[Image],
      bio: Option[Bio]
  ): F[Unit]
}

object LiveUsers {
  def make[F[_]: Sync](
      sessionPool: Resource[F, Session[F]],
      crypto: Crypto
  ): F[Users[F]] =
    Sync[F].delay(
      new LiveUsers[F](sessionPool, crypto)
    )
}

final class LiveUsers[F[_]: BracketThrow: GenUUID] private (
    sessionPool: Resource[F, Session[F]],
    crypto: Crypto
) extends Users[F] {
  import UserQueries._

  def find(email: Email, password: Password): F[Option[UnauthenticatedUser]] =
    sessionPool.use { session =>
      session.prepare(selectUser).use { q =>
        q.option(email).map {
          case Some(u ~ p) if p.value == crypto.encrypt(password).value => u.some
          case _                                                        => none[UnauthenticatedUser]
        }
      }
    }

  def create(username: UserName, email: Email, password: Password): F[UserId] =
    sessionPool.use { session =>
      session.prepare(insertUser).use { cmd =>
        GenUUID[F].make[UserId].flatMap { id =>
          cmd
            .execute(NewUser(id, email, username) ~ crypto.encrypt(password))
            .as(id)
            .handleErrorWith {
              case SqlState.UniqueViolation(e) if e.constraintName == Some("unq_username") =>
                UserNameInUse(username).raiseError[F, UserId]
              case SqlState.UniqueViolation(e) if e.constraintName == Some("unq_email") =>
                EmailInUse(email).raiseError[F, UserId]
            }
        }
      }
    }

  def update(
      id: UserId,
      email: Option[Email],
      username: Option[UserName],
      password: Option[Password],
      image: Option[Image],
      bio: Option[Bio]
  ): F[Unit] =
    sessionPool.use { session =>
      session.prepare(updateUser).use { cmd =>
        cmd.execute(username ~ password.map(crypto.encrypt(_)) ~ email ~ bio ~ image ~ id).void
      }
    }
}

private object UserQueries {

  val userDecoder: Decoder[UnauthenticatedUser ~ EncryptedPassword] =
    (uuid.cimap[UserId] ~ varchar.cimap[UserName] ~ varchar.cimap[EncryptedPassword] ~ varchar
          .cimap[Email] ~ varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt).map {
      case id ~ un ~ p ~ em ~ bi ~ im =>
        UnauthenticatedUser(id, em, un, bi, im) ~ p
    }

  val selectUser: Query[Email, UnauthenticatedUser ~ EncryptedPassword] =
    sql"""
        SELECT * FROM users
        WHERE email = ${varchar.cimap[Email]}
       """.query(userDecoder)

  val newUserEncoder: Encoder[NewUser ~ EncryptedPassword] =
    (uuid.cimap[UserId] ~ varchar.cimap[Email] ~ varchar.cimap[UserName] ~ varchar.cimap[EncryptedPassword]).contramap {
      case u ~ p =>
        u.id ~ u.email ~ u.username ~ p
    }

  val insertUser: Command[NewUser ~ EncryptedPassword] =
    sql"""
        INSERT INTO users (uuid, email, username, password)
        VALUES ($newUserEncoder)
       """.command

  val updateUser: Command[Option[conduit.domain.user.UserName] ~ Option[conduit.domain.user.EncryptedPassword] ~ Option[
        conduit.domain.user.Email
      ] ~ Option[conduit.domain.user.Bio] ~ Option[conduit.domain.user.Image] ~ conduit.domain.user.UserId] =
    sql"""
        UPDATE users u
        SET username = coalesce(${varchar.cimap[UserName].opt}, u.username),
            password = coalesce(${varchar.cimap[EncryptedPassword].opt}, u.password),
            email    = coalesce(${varchar.cimap[Email].opt}, u.email),
            bio      = coalesce(${varchar.cimap[Bio].opt}, u.bio),
            image    = coalesce(${varchar.cimap[Image].opt}, u.image)
        WHERE uuid = ${uuid.cimap[UserId]}
       """.command
}
