import sbt._
import Keys._

object Dependencies {
  val all = {
    val plugins = Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
      compilerPlugin("io.tryp" % "splain" % "0.5.0" cross CrossVersion.patch),
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
    )

    val cats = Seq(
      "org.typelevel" %% "cats-core" % "2.0.0",
      "org.typelevel" %% "cats-effect" % "2.0.0",
      "org.typelevel" %% "cats-mtl-core" % "0.7.0" % "test",
      "com.olegpy" %% "meow-mtl" % "0.3.0-M1" % "test"
    )

    val http4s = Seq(
      "org.http4s" %% "http4s-blaze-server",
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-circe"
    ).map(_ % "0.21.0-M5")

    val pureConfig = Seq(
      "com.github.pureconfig" %% "pureconfig"
    ).map(_ % "0.12.1")

    val logger = Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
      "org.log4s" %% "log4s" % "1.8.2"
    )

    val swagger = Seq(
      "org.webjars" % "swagger-ui" % "3.24.0"
    )

    val tapir = Seq(
      "com.softwaremill.tapir" %% "tapir-http4s-server",
      "com.softwaremill.tapir" %% "tapir-openapi-docs",
      "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml",
      "com.softwaremill.tapir" %% "tapir-json-circe"
    ).map(_ % "0.11.9")

    val circe = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras"
    ).map(_ % "0.12.2")

    val database = Seq(
      "org.postgresql" % "postgresql" % "42.2.8",
      "org.liquibase" % "liquibase-core" % "3.8.1"
    )

    val xml = Seq(
      "javax.xml.bind" % "jaxb-api" % "2.3.1"
    )

    val fuuid = Seq(
      "io.chrisdavenport" %% "fuuid",
      "io.chrisdavenport" %% "fuuid-circe"
    ).map(_ % "0.3.0-M3")

    val tsec = Seq(
      "io.github.jmcardon" %% "tsec-common",
      "io.github.jmcardon" %% "tsec-password"
    ).map(_ % "0.2.0-M2")

    val doobie = Seq(
      "org.tpolecat" %% "doobie-core",
      "org.tpolecat" %% "doobie-hikari",
      "org.tpolecat" %% "doobie-quill"
    ).map(_ % "0.8.6")

    val enumeratum = Seq(
      "com.beachape" %% "enumeratum" % "1.5.13",
      "com.beachape" %% "enumeratum-circe" % "1.5.22"
    )

    val yamusca = Seq(
      "com.github.eikek" %% "yamusca-core" % "0.6.1"
    )

    val console = Seq(
      "dev.profunktor" %% "console4cats" % "0.8.0"
    )

    val cron = Seq(
      "eu.timepit" %% "fs2-cron-core" % "0.2.2"
    )

    val chimney = Seq(
      "io.scalaland" %% "chimney" % "0.3.4"
    )

    val scalaCache = Seq(
      "com.github.cb372" %% "scalacache-core",
      "com.github.cb372" %% "scalacache-cats-effect",
      "com.github.cb372" %% "scalacache-caffeine"
    ).map(_ % "0.28.0")

    val semver = Seq(
      "com.vdurmont" % "semver4j" % "3.1.0"
    )

    val test = Seq(
      "org.scalatest" %% "scalatest" % "3.0.8" % "it, test"
    )

    Seq(
      libraryDependencies ++= plugins ++ cats ++ http4s ++ pureConfig ++ logger ++ swagger ++ tapir ++ circe ++ database ++ xml ++ fuuid ++ tsec ++ doobie ++ enumeratum ++ yamusca ++ console ++ cron ++ test ++ chimney ++ scalaCache ++ semver
    )
  }
}
