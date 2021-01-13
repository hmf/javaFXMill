# Issue with using JavaFX libraries  

After version `0.5.3` attempting to run a javaFX application breaks.
See:

* Issue [767](https://github.com/lihaoyi/mill/issues/767)
* Issue [775](https://github.com/lihaoyi/mill/pull/775)  
* Issue [759](https://github.com/lihaoyi/mill/issues/759)
* Issue [1947](https://github.com/coursier/coursier/issues/1947)
* Issue [1928](https://github.com/lihaoyi/mill/issues/928)

## Launching Mill with the OpenFX modules

### Version 0.5.3

These execute correctly: 
  * `./mill -i javafx.run`
  * `./mill -i javafx.runMain Main`

This fails:
  `./mill -i javafx.runMain helloworld.HelloWorld`

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