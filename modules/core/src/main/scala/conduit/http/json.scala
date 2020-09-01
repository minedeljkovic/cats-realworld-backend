package conduit.http

import cats.Applicative
import dev.profunktor.auth.jwt.JwtToken
import io.circe._
import io.circe.generic.semiauto._
import io.circe.refined._
import io.estatico.newtype.Coercible
import io.estatico.newtype.ops._
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import conduit.domain.article._
import conduit.domain.user._
import conduit.domain.profile._
import conduit.domain.healthcheck._
import conduit.domain.tag._
import conduit.domain.comment._

object json extends JsonCodecs {
  implicit def deriveEntityEncoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]
}

private[http] trait JsonCodecs {

  // ----- Coercible codecs -----
  implicit def coercibleDecoder[A: Coercible[B, *], B: Decoder]: Decoder[A] =
    Decoder[B].map(_.coerce[A])

  implicit def coercibleEncoder[A: Coercible[B, *], B: Encoder]: Encoder[A] =
    Encoder[B].contramap(_.repr.asInstanceOf[B])

  implicit def coercibleKeyDecoder[A: Coercible[B, *], B: KeyDecoder]: KeyDecoder[A] =
    KeyDecoder[B].map(_.coerce[A])

  implicit def coercibleKeyEncoder[A: Coercible[B, *], B: KeyEncoder]: KeyEncoder[A] =
    KeyEncoder[B].contramap[A](_.repr.asInstanceOf[B])

  // ----- Domain codecs -----

  implicit val tokenDecoder: Decoder[JwtToken] = Decoder[String].map(JwtToken.apply)
  implicit val tokenEncoder: Encoder[JwtToken] = Encoder[String].contramap(_.value)

  implicit val userDecoder: Decoder[User] = deriveDecoder[User]
  implicit val userEncoder: Encoder[User] = deriveEncoder[User]

  implicit val userResponseEncoder: Encoder[UserResponse] = deriveEncoder[UserResponse]

  implicit val appStatusEncoder: Encoder[AppStatus] = deriveEncoder[AppStatus]

  implicit val registerUserDecoder: Decoder[RegisterUser]               = deriveDecoder[RegisterUser]
  implicit val registerUserRequestDecoder: Decoder[RegisterUserRequest] = deriveDecoder[RegisterUserRequest]

  implicit val loginUserDecoder: Decoder[LoginUser]               = deriveDecoder[LoginUser]
  implicit val loginUserRequestDecoder: Decoder[LoginUserRequest] = deriveDecoder[LoginUserRequest]

  implicit val updateUserDecoder: Decoder[UpdateUser]               = deriveDecoder[UpdateUser]
  implicit val updateUserRequestDecoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]

  implicit val profileEncoder: Encoder[Profile]                 = deriveEncoder[Profile]
  implicit val profileResponseEncoder: Encoder[ProfileResponse] = deriveEncoder[ProfileResponse]

  implicit val createArticleParamDecoder: Decoder[CreateArticleParam]     = deriveDecoder[CreateArticleParam]
  implicit val createArticleRequestDecoder: Decoder[CreateArticleRequest] = deriveDecoder[CreateArticleRequest]

  implicit val authorEncoder: Encoder[Author] = deriveEncoder[Author]

  implicit val articleEncoder: Encoder[Article] = deriveEncoder[Article]

  implicit val articleResponseEncoder: Encoder[ArticleResponse]   = deriveEncoder[ArticleResponse]
  implicit val articlesResponseEncoder: Encoder[ArticlesResponse] = deriveEncoder[ArticlesResponse]

  implicit val updateArticleParamDecoder: Decoder[UpdateArticleParam]     = deriveDecoder[UpdateArticleParam]
  implicit val updateArticleRequestDecoder: Decoder[UpdateArticleRequest] = deriveDecoder[UpdateArticleRequest]

  implicit val tagsResponseEncoder: Encoder[TagsResponse] = deriveEncoder[TagsResponse]

  implicit val createCommentParamDecoder: Decoder[CreateCommentParam]     = deriveDecoder[CreateCommentParam]
  implicit val createCommentRequestDecoder: Decoder[CreateCommentRequest] = deriveDecoder[CreateCommentRequest]

  implicit val commentAuthorEncoder: Encoder[CommentAuthor] = deriveEncoder[CommentAuthor]

  implicit val commentEncoder: Encoder[Comment] = deriveEncoder[Comment]

  implicit val commentResponseEncoder: Encoder[CommentResponse]   = deriveEncoder[CommentResponse]
  implicit val commentsResponseEncoder: Encoder[CommentsResponse] = deriveEncoder[CommentsResponse]

}
