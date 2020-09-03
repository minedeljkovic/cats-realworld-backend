package conduit.algebras

import cats.effect.Sync
import cats.implicits._
import conduit.config.data._
import dev.profunktor.auth.jwt._
import io.circe.syntax._
import pdi.jwt._
import scala.concurrent.duration.FiniteDuration

trait Tokens[F[_]] {
  def create: F[JwtToken]
}

object LiveTokens {
  def make[F[_]: Sync](
      tokenConfig: JwtSecretKeyConfig,
      tokenExpiration: TokenExpiration
  ): F[Tokens[F]] =
    Sync[F].delay(java.time.Clock.systemUTC).map { implicit jClock =>
      new LiveTokens[F](tokenConfig, tokenExpiration.value)
    }
}

final class LiveTokens[F[_]: Sync] private (
    config: JwtSecretKeyConfig,
    exp: FiniteDuration
)(implicit val ev: java.time.Clock)
    extends Tokens[F] {
  def create: F[JwtToken] =
    for {
      uuid <- GenUUID[F].make
      claim <- Sync[F].delay(JwtClaim(uuid.asJson.noSpaces).issuedNow.expiresIn(exp.toMillis))
      secretKey = JwtSecretKey(config.value.value.value)
      token <- jwtEncode[F](claim, secretKey, JwtAlgorithm.HS256)
    } yield token
}
