package conduit

import conduit.domain.article._
import conduit.domain.profile._
import conduit.domain.user._
import conduit.domain.tag._
import io.estatico.newtype.ops._
import io.estatico.newtype.Coercible
import java.util.UUID
import java.time._
import org.scalacheck.Gen
import squants.market._
import conduit.domain.user.UserId
import conduit.domain.user.UserName

object generators {

  def cbUuid[A: Coercible[UUID, *]]: Gen[A] =
    Gen.uuid.map(_.coerce[A])

  def cbStr[A: Coercible[String, *]]: Gen[A] =
    genNonEmptyString.map(_.coerce[A])

  def cbInt[A: Coercible[Int, *]]: Gen[A] =
    Gen.posNum[Int].map(_.coerce[A])

  def cbLong[A: Coercible[Long, *]]: Gen[A] =
    Gen.posNum[Long].map(_.coerce[A])

  def cbBoolean[A: Coercible[Boolean, *]]: Gen[A] =
    Gen.oneOf(true, false).map(_.coerce[A])

  val genMoney: Gen[Money] =
    Gen.posNum[Long].map(n => USD(BigDecimal(n)))

  val genNonEmptyString: Gen[String] =
    Gen
      .chooseNum(21, 40)
      .flatMap { n =>
        Gen.buildableOfN[String, Char](n, Gen.alphaChar)
      }

  val genLocalDateTime: Gen[LocalDateTime] =
    for {
      y <- Gen.choose(Year.MIN_VALUE, Year.MAX_VALUE)
      m <- Gen.choose(1, 12)
      d <- Gen.choose(1, 28)
      h <- Gen.choose(0, 23)
      i <- Gen.choose(0, 59)
      s <- Gen.choose(0, 59)
      u <- Gen.choose(0, 999999999)
    } yield LocalDateTime.of(y, m, d, h, i, s, u)

  val genZoneOffset: Gen[ZoneOffset] =
    for {
      h <- Gen.choose(0, 17)
      i <- Gen.choose(0, 59)
      s <- Gen.choose(0, 59)
    } yield ZoneOffset.ofHoursMinutesSeconds(h, i, s)

  val genOffsetDateTime: Gen[OffsetDateTime] =
    for {
      d <- genLocalDateTime
      z <- genZoneOffset
    } yield OffsetDateTime.of(d, z)

  def cbOffsetDateTime[A: Coercible[OffsetDateTime, *]]: Gen[A] =
    genOffsetDateTime.map(_.coerce[A])

  val authorGen: Gen[Author] =
    for {
      id <- cbUuid[UserId]
      un <- cbStr[UserName]
      bi <- Gen.option(cbStr[Bio])
      im <- Gen.option(cbStr[Image])
      fo <- cbBoolean[FollowingStatus]
    } yield Author(id, un, bi, im, fo)

  val articleGen: Gen[Article] =
    for {
      id <- cbUuid[ArticleId]
      sl <- cbStr[Slug]
      ti <- cbStr[Title]
      de <- cbStr[Description]
      bo <- cbStr[Body]
      tl <- Gen.listOf(cbStr[ArticleTag])
      cr <- cbOffsetDateTime[CreateDateTime]
      up <- cbOffsetDateTime[UpdateDateTime]
      fd <- cbBoolean[FavoritedStatus]
      fc <- cbLong[FavoritesCount]
      au <- authorGen
    } yield Article(id, sl, ti, de, bo, tl, cr, up, fd, fc, au)

}
