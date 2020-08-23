package conduit

import io.estatico.newtype.ops._
import io.estatico.newtype.Coercible
import java.util.UUID
import org.scalacheck.Gen
import squants.market._

object generators {

  def cbUuid[A: Coercible[UUID, *]]: Gen[A] =
    Gen.uuid.map(_.coerce[A])

  def cbStr[A: Coercible[String, *]]: Gen[A] =
    genNonEmptyString.map(_.coerce[A])

  def cbInt[A: Coercible[Int, *]]: Gen[A] =
    Gen.posNum[Int].map(_.coerce[A])

  val genMoney: Gen[Money] =
    Gen.posNum[Long].map(n => USD(BigDecimal(n)))

  val genNonEmptyString: Gen[String] =
    Gen
      .chooseNum(21, 40)
      .flatMap { n =>
        Gen.buildableOfN[String, Char](n, Gen.alphaChar)
      }

}
