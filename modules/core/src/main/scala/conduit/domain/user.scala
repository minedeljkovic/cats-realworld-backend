package conduit.domain

import dev.profunktor.auth.jwt.JwtToken
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import java.util.UUID
import javax.crypto.Cipher
import scala.util.control.NoStackTrace

object user {

  @newtype case class UserId(value: UUID)
  @newtype case class UserName(value: String)
  @newtype case class Password(value: String)
  @newtype case class Email(value: String)
  @newtype case class Bio(value: String)
  @newtype case class Image(value: String)

  @newtype case class EncryptedPassword(value: String)

  @newtype case class EncryptCipher(value: Cipher)
  @newtype case class DecryptCipher(value: Cipher)

  case class NewUser(
      id: UserId,
      email: Email,
      username: UserName
  )

  case class UnauthenticatedUser(
      id: UserId,
      email: Email,
      username: UserName,
      bio: Option[Bio],
      image: Option[Image]
  )

  case class User(
      id: UserId,
      email: Email,
      token: JwtToken,
      username: UserName,
      bio: Option[Bio],
      image: Option[Image]
  )

  // --------- user registration -----------

  @newtype case class UserNameParam(value: NonEmptyString) {
    def toDomain: UserName = UserName(value.value.toLowerCase)
  }

  @newtype case class EmailParam(value: NonEmptyString) {
    def toDomain: Email = Email(value.value)
  }

  @newtype case class PasswordParam(value: NonEmptyString) {
    def toDomain: Password = Password(value.value)
  }

  case class RegisterUser(
      username: UserNameParam,
      email: EmailParam,
      password: PasswordParam
  )
  case class RegisterUserRequest(user: RegisterUser)

  case class UserNameInUse(username: UserName) extends NoStackTrace
  case class EmailInUse(email: Email) extends NoStackTrace
  case class InvalidUserOrPassword(email: Email) extends NoStackTrace
  case object UnsupportedOperation extends NoStackTrace

  case object TokenNotFound extends NoStackTrace

  case class UserResponse(user: User)

  // --------- user login -----------

  case class LoginUser(
      email: EmailParam,
      password: PasswordParam
  )
  case class LoginUserRequest(user: LoginUser)

  // --------- user update -----------

  @newtype case class ImageParam(value: NonEmptyString) {
    def toDomain: Image = Image(value.value)
  }

  @newtype case class BioParam(value: NonEmptyString) {
    def toDomain: Bio = Bio(value.value)
  }

  case class UpdateUser(
      email: Option[EmailParam],
      username: Option[UserNameParam],
      password: Option[PasswordParam],
      image: Option[ImageParam],
      bio: Option[BioParam]
  )
  case class UpdateUserRequest(user: UpdateUser)
}
