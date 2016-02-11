lazy val commonSettings = Seq( organization := "com.github.dunmatt",
                               version := "0.1.0",
                               scalaVersion := "2.11.7")

resolvers += Resolver.mavenLocal
val managedDependencies = Seq(
  "com.github.dunmatt" %% "roboclaw" % "0.2.7"
  // , "com.lihaoyi" %% "scalarx" % "0.2.8"
  , "com.squants" %% "squants" % "0.5.3"
  // , "org.slf4j" % "slf4j-log4j12" % "1.7.13"
  , "org.slf4j" % "slf4j-jdk14" % "1.7.13"
)

lazy val root = (project in file(".")).settings( commonSettings: _*)
                                      .settings(
  name := "Robokomodo"
  , libraryDependencies ++= managedDependencies
)
