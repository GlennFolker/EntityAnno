# `EntityAnno`
Utility tools for generating [`Mindustry`](https://github.com/Anuken/Mindustry) custom entity component classes.

## Installation
Note that this only works with Java projects, not Kotlin or Scala or other similar JVM languages. The important bits are as following:

0. - Install [JDK 17](https://adoptium.net/temurin/releases/) or above.
   - Use [Anuke's mod template](https://github.com/Anuken/MindustryJavaModTemplate). Not doing so will render you as "know what you're doing" and it's your responsibility to bend the guides as suited to your needs (see [Entesting](https://github.com/GlennFolker/Entesting) for an example without the template).
   - Assume `/` is your mod's root folder.
   - If mentioned files don't exist, create them.
1. Go to `/settings.gradle` and add these lines:
   ```gradle
   pluginManagement{
       repositories{
           gradlePluginPortal()
           maven{url 'https://www.jitpack.io'}
       }
   }
   
   if(JavaVersion.current().ordinal() < JavaVersion.VERSION_17.ordinal()){
       throw new GradleException("JDK 17 is a required minimum version. Yours: ${System.getProperty('java.version')}")
   }
   ```
   This is done so that Gradle can find this plugin, and to enforce usage of Java 17+ for compiling.
2. Go to `/gradle.properties` and add these lines:
   ```properties
   mindustryVersion = v144.3
   arcVersion = v144.3
   entVersion = 1.0.0

   kapt.include.compile.classpath = false
   kotlin.stdlib.default.dependency = false
   ```
   - You can tweak `mindustryVersion` to any tag/commit you prefer (and have `arcVersion` _exactly_ the same as said `Mindustry` version uses, done by looking at the `archash` property in `Mindustry`'s `gradle.properties`).
   - `entVersion` should be left alone, and set to the latest release of _this_ repository (not `Mindustry`! Exact fetched sources will be dealt with later).
   - The KAPT/Kotlin stuff at the bottom is used to decrease compile-time penalty and not use the entire Kotlin JVM standard libraries, because they're literally pointless in this context.
3. Go to `/gradle.properties`, and in the property `org.gradle.jvmargs`, replace `--add-exports` with `--add-opens` and remove `--illegal-access=permit` so it looks like below:
   ```properties
   org.gradle.jvmargs = \
   --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED \
   --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
   --add-opens=java.base/sun.reflect.annotation=ALL-UNNAMED
   ```
   This is done to grant necessary internal API accesses for the annotation processor.
4. Go to `/build.gradle` and replace this line:
   ```gradle
   apply plugin: "java"
   ```
   With these:
   ```gradle
   plugins{
       id 'java'
       id 'com.github.GlennFolker.EntityAnno' version "$entVersion"
   }
   ```
   This is the core part of the usage.
5. Go to `/build.gradle` and replace these lines:
   ```gradle
   targetCompatibility = 8
   sourceCompatibility = JavaVersion.VERSION_16
   ```
   With these:
   ```gradle
   sourceCompatibility = 17
   tasks.withType(JavaCompile).configureEach{
       sourceCompatibility = 17
       options.release = 8

       options.incremental = true
       options.encoding = 'UTF-8'
   }
   ```
   This is to allow compiling with Java 17 syntaxes while targeting Java 8 bytecodes.
6. Go to `/build.gradle` and replace these lines:
   ```gradle
   ext{
       //the build number that this mod is made for
       mindustryVersion = 'v143.1'
       jabelVersion = "93fde537c7"
       sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
   }

   //java 8 backwards compatibility flag
   allprojects{
       tasks.withType(JavaCompile){
           options.compilerArgs.addAll(['--release', '8'])
       }
   }
   ```
   With these:
   ```gradle
   ext{
       sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
   }
   ```
7. Go to `/build.gradle` and replace these lines:
   ```gradle
   dependencies{
       compileOnly "com.github.Anuken.Arc:arc-core:$mindustryVersion"
       compileOnly "com.github.Anuken.MindustryJitpack:core:$mindustryVersion"

       annotationProcessor "com.github.Anuken:jabel:$jabelVersion"
   }
   ```
   With these (without the comments, of course):
   ```gradle
   dependencies{
       // i.
       compileOnly "com.github.Anuken.Arc:arc-core:$arcVersion"
       compileOnly "com.github.Anuken.MindustryJitpack:core:$mindustryVersion"

       // ii.
       annotationProcessor "com.github.GlennFolker.EntityAnno:downgrader:$entVersion"

       // iii.
       compileOnly "com.github.GlennFolker.EntityAnno:entity:$entVersion"
       // iv.
       kapt "com.github.GlennFolker.EntityAnno:entity:$entVersion"
   }
   ```
   1. Adds `Mindustry` and `Arc` as a compile classpath.
   2. Lets you use Java 9+ syntaxes while still targeting Java 8 bytecode (which is necessary), mostly because Java is stupid.
   3. Adds the annotation processor classpath into your project, without bundling them into the final `.jar`.
   4. Registers the annotation processor to the compiler. _Why KAPT?_ Because KAPT is fast and generally friendly to incremental compilation, especially if your project is decoupled into several modules (like [Confictura](https://github.com/GlennFolker/Confictura)).
8. Go to `/build.gradle` and remove these lines:
   ```gradle
   //force arc version
   configurations.all{
       resolutionStrategy.eachDependency { details ->
           if(details.requested.group == 'com.github.Anuken.Arc'){
               details.useVersion "$mindustryVersion"
           }
       }
   }
   ```
9. Add this property block in `/build.gradle` wherever you like (as long as it's done in project evaluation, that is):
   ```gradle
   entityAnno{
       modName = 'your-mod-name'
       mindustryVersion = project['mindustryVersion']
       revisionDir = file("$rootDir/revisions/")
       fetchPackage = 'yourmod.fetched'
       genSrcPackage = 'yourmod.entities.comp'
       genPackage = 'yourmod.gen.entities'
   }
   ```
   - `modName` is the internal mod name as specified in your `/mod.json`.
   - `mindustryVersion` is the `Mindustry` version that you use (`project['mindustryVersion']` refers to the property in `/gradle.properties`, so make sure the property name matches!), so that the annotation processor fetches correct entity component source codes.
   - `revisionDir` is used for saves and netcodes history, don't worry about it. Just make sure _not_ to `.gitignore` the folder.
   - `fetchPackage`, `genSrcPackage`, and `genPackage` are respectively the package names for storing downloaded vanilla sources, your entity component sources (that'll be excluded from the final `.jar`), and the resulting generated entity classes. Change `yourmod` to your mod's root package name.
10. Add the line `EntityRegistry.register();` (from the `genPackage`) in your mod class' `loadContent()` method.
11. Refer to [usage](/USAGE.md) for more detailed entity annotation usages.
12. Compile and use the mod as the guide in the mod template says.

## Contributing
This project is licensed under [GNU GPL v3.0](/LICENSE).

## Version Compatibility
| `Mindustry`/`Arc` | `EntityAnno` |
| ----------------- | ------------ |
| `v144.3`          | `1.0.0`      |
