package org.scalawiki

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.io.IO
import akka.pattern.ask
//import slick.driver.H2Driver.api._

import org.scalawiki.http.{HttpClient, HttpClientImpl}
import org.scalawiki.json.MwReads._
import org.scalawiki.query.{SinglePageQuery, PageQuery}
import org.scalawiki.sql.MwDatabase
import play.api.libs.json._
import spray.can.Http
import spray.http._
import spray.util._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait MwBot {

  def host: String

  def mwDb: Option[MwDatabase]

  def login(user: String, password: String): Future[String]

  def get(params: Map[String, String]): Future[String]

  def getByteArray(url: String): Future[Array[Byte]]

  def post[T](reads: Reads[T], params: (String, String)*): Future[T]

  def post[T](reads: Reads[T], params: Map[String, String]): Future[T]

  def postMultiPart[T](reads: Reads[T], params: Map[String, String]): Future[T]

  def postFile[T](reads: Reads[T], params: Map[String, String], fileParam: String, filename: String): Future[T]

  def page(title: String): SinglePageQuery

  def page(id: Long): SinglePageQuery

  def pageText(title: String): Future[String]

  def token: String

  def await[T](future: Future[T]): T

  def dbCache: Boolean

  def system: ActorSystem

  def log: LoggingAdapter
}

class MwBotImpl(val http: HttpClient, val system: ActorSystem, val host: String, val database: Option[MwDatabase]) extends MwBot {

  implicit val sys = system

  import system.dispatcher

  val baseUrl: String = "https://" + host + "/w/"

  val indexUrl = baseUrl + "index.php"

  val apiUrl = baseUrl + "api.php"

  def encodeTitle(title: String): String = MwUtils.normalize(title)

  override def log = system.log

  override def mwDb = database

  override def dbCache = database.isDefined

  override def login(user: String, password: String): Future[String] = {
    require(user != null, "User is null")
    require(password != null, "Password is null")

    log.info(s"$host login user: $user")

    http.post(apiUrl, "action" -> "login", "lgname" -> user, "lgpassword" -> password, "format" -> "json") map http.cookiesAndBody map { cb =>
      http.setCookies(cb.cookies)
      val json = Json.parse(cb.body)
      json.validate(loginResponseReads).fold({ err =>
        log.error("Could not login" + err)
        err.toString()
      }, { resp =>
        val params = Map("action" -> "login", "lgname" -> user, "lgpassword" -> password, "lgtoken" -> resp.token.get, "format" -> "json")
        Await.result(http.post(apiUrl, params) map http.cookiesAndBody map { cb =>
          http.setCookies(cb.cookies)
          val json = Json.parse(cb.body)
          val l = json.validate(loginResponseReads) // {"login":{"result":"NotExists"}}
          l.fold(err => err.toString(),
            success => {
              log.info(s"$host login user: $user, result: ${success.result}")
              success.result
            }
          )
        }, http.timeout)
      })
    }
  }

  override lazy val token = await(getToken)

  def getToken = get(tokenReads, "action" -> "query", "meta" -> "tokens")

  def getTokens = get(tokensReads, "action" -> "tokens")


  def get[T](reads: Reads[T], params: (String, String)*): Future[T] =
    http.get(getUri(params:_*)) map {
      body =>
        Json.parse(body).validate(reads).get
    }

  override def getByteArray(url: String): Future[Array[Byte]] =
    http.getResponse(url) map {
      response => response.entity.data.toByteArray
    }


  override def post[T](reads: Reads[T], params: (String, String)*): Future[T] =
    post(reads, params.toMap)

  override def post[T](reads: Reads[T], params: Map[String, String]): Future[T] =
    http.post(apiUrl, params) map http.getBody map {
      body =>
        val result = Json.parse(body).validate(reads).get
        println(result)
        result
    }

  override def postMultiPart[T](reads: Reads[T], params: Map[String, String]): Future[T] =
    http.postMultiPart(apiUrl, params) map http.getBody map {
      body =>
        val json = Json.parse(body)
        val response = json.validate(reads)
//        response.fold[T](err => {
//          json.validate(errorReads)
//        },
//          success => success
//        )
        val result = response.get
        println(result)
        result
    }

  override def postFile[T](reads: Reads[T], params: Map[String, String], fileParam: String, filename: String): Future[T] =
    http.postFile(apiUrl, params, fileParam , filename) map http.getBody map {
      body =>
        val json = Json.parse(body)
        val response = json.validate(reads)
        //        response.fold[T](err => {
        //          json.validate(errorReads)
        //        },
        //          success => success
        //        )
        val result = response.get
        println(result)
        result
  }

  def pagesByTitle(titles: Set[String]) = PageQuery.byTitles(titles, this)

  def pagesById(ids: Set[Long]) = PageQuery.byIds(ids, this)

  override def page(title: String) = PageQuery.byTitle(title, this)

  override def page(id: Long) = PageQuery.byId(id, this)

  override def pageText(title: String): Future[String] = {
    val url = getIndexUri("title" -> encodeTitle(title), "action" -> "raw")
    http.get(url)
  }

  def getIndexUri(params: (String, String)*) =
    Uri(indexUrl) withQuery (params ++ Seq("format" -> "json"): _*)

  def getUri(params: (String, String)*) =
    Uri(apiUrl) withQuery (params ++ Seq("format" -> "json"): _*)

  override def get(params: Map[String, String]): Future[String] = {
    val uri: Uri = getUri(params)

    log.info(s"$host GET url: $uri")
    http.get(uri)
  }

  def getUri(params: Map[String, String]) =
    Uri(apiUrl) withQuery (params ++ Map("format" -> "json"))

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }

  override def await[T](future: Future[T]) = Await.result(future, http.timeout)

}

object MwBot {

  import spray.caching.{Cache, LruCache}

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent._

  val commons = "commons.wikimedia.org"
  val ukWiki = "uk.wikipedia.org"

  def create(host: String, withDb: Boolean = false): MwBot = {
    val system = ActorSystem()
    val http = new HttpClientImpl(system)

    val bot = /*if (withDb) {
      val db = Database.forURL("jdbc:h2:~/scalawiki", driver = "org.h2.Driver")
      new MwBotImpl(http, system, host, Some(new MwDatabase(db, Some(MwDatabase.dbName(host)))))
    } else {*/
      new MwBotImpl(http, system, host, None)
    //}

    if (withDb) {
      bot.database.foreach(_.createTables())
    }

    bot.await(bot.login(LoginInfo.login, LoginInfo.password))
    bot
  }

  val cache: Cache[MwBot] = LruCache()

  def get(host: String): MwBot = {
    Await.result(cache(host) {
      Future { create(host) }
    }, 1.minute)
  }
}


