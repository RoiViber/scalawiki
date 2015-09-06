package org.scalawiki.wikitext

import de.fau.cs.osr.ptk.common.ast.AstNode
import org.sweble.wikitext.engine.config.WikiConfig
import org.sweble.wikitext.engine.nodes.EngProcessedPage
import org.sweble.wikitext.engine.{PageId, PageTitle, WtEngineImpl}
import org.sweble.wikitext.parser.nodes._
import org.sweble.wikitext.parser.utils.WtRtDataPrinter

trait SwebleParser {

  import scala.collection.JavaConverters._

  def config: WikiConfig

  def parsePage(title: String, text: String): EngProcessedPage = {
    val engine = new WtEngineImpl(config)
    engine.postprocess(getPageId(title), text, null)
  }

  def getPageId(title: String): PageId = {
    new PageId(PageTitle.make(config, title), -1)
  }

  def findNode[T](node: WtNode, pf: PartialFunction[WtNode, T]): Option[T] = {
    if (pf.isDefinedAt(node))
      Some(pf(node))
    else
      node.asScala.view.flatMap(child => findNode(child, pf)).headOption
  }

  def collectNodes[T](node: WtNode, pf: PartialFunction[WtNode, T]): Seq[T] = {
    if (pf.isDefinedAt(node))
      Seq(pf(node))
    else
      node.asScala.flatMap(child => collectNodes(child, pf))
  }

  def nodesToText[T <: AstNode[WtNode]](node: WtNode, pf: PartialFunction[WtNode, T]): Seq[String] =
    collectNodes(node, pf).map(c => getText(c.get(1)).trim)

  def getText(node: WtNode): String = {
    WtRtDataPrinter.print(node)
  }

  def replace[T <: WtNode](wiki: String, pf: PartialFunction[WtNode, T], mapper: (T => String)): String = {
    val page = parsePage("Some title", wiki).getPage

    replaceNodeWithText(page, pf, mapper)
  }

  def replaceNodeWithText[T <: WtNode](node: WtNode, pf: PartialFunction[WtNode, T], mapper: (T => String)): String = {
    if (pf.isDefinedAt(node))
      mapper(node.asInstanceOf[T])
    else if (node.isEmpty)
      getText(node)
    else
      node.asScala.map(child => replaceNodeWithText(child, pf, mapper)).mkString
  }
}
