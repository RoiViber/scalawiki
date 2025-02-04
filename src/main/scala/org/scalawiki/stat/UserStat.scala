package org.scalawiki.stat

class UserStat(revisionStats: Seq[RevisionStat]) {
  val users = revisionStats.foldLeft(Set.empty[String]) {
    (users, stats) => users ++ stats.users
  }

  val byUser = users.map { user =>
    (user, revisionStats.filter(_.users.contains(user)).sortBy(-_.byUserSize(user)))
  }.toMap

  val byUserAddedOrRemoved = byUser.toSeq.map {
    case (user, statSeq) => (user, statSeq.map(_.byUserSize(user)).sum)
  }.sortBy{
    case (user, size) => -size
  }

  override def toString = {
    val header = "{| class='wikitable sortable'\n" +
      "|+ users\n" + "! user !! added or rewritten !! articles number !! article list\n"

    header + byUserAddedOrRemoved.map{
      case (user, size) =>
        s"| [[User:$user|$user]] || $size || ${byUser(user).size} || ${byUser(user).map(rs => s"[[${rs.page.title}]] ${rs.byUserSize(user)}").mkString("<br>")}"
    }
      .mkString("\n|-\n", "\n|-\n", "")  + "\n|}"
  }


}
