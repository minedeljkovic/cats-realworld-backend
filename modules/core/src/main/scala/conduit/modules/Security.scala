package conduit.modules

import cats.effect._
import cats.implicits._
import conduit.algebras._
import conduit.config.data._
import conduit.http.auth.users._
import dev.profunktor.auth.jwt._
import dev.profunktor.redis4cats.RedisCommands
import pdi.jwt._
import skunk.Session

object Security {
  def make[F[_]: Sync](
      cfg: AppConfig,
      sessionPool: Resource[F, Session[F]],
      redis: RedisCommands[F, String, String]
  ): F[Security[F]] = {

    val userJwtAuth: UserJwtAuth =
      UserJwtAuth(
        JwtAuth
          .hmac(
            cfg.tokenConfig.value.value.value,
            JwtAlgorithm.HS256
          )
      )

    for {
      tokens <- LiveTokens.make[F](cfg.tokenConfig, cfg.tokenExpiration)
      crypto <- LiveCrypto.make[F](cfg.passwordSalt)
      users <- LiveUsers.make[F](sessionPool, crypto)
      auth <- LiveAuth.make[F](cfg.tokenExpiration, tokens, users, redis)
    } yield new Security[F](auth, userJwtAuth)

  }
}

final class Security[F[_]] private (
    val auth: Auth[F],
    val userJwtAuth: UserJwtAuth
)
