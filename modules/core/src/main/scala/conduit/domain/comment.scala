package conduit.domain

import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import scala.util.control.NoStackTrace
import java.time.OffsetDateTime
import conduit.domain.user._
import conduit.domain.profile._
import cats.kernel.Eq

object comment {

  @newtype case class CommentId(value: Int)
  @newtype case class Body(value: String)

  @newtype case class CreateDateTime(value: OffsetDateTime)
  implicit val eqCreateDateTime = Eq.fromUniversalEquals[CreateDateTime]

  @newtype case class UpdateDateTime(value: OffsetDateTime)
  implicit val eqUpdateDateTime = Eq.fromUniversalEquals[UpdateDateTime]

  case class CommentAuthor(
      uuid: UserId,
      username: UserName,
      bio: Option[Bio],
      image: Option[Image],
      following: FollowingStatus
  )

  case class Comment(
      id: CommentId,
      body: Body,
      createdAt: CreateDateTime,
      updatedAt: UpdateDateTime,
      author: CommentAuthor
  )
  case class CommentResponse(comment: Comment)
  case class CommentsResponse(comments: List[Comment])

  // --------- create comment -----------

  @newtype case class BodyParam(value: NonEmptyString)

  case class CreateCommentParam(
      body: BodyParam
  ) {
    def toBody: Body =
      Body(body.value.value)
  }
  case class CreateCommentRequest(comment: CreateCommentParam)

  // --------- delete comment -----------

  case class CurrentUserNotCommentAuthor(authorId: UserId) extends NoStackTrace

}
