package org.scalawiki.wlx.dto

import scala.collection.immutable.SortedSet

class Country(
               val code: String,
               val name: String,
               val languageCode: String,
               val regions: Seq[Region] = Seq.empty) {

  val regionIds = SortedSet(regions.map(_.code):_*)
  val regionNames = regions.sortBy(_.code).map(_.name)
  val regionById = regions.groupBy(_.code).mapValues(_.head)

}

object Country {
  val Ukraine = new Country("ua", "Ukraine", "uk",
    Map(
      "80" -> "Київ",
      "07" -> "Волинська область",
      "68" -> "Хмельницька область",
      "05" -> "Вінницька область",
      "35" -> "Кіровоградська область",
      "65" -> "Херсонська область",
      "63" -> "Харківська область",
      "01" -> "Автономна Республіка Крим",
      "32" -> "Київська область",
      "61" -> "Тернопільська область",
      "18" -> "Житомирська область",
      "48" -> "Миколаївська область",
      "46" -> "Львівська область",
      "14" -> "Донецька область",
      "44" -> "Луганська область",
      "74" -> "Чернігівська область",
      "12" -> "Дніпропетровська область",
      "73" -> "Чернівецька область",
      "71" -> "Черкаська область",
      "59" -> "Сумська область",
      "26" -> "Івано-Франківська область",
      "56" -> "Рівненська область",
      "85" -> "Севастополь",
      "23" -> "Запорізька область",
      "53" -> "Полтавська область",
      "21" -> "Закарпатська область",
      "51" -> "Одеська область"
    ).map {case (code, name) => Region(code, name)}.toSeq
  )

  val Armenia = new Country("am", "Armenia & Nagorno-Karabakh", "hy")

  val Austria = new Country("au", "Austria", "de")

  val Catalonia = new Country("ca", "Andorra & Catalan areas", "ca")

  val Azerbaijan = new Country("az", "Azerbaijan", "az")

  val Estonia = new Country("ee", "Estonia", "et")

  val Nepal = new Country("np", "Nepal", "en")

  val Russia = new Country("ru", "Russia", "ru.wikivoyage.org")

  val Switzerland = new Country("ch", "Switzerland", "commons.wikimedia.org")

}
