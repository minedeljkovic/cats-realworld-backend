package conduit.ext

import io.estatico.newtype.macros.newtype
import io.estatico.newtype.Coercible
import io.estatico.newtype.ops._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import skunk._
import skunk.codec.all._
import skunk.implicits._

object skunkx {

  implicit class CodecOps[B](codec: Codec[B]) {
    def cimap[A: Coercible[B, *]](implicit ev: Coercible[A, B]): Codec[A] =
      codec.imap(_.coerce[A])((ev(_)))
  }

  @newtype case class Limit(value: Int)
  @newtype case class Offset(value: Int)

  @newtype case class LimitParam(value: Int Refined Positive) {
    def toDomain: Limit = Limit(value.value)
  }
  @newtype case class OffsetParam(value: Int Refined Positive) {
    def toDomain: Offset = Offset(value.value)
  }

  def limitOffset[A](order: Fragment[A]): Fragment[A ~ Option[Limit] ~ Option[Offset]] =
    sql"""$order LIMIT ${int4.cimap[Limit].opt} OFFSET ${int4.cimap[Offset]}""".contramap {
      case lim ~ off => lim ~ off.getOrElse(Offset(0))
    }
}
