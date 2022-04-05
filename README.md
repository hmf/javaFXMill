
<!--- cSpell:ignore openfx, javafx, unmanaged, helloworld --->

# Example of Mill setup of a JavaFX/OpenJFX application   

## Introduction

This project shows how to set up a Mill project that uses [OpenJFX (JavaFx)](https://openjfx.io/) library. More specifically it shows how one needs to configure the use of the module system for JDK1.9+. Unlike other modules, the JavaFX cannot be used as regular JAR libraries. This means the JVM parameters for the module path and module names must be used. 

The project contains two examples of a very simple JavaFX application that shows a button. In the first application, when pressed the message:

    Hello World!

is written to console. One can exit the application when desired. The second application starts up a very small dialogue box. Pressing the button terminates the application. Each of these demonstrates alternate ways of starting up a JavaFX/OpenFX application from Scala. 

## Getting started

The project has three modules: `javafx`, `managed`and `unmanaged` that you can find in the  build script `build.sc`. Both the `javafx` and `managed` module shows how to set up the modules using Mill's support for managed libraries. In these cases, you need only add the library to use, the dependent JavaFX modules will be downloaded for you. 

The `javafx` module is an example of the use of pure Java code. To execute the application use any of these command:

* `./mill -i javafx.run`
* `./mill -i javafx.runMain helloworld.HelloWorld`
* `./mill -i --watch javafx.run`
* `./mill -i javafx.runMain button.Main`
* `./mill -i --watch javafx.runMain button.Main`


The `managed` module has two applications, each exemplifying 2 different ways to start Scala JavaFX (OpenFX) applications (*one cannot use the class instance directly*). To execute the application use any of these command:

* `./mill -i managed.run`
* `./mill -i managed.runMain helloworld.HelloWorld`
* `./mill -i --watch managed.run`
* `./mill -i managed.runMain button.Main`
* `./mill -i --watch managed.runMain button.Main`

The `unmanaged` module requires that you list all the dependent JavaFX libraries that are required. You can use both managed and unmanaged libraries simultaneously, so you need list those libraries you wish to use. This module has the exact same applications, so the command are the same save for the module name. 

To execute the application use any of these command:

* `./mill -i unmanaged.run`
* `./mill -i unmanaged.runMain helloworld.HelloWorld`
* `./mill -i --watch unmanaged.run`
* `./mill -i unmanaged.runMain button.Main`
* `./mill -i --watch unmanaged.runMain button.Main`

## Setting up the Modules

The `build.sc` tries to automate as much of the JVM module parameters as possible. More concretely, it uses Mill's `runClasspath()` method to obtain the list of libraries (contains bot managed and unmanaged archives). It then filters these to obtain the JavaFX libraries. 

```scala
  override def forkArgs: Target[Seq[String]] = T {
    // get the unmanaged libraries
    val unmanaged: Loose.Agg[PathRef] = runClasspath() // unmanagedClasspath()
    // get the OpenJFX unmanaged libraries
    val s: Loose.Agg[String] = unmanaged.map(_.path.toString())
      .filter{
        s =>
          val t= s.toLowerCase()
          t.contains("javafx") || t.contains("controlsfx")
      }
```

However, it may be necessary to tweak those parameters according to your use case. More concretely you may need to add module exports or patches (a patch allows you to substitute a boot module with another drop-in replacement, for example using [TestFX/Monocle](https://github.com/TestFX/Monocle)). So take a look at the `override def forkArgs: Target[Seq[String]]` method and change that accordingly. 

## Using IntelliJ

To use this project in IntelliJ execute the following command:


 `./mill mill.scalalib.GenIdea/idea`

Note that Mill has support for [Bloop](https://scalacenter.github.io/bloop/). 
However, this needs to be set up in IntelliJ first.

## Using VSCode

In VSCode one need only open and import the Mill build script. Because Mill supports [Bloop](https://scalacenter.github.io/bloop/), this should automatically work for you provided you have installed the [Metals extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=scalameta.metals#overview). See also [metals-vscode in Github](https://github.com/scalameta/metals-vscode).
