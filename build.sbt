lazy val scala212 = "2.12.19"
lazy val scala213 = "2.13.16"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalaVersion := scala213
ThisBuild / version := "0.4.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "com.github.jumale"
ThisBuild / homepage := Some(url("https://github.com/jumale/sdebug"))
ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
lazy val scalactic = "org.scalactic" %% "scalactic" % "3.2.19"
lazy val playJson = "com.typesafe.play" %% "play-json" % "2.10.5"

lazy val root = (project in file("."))
  .aggregate(core, playJsonSupport, scalacticSupport, impl, implExt)
  .settings(name := "sdebug")

lazy val core = (project in file("./core"))
  .settings( //
    name := "sdebug-core",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(scalaTest % Test)
  )

lazy val playJsonSupport = (project in file("./play-json"))
  .settings( //
    name := "sdebug-play-json",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(scalaTest % Test, playJson)
  )
  .dependsOn(core)

lazy val scalacticSupport = (project in file("./scalactic"))
  .settings( //
    name := "sdebug-scalactic",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(scalaTest % Test, scalactic)
  )
  .dependsOn(core)

lazy val impl = (project in file("./impl"))
  .settings( //
    name := "sdebug-impl",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(scalaTest % Test)
  )
  .dependsOn(core)

lazy val implExt = (project in file("./impl-ext"))
  .settings( //
    name := "sdebug-impl-ext",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(scalaTest % Test)
  )
  .dependsOn(core, playJsonSupport, scalacticSupport)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
