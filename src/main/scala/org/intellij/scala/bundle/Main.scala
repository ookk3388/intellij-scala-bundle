package org.intellij.scala.bundle

import java.io.File
import java.net.URL

import org.intellij.scala.bundle.Descriptor._
import org.intellij.scala.bundle.Mapper.{matches, _}

import scala.Function._

/**
  * @author Pavel Fatin
  */
object Main {
  private val Version = "2017.3.4"

  def main(args: Array[String]): Unit = {
    val target = file("./target")

    val repository = target / "repository"

    repository.mkdir()

    Components.All.filter(_.downloadable).par.foreach { component =>
      downloadComponent(repository, component)
    }

    packageScalaLibrarySources(repository, Components.Scala.Sources)

    val commands = Seq(
      () => build(repository, Components.All, Descriptors.Windows)(target / s"intellij-scala-bundle-$Version-windows.zip"),
      () => build(repository, Components.All, Descriptors.Linux)(target / s"intellij-scala-bundle-$Version-linux.tar.gz"),
      () => build(repository, Components.All, Descriptors.Mac)(target / s"intellij-scala-bundle-$Version-osx.tar.gz"))

    commands.par.foreach(_.apply())

    info(s"Done.")
  }

  private object Versions {
    val Idea = "173.4548.28"
    val IdeaWindows = "2017.3.4" // for idea.exe only
    val ScalaPlugin = "2017.3.11.1"
    val Runtime = "8u152b1024.11"
    val Scala = "2.12.4"
  }

  private object Components {
    object Idea {
      val Bundle = Component(s"https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/${Versions.Idea}/ideaIC-${Versions.Idea}.zip")
      val Windows = Component(s"https://download.jetbrains.com/idea/ideaIC-${Versions.IdeaWindows}.win.zip")
      val ScalaPlugin = Component(s"https://plugins.jetbrains.com/files/1347/42173/scala-intellij-bin-${Versions.ScalaPlugin}.zip")
      val Resources = Component("../../src/main/resources")
    }

    object Runtime {
      val Windows = Component(s"https://bintray.com/jetbrains/intellij-jdk/download_file?file_path=jbrex${Versions.Runtime}_windows_x86.tar.gz")
      val Linux = Component(s"https://bintray.com/jetbrains/intellij-jdk/download_file?file_path=jbrex${Versions.Runtime}_linux_x64.tar.gz")
      val Mac = Component(s"https://bintray.com/jetbrains/intellij-jdk/download_file?file_path=jbrex${Versions.Runtime}_osx_x64.tar.gz")
    }

    object Scala {
      val Windows = Component(s"https://downloads.lightbend.com/scala/${Versions.Scala}/scala-${Versions.Scala}.zip")
      val Unix = Component(s"https://downloads.lightbend.com/scala/${Versions.Scala}/scala-${Versions.Scala}.tgz")
      val Sources = Component(s"https://github.com/scala/scala/archive/v${Versions.Scala}.tar.gz")
      val LibrarySources = Component("./")
    }

    val All = Seq(
      Idea.Bundle, Idea.Windows, Idea.ScalaPlugin, Idea.Resources,
      Runtime.Windows, Runtime.Linux, Runtime.Mac,
      Scala.Windows, Scala.Unix, Scala.Sources, Scala.LibrarySources
    )
  }

  private object Descriptors {
    import Components._

    private val Common: Descriptor = {
      case Idea.Bundle =>
        matches("bin/appletviewer\\.policy") |
          matches("bin/log\\.xml") |
          matches("lib/.*") - matches("lib/libpty.*") |
          matches("license/.*") |
          matches("plugins/(git4idea|github|junit|IntelliLang|maven|properties|terminal)/.*") |
          matches("build.txt") |
          matches("LICENSE.txt") |
          matches("NOTICE.txt")
      case Idea.ScalaPlugin =>
        to("data/plugins/")
      case Scala.LibrarySources =>
        from(s"scala-library-sources-${Versions.Scala}.zip") & to("scala/src/scala-library.zip")
      case Idea.Resources =>
        matches("data/.*")
    }

    private val WindowsSpecific: Descriptor = {
      case Idea.Bundle =>
        from("bin/win/") & to("bin/") |
          matches("bin/.*\\.(bat|dll|exe|ico)") |
          matches("lib/libpty/win/.*")
      case Idea.Windows =>
        matches("bin/idea.exe")
      case Runtime.Windows =>
        from("jre") & to("jre32/")
      case Scala.Windows =>
        from(s"scala-${Versions.Scala}/") & to("scala/")
      case Idea.Resources =>
        matches("IDEA.lnk")
    }

