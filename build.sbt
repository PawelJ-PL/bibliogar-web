val commonSettings = Seq(
  name := "bibliogar",
  scalaVersion := "2.13.1"
)

javaAgents += "io.kamon" % "kanela-agent" % "1.0.6"

val root = (project in file("."))
  .settings(
    commonSettings,
    Dependencies.all,
    ScalaOpts.options,
    Defaults.itSettings,
    useJGit
  )
  .enablePlugins(GitVersioning)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAgent)
  .configs(IntegrationTest)

git.gitTagToVersionNumber := { tag: String =>
  if(tag matches "[0-9]+\\..*") Some(tag)
  else None
}

parallelExecution in IntegrationTest := false
testForkedParallel in IntegrationTest := false