import java.util.Date

import sbt.{Credentials, Path, Resolver}

//import UnidocKeys._
import sbt.{Credentials, Path, Resolver}

// dependency versions
val akka = "2.5.13"
val aws = "1.11.343"
val scalaCheck = "org.scalacheck"     %% "scalacheck"                          % "1.14.0"
val scalaTest  = "org.scalatest"      %% "scalatest"                           % "3.0.5" % "test"
val sprayJson  = "io.spray"           %% "spray-json"                          % "1.3.4"
val cftg       = "com.monsanto.arch"  %% "cloud-formation-template-generator"  % "3.9.1"

def crossVersionScalaOptions(scalaVersion: String) = {
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 11)) => Seq(
      "-Yclosure-elim",
      "-Yinline"
    )
    case _ => Nil
  }
}

val compileOnlyOptions = Seq(
  "-deprecation",
  "-Xlint",
  "-Xverify"
)

lazy val commonSettings = Seq(
  // metadata
  homepage := Some(url("https://monsantoco.github.io/aws2scala")),
  organization := "com.monsanto.arch",
  organizationName := "Monsanto",
  organizationHomepage := Some(url("http://engineering.monsanto.com")),
  startYear := Some(2015),
  licenses := Seq("BSD New" → url("http://opensource.org/licenses/BSD-3-Clause")),

  // scala compilation
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.8", "2.12.3"),
  releaseCrossBuild := true,
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-unchecked",
    "-feature"
  ),
  (scalacOptions in Compile) ++= compileOnlyOptions ++ crossVersionScalaOptions(scalaVersion.value),
  (scalacOptions in Test) --= compileOnlyOptions,

  // Needed to avoid OOM errors
  (fork in Test) := true,

  // documentation
  apiMappingsScala ++= Map(
    ("com.typesafe.akka", "akka-actor") → "http://doc.akka.io/api/akka/%s",
    ("com.typesafe.akka", "akka-stream") → "http://doc.akka.io/api/akka/%s"
  ),
  apiMappingsJava ++= Map(
    ("com.typesafe", "config") → "http://typesafehub.github.io/config/latest/api"
  ) ++ createAwsApiMappings("core", "cloudformation", "ec2", "iam", "kms", "rds", "s3", "sns", "sts"),

  // coverage
  coverageExcludedPackages := "com\\.monsanto\\.arch\\.awsutil\\.test_support\\..*;com\\.monsanto\\.arch\\.awsutil\\.testkit\\..*",

  // Allow resolution on JCenter
  resolvers += Resolver.jcenterRepo
)

val AwsDocURL = "http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc"

def createAwsApiMappings(libs: String*) = libs.map(lib ⇒ ("com.amazonaws", s"aws-java-sdk-$lib") → AwsDocURL).toMap
publishTo in ThisBuild := Def.taskDyn[Option[Resolver]] {
  if (isSnapshot.value)
    Def.task(Some("Artifactory Realm" at "https://oss.jfrog.org/oss-snapshot-local/"))
  else
    Def.task(publishTo.value) /* Value set by bintray-sbt plugin */
}.value

credentials := Def.taskDyn[Seq[Credentials]] {
  if (isSnapshot.value)
    Def.task(List(Path.userHome / ".bintray" / ".artifactory").filter(_.exists).map(Credentials(_)))
  else
    Def.task(credentials.value) /* Value set by bintray-sbt plugin */
}.value

lazy val bintrayPublishingSettings = Seq(
  bintrayOrganization := Some("monsanto"),
  bintrayPackageLabels := Seq("aws", "scala", "akka-streams"),
  bintrayVcsUrl := Some("https://github.com/MonsantoCo/aws2scala.git")
)

lazy val noPublishingSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val commonDependencies = Seq(
  "com.typesafe.akka"           %% "akka-stream"    % akka,
  "com.typesafe.scala-logging"  %% "scala-logging"  % "3.9.0",
  awsDependency("core")
)

def awsDependency(lib: String): ModuleID = "com.amazonaws" % s"aws-java-sdk-$lib" % aws

def awsDependencies(libs: String*): Seq[ModuleID] = libs.map(awsDependency)

