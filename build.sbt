import Dependencies._

ThisBuild / scalaVersion := "2.13.7"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "com.github.jumale"
ThisBuild / homepage := Some(url("https://github.com/jumale/sdebug"))
ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

lazy val root = (project in file("."))
  .settings(name := "sdebug")
  .aggregate(core, playJsonSupport)

lazy val core = (project in file("./core"))
  .settings( //
    name := "sdebug-core",
    libraryDependencies ++= Seq(scalaTest % Test)
  )

lazy val playJsonSupport = (project in file("./play-json"))
  .settings( //
    name := "sdebug-play-json",
    libraryDependencies ++= Seq(scalaTest % Test, playJson)
  )
  .dependsOn(core)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
