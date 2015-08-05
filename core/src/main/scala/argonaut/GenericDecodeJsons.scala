package argonaut

import scalaz.Scalaz.ApplicativeIdV
import shapeless._, labelled.{ FieldType, field }

object GenericDecodeJsons {
  implicit def hnilDecodeJsonSucceeds: DecodeJson[HNil] =
    DecodeJson { c =>
      (HNil: HNil).point[DecodeResult]
    }

  implicit def stopAtFirstErrorHConsJsObjectDecodeJson[K <: Symbol, H, T <: HList](implicit
    key: Witness.Aux[K],
    headDecode: Lazy[DecodeJson[H]],
    tailDecode: Lazy[DecodeJson[T]]
  ): DecodeJson[FieldType[K, H] :: T] =
    DecodeJson { c =>
      for {
        head <- c.get(key.value.name)(headDecode.value)
        tail <- c.as(tailDecode.value)
      } yield field[K](head) :: tail
    }

  implicit def cnilDecodeJsonFails: DecodeJson[CNil] =
    DecodeJson(c => DecodeResult.fail("CNil", c.history))

  implicit def cconsAsJsObjectDecodeJson[K <: Symbol, H, T <: Coproduct](implicit
    key: Witness.Aux[K],
    headDecode: Lazy[DecodeJson[H]],
    tailDecode: Lazy[DecodeJson[T]]
  ): DecodeJson[FieldType[K, H] :+: T] =
    DecodeJson { c =>
      (c --\ key.value.name).focus match {
        case Some(headJson) => headJson.as(headDecode.value).map(h => Inl(field(h)))
        case None           => tailDecode.value.decode(c).map(Inr(_))
      }
    }

  implicit def defaultInstanceDecodeJson[F, G](implicit
    gen: LabelledGeneric.Aux[F, G],
    decode: Lazy[DecodeJson[G]]
  ): DecodeJson[F] =
    decode.value.map(gen.from)
}

trait DefaultGenericDecodeJsons {
  implicit val hnilDecodeJson: DecodeJson[HNil] =
    GenericDecodeJsons.hnilDecodeJsonSucceeds

  implicit def hconsDecodeJson[K <: Symbol, H, T <: HList](implicit
    key: Witness.Aux[K],
    headDecode: Lazy[DecodeJson[H]],
    tailDecode: Lazy[DecodeJson[T]]
  ): DecodeJson[FieldType[K, H] :: T] =
    GenericDecodeJsons.stopAtFirstErrorHConsJsObjectDecodeJson(key, headDecode, tailDecode)

  implicit val cnilDecodeJson: DecodeJson[CNil] =
    GenericDecodeJsons.cnilDecodeJsonFails

  implicit def cconsDecodeJson[K <: Symbol, H, T <: Coproduct](implicit
    key: Witness.Aux[K],
    headDecode: Lazy[DecodeJson[H]],
    tailDecode: Lazy[DecodeJson[T]]
  ): DecodeJson[FieldType[K, H] :+: T] =
    GenericDecodeJsons.cconsAsJsObjectDecodeJson(key, headDecode, tailDecode)

  implicit def instanceDecodeJson[F, G](implicit
    gen: LabelledGeneric.Aux[F, G],
    decode: Lazy[DecodeJson[G]]
  ): DecodeJson[F] =
    GenericDecodeJsons.defaultInstanceDecodeJson(gen, decode)
}
