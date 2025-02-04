package org.scalawiki.dto

import org.joda.time.DateTime

case class User(
                 id: Option[Long],
                 login: Option[String],
                 editCount: Option[Long] = None,
                 registration: Option[DateTime] = None,
                 blocked: Option[Boolean] = None)
  extends Contributor {

  override def name = login

}

object User {
  def apply(id: Long, login: String) =
    new User(Some(id), Option(login))
}


