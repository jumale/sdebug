# Scala Debugging Utils

This library provides basic tools for debugging Scala code in console: pretty-printing variables, diffs, and some more.<br>
It is my personal util-library which I use for debugging in my daily work (mostly in tests). It's all started with 
a single function `prettyFormat` ([inspired by this gist](https://gist.github.com/carymrobbins/7b8ed52cd6ea186dbdf8)) 
and eventually grew into my personal tool-kit.

I prefer installing this library via `global.sbt` - this allows me to use it in any project, and also ensures that I do
not forget to remove prints from my code (because forgotten prints will cause a compilation error in CI which does not know about this library).

## Installation

- clone this project locally
- go to the root folder and run `sbt +publishLocal` or `make build`
- create if not exists `~/.sbt/1.0/global.sbt` (or `~/.sbt/0.13/global.sbt` for older SBT)
- install one of the versions:
  - an extended version with PlayJson and Scalatest support:
    ```scala
    libraryDependencies ++= Seq(
      "com.github.jumale" %% "sdebug-impl-ext"  % "0.4.0-SNAPSHOT"
    )
    ```
  - or a pure version without any dependencies:
    ```scala
    libraryDependencies ++= Seq(
      "com.github.jumale" %% "sdebug-impl"  % "0.4.0-SNAPSHOT"
    )
    ```
- reload your SBT console

> Note: the extended version includes an extended printer for JsValue from PlayJson and provides an implementation of 
> `org.scalactic.Prettifier` which can be optionally imported in Scalatest test-classes to make test-failures more readable.

## Functions
You can also find some examples in the [DebuggerExamplesTest.scala](./core/src/test/scala/com/github/jumale/sdebug/DebuggerExamplesTest.scala).

---
Importing a scalatest formatter (available only in `sdebug-impl-ext`):
```scala
class MyTest extends AnyWordSpec {
  import sdebug.scalacticPrettifierAnalyse
  // now failed tests will automatically print errors in the sdebug format
}
```
Or an alternative version which does not print analysis:
```scala
class MyTest extends AnyWordSpec {
  import sdebug.scalacticPrettifier
}
```
The choice between the two versions depends on whether you run tests in Intellij IDEA or in native terminal.

---
The debug-printer can be toggled off until the next toggle-on. 
This is useful when you have multiple calls of the same debugged code, but you want to see the output of one specific call:
```scala
sdebug.off()
// some code
sdebug.on()
```

---
```scala
// just a simple message log
sdebug.log(s"lorem ipsum")
```
![log](./doc/screenshot/log.png)

---
```scala
// pretty-print any variable
// the printed result is a valid Scala code, so it can be copy-pasted back to IDE if needed
sdebug.print(swagger)

// also can print multiple values: 
sdebug.print(foo, bar, baz)

// also can print multiple named values: 
sdebug.print(
  "foo" -> foo, 
  "bar" -> bar, 
  "baz" -> baz,
)
```
![log](./doc/screenshot/dump.png)

---
```scala
// exceptions are printed with stack-trace
// the stack-trace length is limited to 10 by default, but it can be changed via setter 'sdebug.setErrorTraceLimit(20)'
sdebug.print(exception)
```
![log](./doc/screenshot/dumpException.png)

---
```scala
// print diffs between two values
// the diff is calculated recursively, and shows precisely which key or value has been changed/added/deleted
sdebug.diff(left, right)
```
![log](./doc/screenshot/diff.png)

---
```scala
// just prints a stack-trace to the current line 
sdebug.trace(limit = 10)
```
![log](./doc/screenshot/trace.png)

---
```scala
// a wrapper for Thread.sleep, which also prints breadcrumbs, so that it can't be accidentally forgotten in code 
sdebug.sleep(200)
```
![log](./doc/screenshot/sleep.png)

---
```scala
// printing results in table format 
sdebug.table(
  Seq("Name", "Value"),
  Seq("string", "foo"),
  Seq("tuple", (42, Left(None))),
  Seq("bool", false),
  Seq("list of case classes", List(CellVal("a", 1), CellVal("b", 2), CellVal("c", 3), CellVal("d", 4)))
)
```
![log](./doc/screenshot/table.png)

---
```scala
// measures execution time of the provided code-block 
// also supports custom name: `sdebug.measure("customName")(myFunction())`
sdebug.measure(myFunction())
```
![log](./doc/screenshot/sleep.png)

---
```scala
// format a value, but instead of printing save it to a file
sdebug.formatAndSave("filename.txt")(value)
// also supports multiple values :`sdebug.formatAndSave("filename.txt")(foo, bar, baz)`
// the default file location is `./target` (i.e. the file from the example will be saved to `./target/filename.txt`)
// the location can be changed via settings when manually creating a new instance of Debugger
```

---
Enable timestamps in prints
```scala
sdebug.setShowTime(true)
```

---
Disable colors
```scala
sdebug.setColorize(false)
```

---
Configure how class names are printed:
```scala
sdebug.configureClassNames(
  showFull = true, // true results in 'class.getName', false results in 'class.getSimpleName'
  replace = Seq(
    // regex-match and replace substings in printed class names
    "^\\w+".r -> "customPrefix"
  )
)
```

---
Fully replace printed types with aliases
```scala
sdebug.aliases(
  "ClassA" -> "ClassB",
  "ClassC" -> "ClassD",
)
```
