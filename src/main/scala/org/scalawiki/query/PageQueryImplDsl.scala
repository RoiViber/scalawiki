package org.scalawiki.query

import java.nio.file.{Files, Paths}

import org.scalawiki.MwBot
import org.scalawiki.dto.Page
import org.scalawiki.dto.cmd._
import org.scalawiki.dto.cmd.edit._
import org.scalawiki.dto.cmd.query.list._
import org.scalawiki.dto.cmd.query.prop._
import org.scalawiki.dto.cmd.query.prop.rvprop.RvProp
import org.scalawiki.dto.cmd.query._
import org.scalawiki.json.MwReads._

import scala.concurrent.Future

class PageQueryImplDsl(query: Either[Set[Long], Set[String]], bot: MwBot, dbCache: Boolean = false) extends PageQuery with SinglePageQuery {

  override def revisions(namespaces: Set[Int], props: Set[String], continueParam: Option[(String, String)]): Future[Seq[Page]] = {

    import org.scalawiki.dto.cmd.query.prop.rvprop._

    val pages = query.fold(
      ids => PageIdsParam(ids.toSeq),
      titles => TitlesParam(titles.toSeq)
    )

    val action = Action(Query(
      pages,
      Prop(
        Info(),
        Revisions(
          RvProp(RvPropArgs.byNames(props.toSeq): _*),
          RvLimit("max")
        )
      )
    ))

    new DslQueryDbCache(new DslQuery(action, bot)).run()
  }

  override def revisionsByGenerator(
                                     generator: String,
                                     generatorPrefix: String,
                                     namespaces: Set[Int],
                                     props: Set[String],
                                     continueParam: Option[(String, String)],
                                     limit: String,
                                     titlePrefix: Option[String]): Future[Seq[Page]] = {

    val pageId: Option[Long] = query.left.toOption.map(_.head)
    val title: Option[String] = query.right.toOption.map(_.head)

    val action = Action(Query(
      Prop(
        Info(),
        Revisions(RvProp(RvPropArgs.byNames(props.toSeq): _*))
      ),
      Generator(ListArgs.toDsl(generator, title, pageId, namespaces, Some(limit)))
    ))

    new DslQueryDbCache(new DslQuery(action, bot)).run()
  }

  override def imageInfoByGenerator(
                                     generator: String,
                                     generatorPrefix: String,
                                     namespaces: Set[Int],
                                     props: Set[String],
                                     continueParam: Option[(String, String)],
                                     limit: String,
                                     titlePrefix: Option[String]): Future[Seq[Page]] = {
    import org.scalawiki.dto.cmd.query.prop.iiprop._

    val pageId: Option[Long] = query.left.toOption.map(_.head)
    val title: Option[String] = query.right.toOption.map(_.head)

    val action = Action(Query(
      Prop(
        ImageInfo(
          IiProp(IiPropArgs.byNames(props.toSeq): _*)
        )
      ),
      Generator(ListArgs.toDsl(generator, title, pageId, namespaces, Some(limit)))
    ))

    new DslQuery(action, bot).run()
  }

  override def edit(text: String, summary: Option[String] = None, section: Option[String] = None, token: Option[String] = None, multi: Boolean = true) = {

    val page = query.fold(
      ids => PageId(ids.head),
      titles => Title(titles.head)
    )

    val action = Action(Edit(
      page,
      Text(text),
      Token(token.fold(bot.token)(identity))
    )
    )

    val params = action.pairs.toMap ++
      Map("action" -> "edit",
        "format" -> "json",
        "bot" -> "x",
        "assert" -> "user",
        "assert" -> "bot") ++ section.map(s => "section" -> s).toSeq

    bot.log.info(s"${bot.host} edit page: $page, summary: $summary")

    if (multi)
      bot.postMultiPart(editResponseReads, params)
    else
      bot.post(editResponseReads, params)
  }

  override def upload(filename: String) = {
    val page = query.right.toOption.fold(filename)(_.head)
    val token = bot.token
    val fileContents = Files.readAllBytes(Paths.get(filename))
    val params = Map(
      "action" -> "upload",
      "filename" -> page,
      "token" -> token,
      "format" -> "json",
      "comment" -> "update",
      "filesize" -> fileContents.size.toString,
      "ignorewarnings" -> "true",
      "assert" -> "user",
      "assert" -> "bot")

    bot.postFile(uploadResponseReads, params, "file", filename)
  }

  override def whatTranscludesHere(namespaces: Set[Int], continueParam: Option[(String, String)]): Future[Seq[Page]] = {
    val pages = query.fold(
      ids => EiPageId(ids.head),
      titles => EiTitle(titles.head)
    )

    val action = Action(Query(
      ListParam(
        EmbeddedIn(
          pages,
          EiLimit("max"),
          EiNamespace(namespaces.toSeq)
        )
      )
    ))

    new DslQuery(action, bot).run()
  }

  override def categoryMembers(namespaces: Set[Int], continueParam: Option[(String, String)]): Future[Seq[Page]] = {
    val pages = query.fold(
      ids => CmPageId(ids.head),
      titles => CmTitle(titles.head)
    )

    val action = Action(Query(
      ListParam(
        CategoryMembers(
          pages,
          CmLimit("max"),
          CmNamespace(namespaces.toSeq)
        )
      )
    ))

    new DslQuery(action, bot).run()
  }
}
