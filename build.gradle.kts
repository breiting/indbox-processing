import java.io.File
import java.util.Properties
import org.gradle.internal.os.OperatingSystem
import org.gradle.api.tasks.WriteProperties

plugins { id("java") }

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

// read release.properties
val libraryProperties = Properties().apply {
  load(rootProject.file("release.properties").inputStream())
}

version = if (project.hasProperty("githubReleaseTag")) {
  project.property("githubReleaseTag").toString().drop(1)
} else {
  libraryProperties.getProperty("prettyVersion")
}

//==========================
// USER BUILD CONFIGURATIONS
//==========================

val libName = "indbox"
group = "at.breiting"

// sketchbook location auto-detect (from template)
var sketchbookLocation = ""
val userHome = System.getProperty("user.home")
val currentOS = OperatingSystem.current()

if (currentOS.isMacOsX) {
  sketchbookLocation = if (File("$userHome/Documents/Processing/sketchbook").isDirectory) {
    "$userHome/Documents/Processing/sketchbook"
  } else {
    "$userHome/Documents/Processing"
  }
} else if (currentOS.isWindows) {
  val docsFolder = if (File("$userHome/My Documents").isDirectory) {
    "$userHome/My Documents"
  } else {
    "$userHome/Documents"
  }
  sketchbookLocation = if (File(docsFolder, "Processing/sketchbook").isDirectory) {
    "$docsFolder/Processing/sketchbook"
  } else {
    "$docsFolder/Processing"
  }
} else {
  sketchbookLocation = "$userHome/sketchbook"
}

repositories {
  mavenCentral()
  maven { url = uri("https://jogamp.org/deployment/maven/") }
}

dependencies {
  // Processing core via Maven (template default)
  compileOnly(group = "org.processing", name = "core", version = "4.3.1")

  // Serial backend bundled with the library zip
  implementation("com.fazecast:jSerialComm:2.11.4")

  testImplementation(platform("org.junit:junit-bom:5.10.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

//==============================
// END USER BUILD CONFIGURATIONS
//==============================

// JAR naming
tasks.jar {
  archiveBaseName.set(libName)
  archiveClassifier.set("")
  archiveVersion.set("")
}

// ===========================
// Release tasks (from template)
// ===========================
val releaseRoot = "$rootDir/release"
val releaseName = libName
val releaseDirectory = "$releaseRoot/$releaseName"



tasks.register<WriteProperties>("writeLibraryProperties") {
  group = "processing"
  destinationFile = project.file("library.properties")
  comment = "Processing library metadata"

  property("name", libraryProperties.getProperty("name"))
  property("version", libraryProperties.getProperty("version"))
  property("prettyVersion", project.version.toString())
  property("authors", libraryProperties.getProperty("authors"))
  property("url", libraryProperties.getProperty("url"))
  property("categories", libraryProperties.getProperty("categories"))
  property("sentence", libraryProperties.getProperty("sentence"))
  property("paragraph", libraryProperties.getProperty("paragraph"))
  property("minRevision", libraryProperties.getProperty("minRevision"))
  property("maxRevision", libraryProperties.getProperty("maxRevision"))
}

tasks.build.get().mustRunAfter("clean")
tasks.javadoc.get().mustRunAfter("build")

tasks.register("buildReleaseArtifacts") {
  group = "processing"
  dependsOn("clean", "build", "javadoc", "writeLibraryProperties")
  finalizedBy("packageRelease", "duplicateZipToPdex")

  doFirst {
    println("Releasing library $libName")
    println("Cleaning release...")
    project.delete(files(releaseRoot))
  }

  doLast {
    println("Copy library...")
    copy {
      from(layout.buildDirectory.file("libs/${libName}.jar"))
      into("$releaseDirectory/library")
    }

    println("Copy dependencies...")
    copy {
      from(configurations.runtimeClasspath)
      into("$releaseDirectory/library")
    }

    println("Copy assets...")
    copy {
      from("$rootDir")
      include("shaders/**", "native/**")
      into("$releaseDirectory/library")
      exclude("*.DS_Store")
    }

    println("Copy javadoc...")
    copy {
      from(layout.buildDirectory.dir("docs/javadoc"))
      into("$releaseDirectory/reference")
    }

    println("Copy additional artifacts...")
    copy {
      from(rootDir)
      include("README.md", "readme/**", "library.properties", "examples/**", "src/**")
      into(releaseDirectory)
      exclude("*.DS_Store", "**/networks/**")
    }

    println("Copy repository library.txt...")
    copy {
      from(rootDir)
      include("library.properties")
      into(releaseRoot)
      rename("library.properties", "$libName.txt")
    }
  }
}

tasks.register<Zip>("packageRelease") {
  dependsOn("buildReleaseArtifacts")
  archiveFileName.set("${libName}.zip")
  from(releaseDirectory)
  into(releaseName)
  destinationDirectory.set(file(releaseRoot))
  exclude("**/*.DS_Store")
}

tasks.register<Copy>("duplicateZipToPdex") {
  group = "processing"
  dependsOn("packageRelease")

  from(releaseRoot) {
    include("$libName.zip")
    rename("$libName.zip", "$libName.pdex")
  }
  into(releaseRoot)
}

tasks["duplicateZipToPdex"].mustRunAfter("packageRelease")

tasks.register("deployToProcessingSketchbook") {
  group = "processing"
  dependsOn("buildReleaseArtifacts")
  doLast {
    val installDirectory = file("$sketchbookLocation/libraries/$libName")
    delete(installDirectory)
    copy {
      from(releaseDirectory)
      include("library.properties", "examples/**", "library/**", "reference/**", "src/**")
      into(installDirectory)
    }
  }
}
