val commonSettings = Seq(
  name := "bibliogar",
  version := "0.1",
  scalaVersion := "2.13.1"
)

val root = (project in file("."))
  .settings(
    commonSettings,
    Dependencies.all,
    ScalaOpts.options,
    Defaults.itSettings
  )
  .configs(IntegrationTest)

parallelExecution in IntegrationTest := false
testForkedParallel in IntegrationTest := false