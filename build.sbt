lazy val commonSettings = Seq( organization := "com.github.dunmatt",
                               version := "0.1.0",
                               scalaVersion := "2.11.7")

resolvers += Resolver.mavenLocal
val managedDependencies = Seq(
  "com.squants" %% "squants" % "0.5.3"
  , "com.github.dunmatt" %% "roboclaw" % "0.1.0"
)

lazy val root = (project in file(".")).settings( commonSettings: _*)
                                      .settings(
  name := "Robokomodo"
  , libraryDependencies ++= managedDependencies
)
                                      