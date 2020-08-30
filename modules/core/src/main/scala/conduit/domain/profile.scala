package conduit.domain

import cats.kernel.Eq
import io.estatico.newtype.macros.newtype
import conduit.domain.user._

object profile {

  @newtype case class FollowingStatus(value: Boolean)
  implicit val eqFollowingStatus = Eq.fromUniversalEquals[FollowingStatus]

  case class Profile(
      username: UserName,
      bio: Option[Bio],
      image: Option[Image],
      following: FollowingStatus
  )
  case class ProfileResponse(profile: Profile)

  case class Follower(
      followerId: UserId,
      followedId: UserId
  )

}
