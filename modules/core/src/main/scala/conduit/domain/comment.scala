package conduit.domain

import cats.kernel.Eq
import conduit.domain.profile._
import conduit.domain.user._
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import java.time.OffsetDateTime
import scala.util.control.NoStackTrace

object comment {

  @newtype case class CommentId(value: Int)
  @newtype case class CommentBody(value: String)

  @newtype case class CommentCreateDateTime(value: OffsetDateTime)
  implicit val eqCreateDateTime = Eq.fromUniversalEquals[CommentCreateDateTime]

  @newtype case class CommentUpdateDateTime(value: OffsetDateTime)
  implicit val eqUpdateDateTime = Eq.fromUniversalEquals[CommentUpdateDateTime]

  case class CommentAuthor(
      uuid: UserId,
      username: UserName,
      bio: Option[Bio],
      image: Option[Image],
      following: FollowingStatus
  )

  case class Comment(
      id: CommentId,
      body: CommentBody,
      createdAt: CommentCreateDateTime,
      updatedAt: CommentUpdateDateTime,
      author: CommentAuthor
  )
  case class CommentResponse(comment: Comment)
  case class CommentsResponse(comments: List[Comment])

  // --------- create comment -----------

  @newtype case class CommentBodyParam(value: NonEmptyString)

  case class CreateCommentParam(
      body: CommentBodyParam
  ) {
    def toBody: CommentBody =
      CommentBody(body.value.value)
  }
  case class CreateCommentRequest(comment: CreateCommentParam)

  // --------- delete comment -----------

  case class CurrentUserNotCommentAuthor(authorId: UserId) extends NoStackTrace

}
