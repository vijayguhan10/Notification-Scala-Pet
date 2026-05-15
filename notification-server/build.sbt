name := "notification-server"

organization := "com.metropolis.pet"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

scalaVersion := "2.13.18"

PlayKeys.playDefaultPort := 7000

val slickVersion = "3.5.2"
val flywayVersion = "10.20.0"
val postgresDriverVersion = "42.7.4"
val rabbitMqClientVersion = "5.23.0"

// Only override core Jackson modules IF needed.
// Play 2.9.x already manages Jackson versions internally.
val jacksonVersion = "2.14.3"

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
)

libraryDependencies ++= Seq(
  // Play
  guice,
  ws,

  // Testing
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,

  // Kafka
  "org.apache.kafka" % "kafka-clients" % "3.7.0",

  // RabbitMQ
  "com.rabbitmq" % "amqp-client" % rabbitMqClientVersion,

  // Slick
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,

  // PostgreSQL
  "org.postgresql" % "postgresql" % postgresDriverVersion,

  // Flyway
  "org.flywaydb" % "flyway-core" % flywayVersion,
  "org.flywaydb" % "flyway-database-postgresql" % flywayVersion
)
