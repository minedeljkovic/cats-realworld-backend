package conduit.domain

import io.estatico.newtype.macros.newtype

object tag {

  @newtype case class ArticleTag(value: String)

  case class TagsResponse(tags: List[ArticleTag])

}
