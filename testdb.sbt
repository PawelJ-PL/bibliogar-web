import java.io.File

import scala.sys.process.Process

lazy val runTestDb = taskKey[Unit]("Build Docker with test database")

runTestDb := {
  val srcMigrations =  new File(baseDirectory.value.absolutePath + "/src/main/resources/db/changelog/changelog-master.yml")
  val dstMigrations = new File(baseDirectory.value.absolutePath + "/testdb/resources/changelog-master.yml")
  IO.copyFile(srcMigrations, dstMigrations)
  val workdir =  new File(baseDirectory.value.absolutePath + "/testdb")
  Process(Seq("docker", "build", "-t", "bibliogartestdb", "."), workdir).!
  Process(Seq("docker", "run", "-d", "--rm", "--name", "bibliogartestdb", "-p", "5432:5432",  "bibliogartestdb"), workdir).!
}