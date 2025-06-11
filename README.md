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

## Debugging helpers
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
Print a simple log message:
```scala
sdebug.log(s"lorem ipsum")
```
![log](./doc/screenshot/log.png)

---
Pretty-print variables:
```scala
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
The printed result is a valid Scala code, so it can be copy-pasted back to IDE if needed:<br>
![log](./doc/screenshot/dump.png)

---
Exceptions are printed with a stack-trace:
```scala
sdebug.print(exception)
```
![log](./doc/screenshot/dumpException.png)<br>
The stack-trace length is limited to 10 by default, but it can be changed via setter `sdebug.setErrorTraceLimit(20)`.

---
Print diffs between two values:
```scala
sdebug.diff(left, right)
```
![log](./doc/screenshot/diff.png)<br>
The diff is calculated recursively, and it shows precisely which key or value has been changed/added/deleted.
The `sdebug-impl-ext` also resolves diffs for any `JsValue` from Play JSON library.

---
Print a stack-trace to the current line:
```scala
sdebug.trace(limit = 10)
```
![log](./doc/screenshot/trace.png)

---
A wrapper for `Thread.sleep`, which also prints breadcrumbs, so that it can't be accidentally forgotten in code:
```scala
sdebug.sleep(200)
```
![log](./doc/screenshot/sleep.png)

---
Print results in a table format:
```scala
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
Measure execution time of the provided code-block:
```scala
sdebug.measure(myFunction())

// also supports custom name:
sdebug.measure("customName")(myFunction())
```
![log](./doc/screenshot/sleep.png)

## Additional helpers

Format a value, but instead of printing save it into a file:
```scala
// 
sdebug.formatAndSave("filename.txt")(value)

// also supports multiple values:
sdebug.formatAndSave("filename.txt")(foo, bar, baz)
```
The default file location is `./target` (i.e. the file from the example will be saved to `./target/filename.txt`),
and it's only applicable to relative paths/file-names, while  absolute paths will be handled as is.
The default folder also can be changed when creating a new instance of Debugger (not supported in `sdebug-impl-ext`).

---
Simply read/write file contents:
```scala
sdebug.save("filename.txt")("contents")
val contents = sdebug.read("filename.txt")
```

---
Read JSON files (only in `sdebug-impl-ext`):
```scala
val data = sdebug.readJson[MyData]("filename.json")
```

---
The debug-printer can be toggled off until the next toggle-on.
This is useful when you have multiple calls of the same debugged code, but you want to see the output of one specific call:
```scala
sdebug.off()
// some code
sdebug.on()
```

---
Enable timestamps in prints
```scala
sdebug.setShowTime(true)
```

## Configuration in runtime

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
