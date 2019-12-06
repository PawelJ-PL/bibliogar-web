packageName in Docker := "paweljpl/bibliogar-web"
maintainer in Docker := "Pawel <inne.poczta@gmail.com>"

dockerBaseImage := "openjdk:11-jre-slim"
dockerExposedPorts ++= Seq(8181, 8181)