lazy val testSupport = Project("aws2scala-test-support", file("aws2scala-test-support"))
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Common configuration and utilities for testing in aws2scala",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-slf4j"                   % akka,
      scalaCheck,
      "org.scalamock"      %% "scalamock-scalatest-support"  % "3.6.0",
      scalaTest,
      "ch.qos.logback"      % "logback-classic"              % "1.2.3"
    ) ++ commonDependencies
  )

lazy val coreMacros = Project("aws2scala-core-macros", file("aws2scala-core-macros"))
  .dependsOn(testSupport % "test->test")
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Macros and libraries to enable Akka stream support in aws2scala",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-stream-testkit"  % akka                % "test",
      "org.scala-lang"      % "scala-reflect"        % scalaVersion.value
    ) ++ commonDependencies
  )

lazy val core = Project("aws2scala-core", file("aws2scala-core"))
  .dependsOn(coreMacros)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Core library for aws2scala",
    libraryDependencies += "com.typesafe" % "config" % "1.3.3"
  )

lazy val coreTestSupport = Project("aws2scala-core-test-support", file("aws2scala-core-test-support"))
  .dependsOn(core, testSupport)
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Additional aws2scala test support that depends on the core library"
  )

lazy val coreTestkit = Project("aws2scala-core-testkit", file("aws2scala-core-testkit"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-core",
    libraryDependencies += scalaCheck
  )

lazy val coreTests = Project("aws2scala-core-tests", file("aws2scala-core-tests"))
  .dependsOn(
    core             % "test",
    coreTestSupport  % "test",
    coreTestkit      % "test",
    testSupport      % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Test suite for aws2scala-core",
    libraryDependencies ++= Seq(
      sprayJson            % "test",
      awsDependency("s3")  % "test"
    )
  )

lazy val cloudFormation = Project("aws2scala-cloudformation", file("aws2scala-cloudformation"))
  .dependsOn(core, testSupport % "test->test", coreTestSupport % "test")
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for AWS CloudFormation",
    libraryDependencies ++= Seq(
      awsDependency("cloudformation"),
      sprayJson  % "test",
      cftg       % "test"
    )
  )

lazy val ec2 = Project("aws2scala-ec2", file("aws2scala-ec2"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for Amazon Elastic Cloud Compute (EC2)",
    libraryDependencies += awsDependency("ec2")
  )

lazy val ec2Testkit = Project("aws2scala-ec2-testkit", file("aws2scala-ec2-testkit"))
  .dependsOn(ec2, coreTestkit, iamTestkit)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-ec2",
    libraryDependencies ++= Seq(
      scalaCheck,
      "org.bouncycastle" % "bcprov-jdk15on" % "1.59",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.59"
    )
  )

lazy val ec2Tests = Project("aws2scala-ec2-tests", file("aws2scala-ec2-tests"))
  .dependsOn(
    ec2             % "test",
    ec2Testkit      % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Tests for aws2scala-ec2"
  )

lazy val iam = Project("aws2scala-iam", file("aws2scala-iam"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for AWS Identity and Access Management (IAM)",
    libraryDependencies += awsDependency("iam")
  )

lazy val iamTestkit = Project("aws2scala-iam-testkit", file("aws2scala-iam-testkit"))
  .dependsOn(iam, coreTestkit, sts)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-iam",
    libraryDependencies ++= Seq(scalaCheck, sprayJson)
  )

lazy val iamTests = Project("aws2scala-iam-tests", file("aws2scala-iam-tests"))
  .dependsOn(
    iam             % "test",
    iamTestkit      % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Test suite for aws2scala-iam"
  )

lazy val kms = Project("aws2scala-kms", file("aws2scala-kms"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for AWS Key Management Service (KMS)",
    libraryDependencies += awsDependency("kms")
  )

lazy val kmsTestkit = Project("aws2scala-kms-testkit", file("aws2scala-kms-testkit"))
  .dependsOn(kms, coreTestkit)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-kms",
    libraryDependencies += scalaCheck
  )

lazy val kmsTests = Project("aws2scala-kms-tests", file("aws2scala-kms-tests"))
  .dependsOn(
    kms             % "test",
    kmsTestkit      % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Tests for aws2scala-kms"
  )

