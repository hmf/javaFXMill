
<!--- cSpell:ignore openfx --->

# Example of Mill setup of a JavaFX/OpenJFX application   

## Introduction

This project shows how to set up a Mill project that uses 
[OpenJFX (JavaFx)](https://openjfx.io/) library. More specifically it shows how 
one needs to configure the use of the module system for JDK1.9+. Unlike other
modules, the JavaFX cannot be used as regular JAR libraries. This means the JVM 
parameters for the module path and module names must be used. 

The example is avery simple JavaFX application shows a simple single button. When
pressed the message:

    Hello World!

is written to console.

## Getting started

The project has two modules: `managed`and `unmanaged` that you can find in the 
build script `build.sc`. The `managed` module shows how to set up the modules 
using Mill's support for managed libraries. In this case, you need only add the
library to use, the dependent JavaFX modules will be downloaded for you. 

To execute the application use any of these command:
* `./mill -i managed.run`
* `./mill -i managed.runMain helloworld.HelloWorld`
* `./mill -i --watch managed.run`

The `unmanaged` module requires that you list all the dependent JavaFX 
libraries that are required. You can use both managed and unmanaged libraries
simultaneously, so you need list those libraries you wish to use. 

To execute the application use any of these command:
* `./mill -i unmanaged.run`
* `./mill -i unmanaged.runMain helloworld.HelloWorld`
* `./mill -i --watch unmanaged.run`

The `build.sc` tries to automate as much of the JVM module parameters as 
possible. However, it may be necessary to tweak those parameters according 
to you use case. More concretely you may need to add module exports or 
patches (a patch allows you to substitute a boot module with another drop-in 
replacement, for example using [TestFX/Monocle](https://github.com/TestFX/Monocle)). 

## Using IntelliJ

To use this project in IntelliJ execute the following command:

    ./mill mill.scalalib.GenIdea/idea

Note that Mill has support for [Bloop](https://scalacenter.github.io/bloop/). 
However, this needs to be set up in IntelliJ first.

## Using VSCode

In VSCode one need only open and import the Mill build script. Because Mill 
supports [Bloop](https://scalacenter.github.io/bloop/), this should 
automatically work for you provided you have installed the [Metals extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=scalameta.metals#overview).
See also [metals-vscode in Github](https://github.com/scalameta/metals-vscode).


# Historical notes: Issue with using JavaFX libraries

After version `0.5.3` attempting to run a javaFX application breaks.
See:

* Issue [767](https://github.com/lihaoyi/mill/issues/767)
* Issue [775](https://github.com/lihaoyi/mill/pull/775)  
* Issue [759](https://github.com/lihaoyi/mill/issues/759)
* Issue [1947](https://github.com/coursier/coursier/issues/1947)
* Issue [1928](https://github.com/lihaoyi/mill/issues/928)

Discussion on a workaround is [here](https://github.com/lihaoyi/mill/discussions/1105). 
Unfortunately, this also did not work. It seems like it is not only the artifact downloads
that may be causing problems. More specifically in version `0.5.3` _we cannot run a 
class that is **not** marked as a main class_.   

## Launching Mill with the OpenFX modules

### Version 0.5.3

These execute correctly: 
  * `./mill -i javafx.run`
  * `./mill -i javafx.runMain helloworld.HelloWorld`
  * `./mill -i --watch javafx.run`

This fails:
  ``

with the error:

```
Error: JavaFX runtime components are missing, and are required to run this application
```

Previous versions also work.

###Version 0.9.4

Mill fails to resolve the dependencies: 
```
./mill -i javafx.run
[17/31] javafx.resolvedIvyDeps
1 targets failed
javafx.resolvedIvyDeps Failed to load source dependencies
not found: https://repo1.maven.org/maven2/org/openjfx/javafx-controls/13.0.2/javafx-controls-13.0.2-${javafx.platform}.jar
not found: https://repo1.maven.org/maven2/org/openjfx/javafx-graphics/13.0.2/javafx-graphics-13.0.2-${javafx.platform}.jar
not found: https://repo1.maven.org/maven2/org/openjfx/javafx-base/13.0.2/javafx-base-13.0.2-${javafx.platform}.jar
```  

Mill fails to resolve the dependencies:
```
./mill -i javafx.runMain helloworld.HelloWorld
[16/28] javafx.resolvedIvyDeps 
1 targets failed
javafx.resolvedIvyDeps Failed to load source dependencies
  not found: https://repo1.maven.org/maven2/org/openjfx/javafx-controls/13.0.2/javafx-controls-13.0.2-${javafx.platform}.jar
  not found: https://repo1.maven.org/maven2/org/openjfx/javafx-graphics/13.0.2/javafx-graphics-13.0.2-${javafx.platform}.jar
  not found: https://repo1.maven.org/maven2/org/openjfx/javafx-base/13.0.2/javafx-base-13.0.2-${javafx.platform}.jar

```

## Adding the classifier

We added a classifier automatically. In the working Mill versions we see that it makes
no difference toi the downloaded artifacts. 

## Java Installation

### JDK Installation

Tested on JDK 11. because the JDK does not include the JavaFX run-time, this
has to be installed. For Ubuntu, we need to:

* sudo apt install openjfx
* dpkg-query -L openjfx
* java --module-path /usr/share/openjfx/lib --add-modules=javafx.controls,javafx.fxml,javafx.base,javafx.media,javafx.web,javafx.swing -jar '/home/lotfi/Documents/MyAppfolder/my_application.jar'

The `dpkg-query` provides us with the path to the OpenJFX run-time. This
path and the required modules must be added to the `java` comma nd line
arguments.

### JavaFX Installation

You can install and use the system wide JavaFX. But we need to use and indicate the 
module path.     

# Note on configuring Java modules

In Ubuntu linux

```
user@machine:~$ update-alternatives --list java
/usr/lib/jvm/java-11-openjdk-amd64/bin/java
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
/usr/lib/jvm/java-8-oracle/jre/bin/java
```  

The following gets us the current active path:

```
user@machine:~$readlink -f /usr/bin/java
/usr/lib/jvm/java-11-openjdk-amd64/bin/java
``` 

Look at [Gradle JavaFX plugin](https://github.com/openjfx/javafx-gradle-plugin)
It provides the module paths

# Libraries

## Layout libraries

1. http://miglayout.com/

## Widget Libraries

1. https://github.com/HanSolo/Medusa
1. https://github.com/HanSolo/tilesfx
1. https://github.com/kordamp/jsilhouette
1. https://github.com/jidesoft/jidefx-oss (dead?)
1. https://github.com/FXMisc/RichTextFX
1. https://jfxtras.org/
1. https://github.com/controlsfx/controlsfx
1. https://github.com/gluonhq/maps
1. http://www.object-refinery.com/orsoncharts/
   1. https://github.com/jfree/orson-charts
1. https://github.com/HanSolo/charts
1. https://openjfx.io/javadoc/13/javafx.controls/javafx/scene/chart/package-summary.html

## Styling Libraries

1. https://github.com/sshahine/JFoenix
1. https://github.com/kordamp/bootstrapfx
1. https://bitbucket.org/Jerady/fontawesomefx/src/master/
1. https://github.com/kordamp/ikonli

## Testing Libraries

1. https://github.com/TestFX/TestFX
   1. http://testfx.github.io/TestFX/
  
## Frameworks

1. http://afterburner.adam-bien.com/
   1. https://github.com/AdamBien/followme.fx (dead?)
1. https://github.com/JacpFX/JacpFX (dead?)      
   1. http://jacpfx.org/
1. https://github.com/sialcasa/mvvmFX (dead?)
1. http://griffon-framework.org/
   1. https://github.com/griffon/griffon
1. https://github.com/basilisk-fw/basilisk (dead?)

## List od JavaFX stuff

1. https://github.com/mhrimaz/AwesomeJavaFX
  

1. https://github.com/brunomnsilva/JavaFXSmartGraph
