package at.asitplus.signum.supreme.sign

import at.asitplus.signum.indispensable.*
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.pki.*
import at.asitplus.signum.supreme.sign
import at.asitplus.signum.supreme.signature
import at.asitplus.signum.supreme.succeed
import com.ionspin.kotlin.bignum.integer.Quadruple
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

class EphemeralSignerCommonTests : FreeSpec({
    "Functional" - {
        "RSA" - {
            withData(
                nameFn = { (pad, dig, bits, pre) -> "$dig/$pad/${bits}bit${if (pre) "/pre" else ""}" },
                sequence {
                    RSAPadding.entries.forEach { padding ->
                        Digest.entries.forEach { digest ->
                            when {
                                digest == Digest.SHA512 && padding == RSAPadding.PSS
                                    -> listOf(2048, 3072, 4096)
                                digest == Digest.SHA384 || digest == Digest.SHA512 || padding == RSAPadding.PSS
                                    -> listOf(1024,2048,3072,4096)
                                else
                                    -> listOf(512, 1024, 2048, 3072, 4096)
                            }.forEach { keySize ->
                                yield(Quadruple(padding, digest, keySize, false))
                                yield(Quadruple(padding, digest, keySize, true))
                            }
                        }
                    }
                }) { (padding, digest, keySize, preHashed) ->
                val data = Random.Default.nextBytes(64)
                val signer: Signer
                val signature = try {
                    signer = Signer.Ephemeral {
                        rsa {
                            digests = setOf(digest); paddings = setOf(padding); bits = keySize
                        }
                    }.getOrThrow()
                    signer.sign(SignatureInput(data).let {
                        if (preHashed) it.convertTo(digest).getOrThrow() else it
                    }).signature
                } catch (x: UnsupportedOperationException) {
                    return@withData
                }
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.RSA>().let {
                    it.digest shouldBe digest
                    it.padding shouldBe padding
                }

                val verifier = signer.makeVerifier().getOrThrow()
                verifier.verify(data, signature) should succeed
            }
        }
        "ECDSA" - {
            withData(
                nameFn = { (crv, dig, pre) -> "$crv/$dig${if (pre) "/pre" else ""}" },
                sequence {
                    ECCurve.entries.forEach { curve ->
                        Digest.entries.forEach { digest ->
                            yield(Triple(curve, digest, false))
                            yield(Triple(curve, digest, true))
                        }
                    }
                }) { (crv, digest, preHashed) ->
                val signer =
                    Signer.Ephemeral { ec { curve = crv; digests = setOf(digest) } }.getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().let {
                    it.digest shouldBe digest
                    it.requiredCurve shouldBeIn setOf(null, crv)
                }
                val data = Random.Default.nextBytes(64)
                val signature = signer.sign(SignatureInput(data).let {
                    if (preHashed) it.convertTo(digest).getOrThrow() else it
                }).signature

                val verifier = signer.makeVerifier().getOrThrow()
                verifier.verify(data, signature) should succeed
            }
        }
    }
    "Configuration" - {
        "ECDSA" - {
            "No digest specified (defaults to native)" {
                val curve = Random.of(ECCurve.entries)
                val key = EphemeralKey { ec { this.curve = curve } }.getOrThrow()
                val signer = key.signer().getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().digest shouldBe curve.nativeDigest
            }
            "No digest specified, native disallowed, still succeeds" {
                val curve = Random.of(ECCurve.entries)
                val key = EphemeralKey { ec { this.curve = curve; digests = Digest.entries.filter { it != curve.nativeDigest }.toSet() } }.getOrThrow()
                val signer = key.signer().getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().digest shouldNotBeIn setOf(curve.nativeDigest, null)
            }
            "All digests legal by default" {
                val curve = Random.of(ECCurve.entries)
                val key = EphemeralKey { ec { this.curve = curve } }.getOrThrow()
                val nonNativeDigest = Random.of(Digest.entries.filter {it != curve.nativeDigest})
                val signer = key.signer { ec { digest = nonNativeDigest } }.getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().digest shouldBe nonNativeDigest
            }
            "Illegal digests should fail" {
                val curve = Random.of(ECCurve.entries)
                val key = EphemeralKey { ec { this.curve = curve; digests = Digest.entries.filter {it != curve.nativeDigest}.toSet() } }.getOrThrow()
                key.signer{ ec { digest = curve.nativeDigest } } shouldNot succeed
            }
            "Null digest should work as a default" {
                val key = EphemeralKey { ec { this.curve = Random.of(ECCurve.entries); digests = setOf<Digest?>(null) } }.getOrThrow()
                val signer = key.signer().getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().digest shouldBe null
            }
            "Null digest should work if explicitly specified" {
                val key = EphemeralKey { ec {} }.getOrThrow()
                val signer = key.signer { ec { digest = null } }.getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().digest shouldBe null
            }
        }
        "RSA" - {
            "No digest specified" {
                val key = EphemeralKey { rsa {} }.getOrThrow()
                val signer = key.signer().getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.RSA>()
            }
        }
    }

    "Cert signing" - {
        "RSA" - {
            withData(
                nameFn = { (pad, dig, bits, pre) -> "$dig/$pad/${bits}bit${if (pre) "/pre" else ""}" },
                sequence {
                    RSAPadding.entries.forEach { padding ->
                        Digest.entries.forEach { digest ->
                            when {
                                digest == Digest.SHA512 && padding == RSAPadding.PSS
                                    -> listOf(2048, 3072, 4096)

                                digest == Digest.SHA384 || digest == Digest.SHA512 || padding == RSAPadding.PSS
                                    -> listOf(1024, 2048, 3072, 4096)

                                else
                                    -> listOf(512, 1024, 2048, 3072, 4096)
                            }.forEach { keySize ->
                                yield(Quadruple(padding, digest, keySize, false))
                                yield(Quadruple(padding, digest, keySize, true))
                            }
                        }
                    }
                }) { (padding, digest, keySize, preHashed) ->
                val data = Random.Default.nextBytes(64)
                val signer: Signer


                try {
                    signer = Signer.Ephemeral {
                        rsa {
                            digests = setOf(digest); paddings = setOf(padding); bits = keySize
                        }
                    }.getOrThrow()
                    signer.sign(SignatureInput(data).let {
                        if (preHashed) it.convertTo(digest).getOrThrow() else it
                    }).signature
                } catch (x: UnsupportedOperationException) {
                    return@withData
                }

                val csr = TbsCertificationRequest(
                    subjectName = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("client")))),
                    publicKey = signer.publicKey,
                    attributes = listOf(
                        Pkcs10CertificationRequestAttribute(
                            // No OID is assigned for this; choose one!
                            KnownOIDs.id_sMIME,
                            // ↓↓↓ contains challenge ↓↓↓
                            Asn1String.UTF8("foo").encodeToTlv()
                        )
                    )
                )
                if(digest == Digest.SHA1 && padding== RSAPadding.PSS) return@withData
                val signedCSR = signer.sign(csr).getOrThrow()


                val verifier = signer.makeVerifier().getOrThrow()
                verifier.verify(signedCSR.tbsCsr.encodeToDer(), signedCSR.signature) should succeed


                val tbsCrt = TbsCertificate(
                    serialNumber = Random.nextBytes(16),
                    signatureAlgorithm = signer.signatureAlgorithm.toX509SignatureAlgorithm().getOrThrow(),
                    issuerName = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("Foo")))),
                    validFrom = Asn1Time(
                        Clock.System.now()
                    ),
                    validUntil = Asn1Time(Clock.System.now() + 356.days),
                    subjectName = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("client")))),
                    publicKey = signer.publicKey,
                    extensions = listOf(
                        X509CertificateExtension(
                            KnownOIDs.pkcs_12_OID,
                            critical = true,
                            Asn1PrimitiveOctetString(byteArrayOf())
                        )
                    )
                )
                val cert = signer.sign(tbsCrt).getOrThrow()

                verifier.verify(cert.tbsCertificate.encodeToDer(), cert.signature) should succeed

            }
        }

        "ECDSA" - {
            withData(
                nameFn = { (crv, dig, pre) -> "$crv/$dig${if (pre) "/pre" else ""}" },
                sequence {
                    ECCurve.entries.forEach { curve ->
                        Digest.entries.filterNot { it == Digest.SHA1 }.forEach { digest ->
                            yield(Triple(curve, digest, false))
                            yield(Triple(curve, digest, true))
                        }
                    }
                }) { (crv, digest, preHashed) ->
                val signer =
                    Signer.Ephemeral { ec { curve = crv; digests = setOf(digest) } }.getOrThrow()
                signer.signatureAlgorithm.shouldBeInstanceOf<SignatureAlgorithm.ECDSA>().let {
                    it.digest shouldBe digest
                    it.requiredCurve shouldBeIn setOf(null, crv)
                }
                val csr = TbsCertificationRequest(
                    subjectName = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("client")))),
                    publicKey = signer.publicKey,
                    attributes = listOf(
                        Pkcs10CertificationRequestAttribute(
                            // No OID is assigned for this; choose one!
                            KnownOIDs.id_sMIME,
                            // ↓↓↓ contains challenge ↓↓↓
                            Asn1String.UTF8("foo").encodeToTlv()
                        )
                    )
                )
                val signedCSR = signer.sign(csr).getOrThrow()


                val verifier = signer.makeVerifier().getOrThrow()
                verifier.verify(signedCSR.tbsCsr.encodeToDer(), signedCSR.signature) should succeed


                val tbsCrt = TbsCertificate(
                    serialNumber = Random.nextBytes(16),
                    signatureAlgorithm = signer.signatureAlgorithm.toX509SignatureAlgorithm().getOrThrow(),
                    issuerName = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("Foo")))),
                    validFrom = Asn1Time(
                        Clock.System.now()
                    ),
                    validUntil = Asn1Time(Clock.System.now() + 356.days),
                    subjectName = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("client")))),
                    publicKey = signer.publicKey,
                    extensions = listOf(
                        X509CertificateExtension(
                            KnownOIDs.pkcs_12_OID,
                            critical = true,
                            Asn1PrimitiveOctetString(byteArrayOf())
                        )
                    )
                )
                val cert = signer.sign(tbsCrt).getOrThrow()

                verifier.verify(cert.tbsCertificate.encodeToDer(), cert.signature) should succeed
            }
        }
    }

})
