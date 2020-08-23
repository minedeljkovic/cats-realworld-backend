package conduit.http.auth

import dev.profunktor.auth.jwt._
import io.estatico.newtype.macros.newtype

object users {

  @newtype case class UserJwtAuth(value: JwtSymmetricAuth)

}
