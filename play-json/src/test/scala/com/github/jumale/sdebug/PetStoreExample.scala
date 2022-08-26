package com.github.jumale.sdebug

import com.github.jumale.sdebug.playjson.DebugJson
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, JsValue, Json}

class PetStoreExample extends AnyWordSpec {
  val debug: Debugger = DebugJson()

  val swagger: Swagger = Swagger(
    Map(
      Path("/pet/{petId}/uploadImage") -> Map(
        Method.Post -> PathDef(
          tags = Seq("pet"),
          summary = Some("uploads an image"),
          parameters = Vector(
            Parameter(
              name = "petId",
              in = Left(Parameter.Path),
              description = Some("Id of pet to update"),
              required = true
            ),
            Parameter(name = "additionalMetadata", in = Right(Parameter.FormData), description = None, required = false)
          ),
          responses = Map(
            Code(200) -> Response(
              description = Some("successful operation"),
              schema = Json.obj(
                "type" -> "object",
                "properties" -> Json.arr(
                  Json.obj(
                    "code" -> Json.obj("type" -> "integer", "example" -> 42),
                    "required" -> true,
                    "tags" -> Json.arr("foo", "bar"),
                    "message" -> Json.obj("type" -> "string"),
                    "xml" -> JsNull
                  )
                )
              )
            )
          )
        )
      )
    )
  )

  "print" in {
    debug(swagger)
  }

  "diff" in {
    val expected = swagger
    val actual = swagger.copy(paths =
      swagger.paths.view
        .mapValues(
          _.view
            .mapValues(path =>
              path.copy(
                parameters = path.parameters :+ Parameter("token", Left(Parameter.Path), None, required = false),
                responses = path.responses.map { case (k, r) =>
                  k -> r.copy(
                    description = None,
                    schema = r.schema
                      .as[JsObject]
                      .deepMerge(
                        Json.obj( //
                          "properties" -> (r.schema \ "properties").as[JsArray].value.map { p =>
                            (p.as[JsObject] - "message")
                          }
                        )
                      )
                  )
                }
              )
            )
            .toMap
        )
        .toMap
    )

    debug.diff(expected, actual)
  }
}

final case class Swagger(paths: Map[Path, Map[Method, PathDef]])

final case class Path(value: String) extends AnyVal

trait Method
object Method {
  case object Get extends Method {
    override def toString: String = "get"
  }
  case object Post extends Method {
    override def toString: String = "post"
  }
  case object Put extends Method {
    override def toString: String = "put"
  }
}

final case class PathDef(
  tags: Seq[String] = Seq.empty,
  summary: Option[String] = None,
  parameters: Vector[Parameter] = Vector.empty,
  responses: Map[Code, Response] = Map.empty
)

final case class Code(value: Int) extends AnyVal

final case class Parameter(
  name: String,
  in: Either[Parameter.Path.type, Parameter.FormData.type],
  description: Option[String],
  required: Boolean
)

object Parameter {
  case object Path
  case object FormData
}

final case class Response(description: Option[String], schema: JsValue)
