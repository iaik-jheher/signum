package at.asitplus.crypto.datatypes

import at.asitplus.crypto.datatypes.asn1.*
import at.asitplus.crypto.datatypes.asn1.BERTags.INTEGER
import at.asitplus.crypto.datatypes.io.Base64UrlStrict
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * Data class which holds Asn1 Encoding of a signature of a specified algorithm
 * Allows simple ASN1 - DER - Raw transformation of signature values
 * Does not check for anything!
 */

@Serializable
sealed class CryptoSignature(
    @Contextual
    protected val signature: Asn1Element,
) : Asn1Encodable<Asn1Element> {

    abstract fun serialize(): String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CryptoSignature

        return signature == other.signature
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }

    override fun encodeToTlv(): Asn1Element = signature

//    override fun encodeToDer(): ByteArray {
//        return signature.derEncoded
//    }

    /**
     * Removes ASN1 Structure and returns the value(s) as ByteArray
     */
    abstract val rawByteArray: ByteArray

    /**
     * Input is expected to be x,y coordinates concatenated to bytearray
     */
    @Serializable(with = EC.CryptoSignatureSerializer::class)
    class EC(input: ByteArray) : CryptoSignature(
        asn1Sequence {
            append(
                Asn1Primitive(
                    INTEGER,
                    input.sliceArray(0 until (input.size / 2))
                )
            )
            append(
                Asn1Primitive(
                    INTEGER,
                    input.sliceArray((input.size / 2) until input.size)
                )
            )
        }
    ) {
        override fun serialize(): String =
            rawByteArray.encodeToString(Base64UrlStrict)

        override val rawByteArray by lazy {
            val coordSizes = listOf(
                32 - 1,
                48 - 1,
                66 - 1
            ) // 256, 384, 521 -- note that 521 gets rounded up to 528 -- Minus 1 since arrays start at 0
            val coordSize =
                coordSizes.filter { it <= ((signature as Asn1Sequence).children[0] as Asn1Primitive).content.size }
                    .minOrNull() ?: throw Exception("Illegal signature length")
            byteArrayOf(
                *((signature as Asn1Sequence).children[0] as Asn1Primitive).decode(INTEGER) { it }.padWithZeros(coordSize),
                *(signature.children[1] as Asn1Primitive).decode(INTEGER) { it }.padWithZeros(coordSize)
            )
        }

        object CryptoSignatureSerializer : KSerializer<EC> {
            override val descriptor: SerialDescriptor
                get() = PrimitiveSerialDescriptor("CryptoSignature", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): EC =
                EC(decoder.decodeString().encodeToByteArray())

            override fun serialize(encoder: Encoder, value: EC) {
                encoder.encodeString(value.serialize())
            }
        }
    }

    @Serializable(with = RSAorHMAC.CryptoSignatureSerializer::class)
    class RSAorHMAC(input: ByteArray) : CryptoSignature(
        Asn1Primitive(INTEGER, input)
    ) {
        override fun serialize(): String =
            rawByteArray.encodeToString(Base64UrlStrict)

        override val rawByteArray by lazy { (signature as Asn1Primitive).decode(INTEGER) { it } }

        object CryptoSignatureSerializer : KSerializer<RSAorHMAC> {
            override val descriptor: SerialDescriptor
                get() = PrimitiveSerialDescriptor("CryptoSignature", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): RSAorHMAC =
                RSAorHMAC(decoder.decodeString().encodeToByteArray())

            override fun serialize(encoder: Encoder, value: RSAorHMAC) {
                encoder.encodeString(value.serialize())
            }
        }
    }

    companion object : Asn1Decodable<Asn1Element, CryptoSignature> {
        @Throws(Asn1Exception::class)
        override fun decodeFromTlv(src: Asn1Element): CryptoSignature =
            runRethrowing {
                when (src.tag) {
                    INTEGER -> RSAorHMAC((src as Asn1Primitive).decode(INTEGER) { it })
                    DERTags.DER_SEQUENCE -> {
                        val first = ((src as Asn1Sequence).nextChild() as Asn1Primitive).decode(INTEGER) { it.dropWhile { it == 0.toByte() } }.toByteArray()
                        val second = (src.nextChild() as Asn1Primitive).decode(INTEGER) { it.dropWhile { it == 0.toByte() } }.toByteArray()
                        if (src.hasMoreChildren()) throw IllegalArgumentException("Illegal Signature Format")
                        EC(first + second)
                    }

                    else -> throw IllegalArgumentException("Unknown Signature Format")
                }
            }
    }
}