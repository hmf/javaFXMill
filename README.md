# Example of Mill setup of a JavaFX/OpenJFX application   

## Introduction

The application shows a simple single button. When pressed the message:

    Hello World!

is written to console.

## Getting started

Command to execute the application:
* `./mill -i managed.run`
* `./mill -i managed.runMain helloworld.HelloWorld`
* `./mill -i --watch managed.run`


Command to execute the application:
* `./mill -i unmanaged.run`
* `./mill -i unmanaged.runMain helloworld.HelloWorld`
* `./mill -i --watch unmanaged.run`

# Issue with using JavaFX libraries  (historical notes)

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
