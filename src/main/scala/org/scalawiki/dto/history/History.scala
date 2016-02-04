package org.scalawiki.dto.history

import org.joda.time.DateTime
import org.scalawiki.dto.Revision
import org.scalawiki.dto.filter.RevisionFilter

class History(val revisions: Seq[Revision]) {

  def users(revisionFilter: RevisionFilter): Set[String] = {
    val filtered = revisionFilter.apply(revisions)
    filtered.flatMap(_.user.flatMap(_.name)).toSet
  }

  def delta(revisionFilter: RevisionFilter): Option[Long] = {
    val filtered = revisionFilter.apply(revisions)
    val sum = for (
      oldest <- filtered.lastOption;
      newest <- filtered.headOption;
      d1 <- delta(oldest);
      d2 <- delta(oldest, newest))
    yield d1 + d2
    sum
  }

  def delta(revision: Revision): Option[Long] =
    revision.parentId.flatMap { parentId =>
      if (parentId == 0)
        revision.size
      else
        revisions.filter(_.revId.isDefined).find(_.revId.get == parentId).flatMap {
          parent => delta(parent, revision)
        }
    }

  def delta(from: Revision, to: Revision): Option[Long] =
    for (fromSize <- from.size; toSize <- to.size) yield toSize - fromSize

  def created: Option[DateTime] = revisions.lastOption.filter(_.parentId.forall(_ == 0)).flatMap(_.timestamp)

  def updated: Option[DateTime] = revisions.headOption.flatMap(_.timestamp)

  def createdAfter(from: Option[DateTime]) = created.exists(rev => from.forall(rev.isAfter))

  def editedIn(revisionFilter: RevisionFilter) =
    revisionFilter.apply(revisions).nonEmpty

}