    private val LinuxSpecific: Descriptor = {
      case Idea.Bundle =>
        from("bin/linux/") & to("bin/") |
          matches("bin/.*\\.(py|sh|png)") | matches("bin/fsnotifier") |
          matches("lib/libpty/linux/.*")
      case Runtime.Linux =>
        from("jre") & to("jre64/")
      case Scala.Unix =>
        from(s"scala-${Versions.Scala}/") & to("scala/")
      case Idea.Resources =>
        matches("idea.sh") & edit(_.replaceAll("\r", ""))
    }

    private val MacSpecific: Descriptor = {
      case Idea.Bundle =>
        from("bin/mac/") & to("bin/") |
          matches("bin/.*\\.(py|sh|dylib)") - matches("bin/idea\\.sh") - matches("bin/restart\\.py") |
          matches("bin/fsnotifier") |
          matches("bin/restarter") |
          matches("MacOS/.*") |
          matches("Resources/.*") |
          matches("Info\\.plist") |
          matches("lib/libpty/macosx/.*")
      case Runtime.Mac =>
        any
      case Scala.Unix =>
        from(s"scala-${Versions.Scala}/") & to("scala/")
    }

    private def Patches(separator: String): Descriptor = {
      case Idea.Bundle =>
        matches("bin/idea\\.properties") & edit(_ + IdeaPropertiesPatch.replaceAll("\n", separator)) |
          matches("build.txt") & edit(const(BuildTxtPatch.replaceAll("\n", separator))) |
          any
      case _ => any
    }

    private val IdeaPropertiesPatch: String = "\n" +
      "idea.config.path=${idea.home.path}/data/config\n\n" +
      "idea.system.path=${idea.home.path}/data/system\n\n" +
      "idea.plugins.path=${idea.home.path}/data/plugins\n      "

    private def BuildTxtPatch =
      s"IntelliJ Scala Bundle $Version:\n\n" +
        s"* IntelliJ IDEA ${Versions.Idea}\n" +
        s"* Scala Plugin ${Versions.ScalaPlugin}\n" +
        s"* Scala ${Versions.Scala}\n\n" +
        s"See https://github.com/JetBrains/intellij-scala-bundle for more info."

    private val Permissions: Descriptor = {
      case _ =>
        (matches("bin/.*\\.(sh|py)") |
          matches("bin/fsnotifier(|-arm|64)") |
          matches("bin/restarter") |
          matches("MacOS/idea") |
          matches("idea.sh")) & setMode(100755) | any
    }

    val Windows: Descriptor = ((Common | WindowsSpecific) & Patches("\r\n")).andThen(_ & to(s"intellij-scala-bundle-$Version/"))

    val Linux: Descriptor  = ((Common | LinuxSpecific) & Patches("\n") & Permissions).andThen(_ & to(s"intellij-scala-bundle-$Version/"))

    val Mac: Descriptor = ((Common | MacSpecific) & Patches("\n") & Permissions).andThen(_ & to(s"intellij-scala-bundle-$Version.app/Contents/"))
  }

  private def build(base: File, components: Seq[Component], descriptor: Descriptor)(output: File) {
    info(s"Building ${output.getName}...")

    using(Destination(output)) { destination =>
      components.foreach { component =>
        descriptor.lift(component).foreach { mapper =>
          using(Source(base / component.path)) { source =>
            source.collect(mapper).foreach(destination(_))
          }
        }
      }
    }
  }

  private def downloadComponent(base: File, component: Component): Unit = {
    val destination = base / component.path

    if (!destination.exists) {
      info(s"Downloading ${component.path}...")
      download(new URL(component.location), destination)
      if (!destination.exists) {
        error(s"Error downloading ${component.location}")
        sys.exit(-1)
      }
    }
  }

  private def packageScalaLibrarySources(base: File, component: Component): Unit = {
    val target = base / s"scala-library-sources-${Versions.Scala}.zip"

    if (!target.exists) {
      info(s"Packaging Scala sources...")

      using(Destination(target)) { destination =>
        Source(base / component.path).collect(from(s"scala-${Versions.Scala}/src/library/")).foreach(destination(_))
      }
    }
  }
}
