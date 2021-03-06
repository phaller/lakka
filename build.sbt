organization := "io.github.fsommar"
name := "lakka"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-target:jvm-1.8",
  "-unchecked",
  "-language:postfixOps",
  "-P:lacasa:enable"
)

autoCompilerPlugins := true

addCompilerPlugin("io.github.phaller" % "lacasa-plugin_2.11.8" % "0.1.0-SNAPSHOT")

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.11",
  "io.github.phaller" % "lacasa-core_2.11.8" % "0.1.0-SNAPSHOT",
  "io.github.phaller" % "lacasa-akka_2.11.8" % "0.1.0-SNAPSHOT",
  "junit" % "junit" % "4.8.1" % "test",
  "org.scalatest" % "scalatest_2.11" % "2.1.3" % "test"
)

logLevel := Level.Info
logLevel in compile := Level.Info
logLevel in test := Level.Info

cancelable in Global := true