lazy val rds = Project("aws2scala-rds", file("aws2scala-rds"))
  .dependsOn(core, testSupport % "test->test", coreTestSupport % "test")
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for Amazon Relational Database Service (RDS)",
    libraryDependencies += awsDependency("rds")
  )

lazy val s3 = Project("aws2scala-s3", file("aws2scala-s3"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for Amazon Simple Storage Service (S3)",
    libraryDependencies += awsDependency("s3")
  )

lazy val s3Testkit = Project("aws2scala-s3-testkit", file("aws2scala-s3-testkit"))
  .dependsOn(s3, coreTestkit)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-s3",
    libraryDependencies += scalaCheck
  )

lazy val s3Tests = Project("aws2scala-s3-tests", file("aws2scala-s3-tests"))
  .dependsOn(
    s3              % "test",
    s3Testkit       % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Tests for aws2scala-s3",
    libraryDependencies += sprayJson
  )

lazy val sns = Project("aws2scala-sns", file("aws2scala-sns"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for Amazon Simple Notification Service (SNS)",
    libraryDependencies += awsDependency("sns")
  )

lazy val snsTestkit = Project("aws2scala-sns-testkit", file("aws2scala-sns-testkit"))
  .dependsOn(sns, coreTestkit, iamTestkit)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-sns",
    libraryDependencies ++= Seq(scalaCheck, sprayJson)
  )

lazy val snsTests = Project("aws2scala-sns-tests", file("aws2scala-sns-tests"))
  .dependsOn(
    sns             % "test",
    snsTestkit      % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Tests for aws2scala-sns"
  )

lazy val sqs = Project("aws2scala-sqs", file("aws2scala-sqs"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for Amazon Simple Queue Service (SQS)",
    libraryDependencies += awsDependency("sqs")
  )

lazy val sqsTestkit = Project("aws2scala-sqs-testkit", file("aws2scala-sqs-testkit"))
  .dependsOn(sqs, coreTestkit)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-sqs",
    libraryDependencies ++= Seq(scalaCheck)
  )

lazy val sqsTests = Project("aws2scala-sqs-tests", file("aws2scala-sqs-tests"))
  .dependsOn(
    sqs             % "test",
    sqsTestkit      % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Tests for aws2scala-sqs"
  )

lazy val sts = Project("aws2scala-sts", file("aws2scala-sts"))
  .dependsOn(core)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Client for AWS Security Token Service (STS)",
    libraryDependencies += awsDependency("sts")
  )

lazy val stsTestkit = Project("aws2scala-sts-testkit", file("aws2scala-sts-testkit"))
  .dependsOn(sts, coreTestkit, iamTestkit)
  .settings(
    commonSettings,
    bintrayPublishingSettings,
    description := "Test utility library for aws2scala-sts",
    libraryDependencies += scalaCheck
  )

lazy val stsTests = Project("aws2scala-sts-tests", file("aws2scala-sts-tests"))
  .dependsOn(
    sts             % "test",
    stsTestkit      % "test",
    coreTestSupport % "test",
    testSupport     % "test->test"
  )
  .settings(
    commonSettings,
    noPublishingSettings,
    description := "Tests for aws2scala-sts"
  )

lazy val integrationTests = Project("aws2scala-integration-tests", file("aws2scala-integration-tests"))
  .dependsOn(core, testSupport, cloudFormation, ec2, iam, kms, rds, s3, sns, sqs, sts)
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    noPublishingSettings,
    Defaults.itSettings,
    description := "Integration test suite for aws2scala",
    libraryDependencies += cftg % "it"
  )

lazy val aws2scala = (project in file("."))
  .aggregate(
    testSupport,
    coreMacros, core, coreTestSupport, coreTests, coreTestkit,
    cloudFormation,
    ec2, ec2Testkit, ec2Tests,
    kms, kmsTestkit, kmsTests,
    iam, iamTestkit, iamTests,
    rds,
    s3, s3Testkit, s3Tests,
    sns, snsTestkit, snsTests,
    sqs, sqsTestkit, sqsTests,
    sts, stsTestkit, stsTests,
    integrationTests)
  .settings(
    commonSettings,
    noPublishingSettings,
    // unidoc
//    unidocSettings,
//    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(testSupport, coreTestSupport)
  )
