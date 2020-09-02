package conduit

import conduit.domain.article._
import io.estatico.newtype.Coercible
import java.util.UUID
import org.scalacheck.Arbitrary
import conduit.generators._
import squants.market.Money

object arbitraries {

  implicit def arbCoercibleInt[A: Coercible[Int, *]]: Arbitrary[A] =
    Arbitrary(cbInt[A])

  implicit def arbCoercibleLong[A: Coercible[Long, *]]: Arbitrary[A] =
    Arbitrary(cbLong[A])

  implicit def arbCoercibleStr[A: Coercible[String, *]]: Arbitrary[A] =
    Arbitrary(cbStr[A])

  implicit def arbCoercibleUUID[A: Coercible[UUID, *]]: Arbitrary[A] =
    Arbitrary(cbUuid[A])

  implicit def arbCoercibleBoolean[A: Coercible[Boolean, *]]: Arbitrary[A] =
    Arbitrary(cbBoolean[A])

  implicit val arbMoney: Arbitrary[Money] =
    Arbitrary(genMoney)

  implicit val arbAuthor: Arbitrary[Author] =
    Arbitrary(authorGen)

  implicit val arbArticle: Arbitrary[Article] =
    Arbitrary(articleGen)

}
