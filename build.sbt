import Dependencies._
lazy val scala212 = "2.12.16"
lazy val scala213 = "2.13.8"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalaVersion := scala213
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "com.github.jumale"
ThisBuild / homepage := Some(url("https://github.com/jumale/sdebug"))
ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

lazy val root = (project in file("."))
  .aggregate(core, playJsonSupport, shortcut)
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

lazy val shortcut = (project in file("./shortcut"))
  .settings( //
    name := "sdebug-shortcut",
    crossScalaVersions := supportedScalaVersions
  )
  .dependsOn(core, playJsonSupport)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
