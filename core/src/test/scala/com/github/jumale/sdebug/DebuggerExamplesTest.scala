package com.github.jumale.sdebug

import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.{ArraySeq, HashMap}
import scala.collection.{SortedMap, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class DebuggerExamplesTest extends AnyWordSpec {
  val debug: Debugger = new Debugger()

  final case class Root(
    string: String = "hello world!",
    trueBool: Boolean = true,
    falseBool: Boolean = false,
    nullVal: String = null,
    numbers: Numbers = Numbers(),
    coproducts: Coproducts = Coproducts(),
    emptyCollections: Collections = Collections(
      seq = Seq.empty,
      list = List.empty,
      vector = Vector.empty,
      set = Set.empty,
      map = Map.empty,
      sortedMap = SortedMap.empty,
      hashMap = HashMap.empty,
      mutableSeq = mutable.Seq.empty,
      mutableBuffer = mutable.Buffer.empty,
      mutableStack = mutable.Stack.empty,
      mutableSet = mutable.Set.empty,
      mutableMap = mutable.Map.empty,
      mutableSortedMap = mutable.SortedMap.empty,
      mutableHashMap = mutable.HashMap.empty
    ),
    collections: Collections = Collections(
      seq = Seq(1, 2, 3),
      list = (1 to 10).toList, // multi-line
      vector = Vector(1, 2, 3),
      set = Set(1, 2, 3),
      map = Map("a" -> 1, "b" -> 2, "c" -> 3, "d" -> 4, "e" -> 5, "f" -> 6, "g" -> 7), // multi-line
      sortedMap = SortedMap("a" -> 1, "b" -> 2),
      hashMap = HashMap("a" -> 1, "b" -> 2),
      mutableSeq = mutable.Seq(1, 2, 3),
      mutableBuffer = mutable.Buffer(1, 2, 3),
      mutableStack = mutable.Stack(1, 2, 3),
      mutableSet = mutable.Set(1, 2, 3),
      mutableMap = mutable.Map("a" -> 1, "b" -> 2),
      mutableSortedMap = mutable.SortedMap("a" -> 1, "b" -> 2),
      mutableHashMap = mutable.HashMap("a" -> 1, "b" -> 2)
    ),
    error: Throwable = new NullPointerException("exception message")
  )

  final case class Numbers(
    int: Int = 42,
    long: Long = 420,
    bigInt: BigInt = BigInt(4200),
    double: Double = 42.01d,
    float: Float = 42.02f
  )

  final case class Coproducts(
    some: Option[Int] = Some(42),
    none: Option[Int] = None,
    left: Either[String, Int] = Left("foo"),
    right: Either[String, Int] = Right(42),
    tuple: (Int, String, Boolean) = (42, "yes", true),
    success: Try[Int] = Success(42),
    failure: Try[Int] = Failure(new RuntimeException("error")),
    notCompletedFuture: Future[Int] = Future {
      Thread.sleep(2000)
      42
    },
    successFuture: Future[Int] = Future(42),
    failedFuture: Future[Int] = Future(throw new RuntimeException("error")),
    color: Seq[Color] = Seq(Color.Red, Color.Green),
    dataType: Seq[DataType] = Seq(DataType.NoFields, DataType.WithFields("foo", 42))
  )

  final case class Collections(
    seq: Seq[Int],
    list: List[Int],
    vector: Vector[Int],
    set: Set[Int],
    map: Map[String, Int],
    sortedMap: SortedMap[String, Int],
    hashMap: HashMap[String, Int],
    mutableSeq: mutable.Seq[Int],
    mutableBuffer: mutable.Buffer[Int],
    mutableStack: mutable.Stack[Int],
    mutableSet: mutable.Set[Int],
    mutableMap: mutable.Map[String, Int],
    mutableSortedMap: mutable.SortedMap[String, Int],
    mutableHashMap: mutable.HashMap[String, Int]
  )

  sealed abstract class Color(val name: String)
  object Color {
    case object Red extends Color("red")
    case object Green extends Color("green")
  }

  sealed trait DataType
  object DataType {
    case object NoFields extends DataType
    final case class WithFields(foo: String, bar: Int) extends DataType
  }

  val example: Root = Root()

  "simple log" in {
    debug.log("lorem ipsum")
  }

  "debug value" in {
    debug.print(example)
  }

  "debug values" in {
    debug.print(example.string, example.numbers)
    debug.print(example.string, example.numbers, example.trueBool)
  }

  "debug map" in {
    debug.print( //
      "string" -> example.string,
      "numbers" -> example.numbers,
      "trueBool" -> example.trueBool
    )
  }

  "debug root exception" in {
    debug.print(new NullPointerException("exception message"))
  }

  "debug root custom exception" in {
    final case class MyException(msg: String, level: Int) extends RuntimeException(msg)
    debug.print(MyException("exception message", 42))
  }

  "diff" in {
    sealed trait AbstractKey
    final case class KeyFoo(foo: String) extends AbstractKey
    final case class KeyBar(bar: Int) extends AbstractKey

    sealed trait AbstractParam
    final case class ParamFoo(foo: String) extends AbstractParam
    final case class ParamBar(bar: Int) extends AbstractParam

    final case class Foo(list: Seq[Bar])
    final case class Bar(map: Map[AbstractKey, Baz])
    final case class Baz(param: AbstractParam)

    debug.diff(
      Foo(
        List(
          Bar(
            Map( //
              KeyBar(42) -> Baz(ParamFoo("b")),
              KeyFoo("a") -> Baz(ParamBar(42))
            )
          ),
          Bar(
            Map( //
              KeyFoo("a") -> Baz(ParamFoo("b"))
            )
          )
        )
      ),
      Foo(
        Seq(
          Bar(
            Map( //
              KeyBar(42) -> Baz(ParamFoo("b")),
              KeyFoo("a") -> Baz(ParamFoo("b")),
              KeyFoo("c") -> Baz(ParamFoo("b"))
            )
          )
        )
      )
    )

    debug.diff( //
      List(Bar(Map(KeyFoo("a") -> Baz(ParamFoo("b"))))),
      new ArraySeq.ofRef(List(Bar(Map(KeyFoo("a") -> Baz(ParamFoo("c"))))).toArray)
    )
    debug.diff( //
      Some(List(Bar(Map(KeyFoo("a") -> Baz(ParamFoo("b")))))),
      Some(new ArraySeq.ofRef(List(Bar(Map(KeyFoo("a") -> Baz(ParamFoo("b"))))).toArray))
    )
    debug.diff( //
      List(Bar(Map(KeyFoo("a") -> Baz(ParamFoo("b"))))),
      Vector(Bar(Map(KeyFoo("a") -> Baz(ParamFoo("c")))))
    )
    debug.diff( //
      Set(Baz(ParamFoo("foo")), Foo(Seq.empty), Baz(ParamFoo("bar"))),
      Set(Baz(ParamFoo("foo")), Baz(ParamFoo("bar")), Bar(Map.empty))
    )
  }

  "trace" in {
    def foo(): Unit = bar()
    def bar(): Unit = baz()
    def baz(): Unit = debug.trace(limit = 10)
    foo()
  }

  "sleep" in {
    debug.sleep(200)
  }

  "named sleep" in {
    debug.sleep("foo", 200)
  }

  final case class CellVal(a: String, b: Int)
  "table" in {
    debug.table("", "H1", "H2", "H3 the longest by header")(
      Seq(
        "foo",
        "V1",
        """V2
          |new line
          |yes""".stripMargin,
        "V3"
      ),
      Seq(
        "-bar-",
        List(CellVal("a", 1), CellVal("b", 2), CellVal("c", 3), CellVal("d", 4), CellVal("e", 5)),
        "V2",
        (42, Left(None)),
        "V4"
      ),
      Seq("--baz--", "V1", false)
    )
  }

  "table simple" in {
    debug.table("Name", "Value")(
      Seq("string", "foo"),
      Seq("tuple", (42, Left(None))),
      Seq("bool", false),
      Seq("list of case classes", List(CellVal("a", 1), CellVal("b", 2), CellVal("c", 3), CellVal("d", 4)))
    )
  }

  "measure" in {
    debug.measure("myCode") {
      Thread.sleep(100)
    }
  }
}
