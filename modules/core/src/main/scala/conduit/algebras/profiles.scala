package conduit.algebras

import cats.effect._
import cats.implicits._
import conduit.domain.profile._
import conduit.domain.user._
import conduit.effects._
import conduit.ext.skunkx._
import skunk._
import skunk.codec.all._
import skunk.implicits._

trait Profiles[F[_]] {
  def find(followerId: UserId)(username: UserName): F[Option[Profile]]
  def follow(followerId: UserId)(username: UserName): F[Profile]
  def unfollow(followerId: UserId)(username: UserName): F[Profile]
}

object LiveProfiles {
  def make[F[_]: Sync](
      sessionPool: Resource[F, Session[F]]
  ): F[Profiles[F]] =
    Sync[F].delay(
      new LiveProfiles[F](sessionPool)
    )
}

final class LiveProfiles[F[_]: BracketThrow] private (
    sessionPool: Resource[F, Session[F]]
) extends Profiles[F] {
  import ProfileQueries._

  def find(followerId: UserId)(username: UserName): F[Option[Profile]] =
    sessionPool.use { session =>
      session.prepare(selectProfile).use { q =>
        q.option(followerId ~ username)
          .map(_.map {
            case _ ~ profile => profile
          })
      }
    }

  def follow(followerId: UserId)(username: UserName): F[Profile] =
    sessionPool.use { session =>
      (session.prepare(selectProfile), session.prepare(insertFollower)).tupled.use {
        case (selectQuery, insertCmd) =>
          for {
            followedId ~ profile <- selectQuery.unique(followerId ~ username)
            _ <- if (!profile.following.value)
                  insertCmd.execute(Follower(followedId = followedId, followerId = followerId))
                else ().pure[F]
          } yield profile.copy(following = FollowingStatus(true))
      }
    }

  def unfollow(followerId: UserId)(username: UserName): F[Profile] =
    sessionPool.use { session =>
      (session.prepare(selectProfile), session.prepare(deleteFollower)).tupled.use {
        case (selectQuery, deleteCmd) =>
          for {
            followedId ~ profile <- selectQuery.unique(followerId ~ username)
            _ <- if (profile.following.value)
                  deleteCmd.execute(Follower(followedId = followedId, followerId = followerId))
                else ().pure[F]
          } yield profile.copy(following = FollowingStatus(false))
      }
    }

}

private object ProfileQueries {

  val profileDecoder: Decoder[UserId ~ Profile] =
    (uuid.cimap[UserId] ~ varchar.cimap[UserName] ~ varchar.cimap[Bio].opt ~ varchar.cimap[Image].opt ~
        bool.cimap[FollowingStatus]).map {
      case id ~ un ~ bi ~ im ~ f =>
        id ~ Profile(un, bi, im, f)
    }

  val selectProfile: Query[UserId ~ UserName, UserId ~ Profile] =
    sql"""
        SELECT
          u.uuid,
          u.username,
          u.bio,
          u.image,
          CASE
            WHEN f.followed_id IS NULL THEN false
            ELSE true
          END as following
        FROM users u
        LEFT OUTER JOIN followers f
          ON f.followed_id = u.uuid
            AND f.follower_id = ${uuid.cimap[UserId]}
        WHERE u.username = ${varchar.cimap[UserName]}
       """.query(profileDecoder)

  val insertFollower: Command[Follower] =
    sql"""
        INSERT INTO followers (follower_id, followed_id)
        VALUES (${uuid.cimap[UserId]}, ${uuid.cimap[UserId]})
       """.command.contramap { f =>
      f.followerId ~ f.followedId
    }

  val deleteFollower: Command[Follower] =
    sql"""
        DELETE FROM followers
        WHERE follower_id = ${uuid.cimap[UserId]}
          AND followed_id = ${uuid.cimap[UserId]}
       """.command.contramap { f =>
      f.followerId ~ f.followedId
    }

}
