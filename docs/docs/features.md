---
hide:
  - navigation
---

# Signum Feature Matrix

This page contains feature matrices, providing a detailed summary of what is and isn't supported.

## Operations

The following table provides an overview about the current status of supported and unsupported cryptographic functionality.
More details about the supported algorithms is provided in the next section.

| Operation                   |          JVM          | Android |       iOS       |
|:----------------------------|:---------------------:|:-------:|:---------------:|
| ASN.1 Encoding + Decoding   |           ✔           |    ✔    |        ✔        |
| Signature Creation          |           ✔           |    ✔    |        ✔        |
| Signature Verification      |           ✔           |    ✔    |        ✔        |
| Digest Calculation          |           ✔           |    ✔    |        ✔        |
| Attestation                 |           ❋           |    ✔    |       ✔*        |
| Biometric Auth              |           ✗           |    ✔    |        ✔        |
| Hardware-Backed Key Storage | through dedicated HSM |    ✔    | P-256 keys only |
| Key Agreement               |           ✗           |    ✗    |        ✗        |
| Asymmetric Encryption       |           ✗           |    ✗    |        ✗        |
| Symmetric Encryption        |           ✗           |    ✗    |        ✗        |
| MAC                         |           ✗           |    ✗    |        ✗        |

Hardware-backed key agreement, asymmetric and symmetric encryption are WIP and will be supported in an upcoming release.
This is more than a mere lip service, since we (A-SIT Plus GmbH) need this functionality urgently ourselves and are already working on it.

### ❋ JVM Attestation
The JVM supports a custom attestation format, which can convey attestation
information inside an X.509 certificate.
By default, no semantics are attached to it. It can, therefore be used in any way desired, although this is
highly context-specific.
For example, if a hardware security module is plugged into the JVM crypto provider (e.g. using PKCS11) and this HSM
supports attestation, the JVM-specific attestation format can carry this information. WIP!
If you have suggestions, experience or a concrete use-case where you need this, check the footer and let us know!

### ✔* iOS Attestation
iOS supports App attestation, but no direct key attestation. The Supreme crypto provider emulates key attestation
through app attestation, by _asserting_ the creation of a fresh public/private key pair inside the secure enclave
through application-layer logic encapsulated by the Supreme crypto provider.  
Additional details are described in the [Attestation](supreme.md#attestation) section of the _Supreme_ manual.

## Supported Algorithms

The following matrix lists all supported algorithms and details.
Since everything is supported on all platforms equally,
a separate platform listing is omitted.

| Primitive          | Details                                                                              |
|--------------------|--------------------------------------------------------------------------------------|
| Signature Creation | RSA/ECDSA with SHA2-family hash functions + raw signatures on pre-hashed data        |
| RSA Key Sizes      | 512 (useful for faster tests) up to 4096 (larger keys may not work on all platforms) |
| RSA Padding        | PKCS1 and PSS (with sensible defaults)                                               |
| Elliptic Curves    | NIST Curves (P-256, P-384, P-521)                                                    |
| Digests            | SHA-1 and SHA-2 family (SHA-256, SHA-384, SHA-512)                                   |

On the JVM and on Android, supporting more algorithms is rather easy, since Bouncy Castle works on both platforms
and can be used to provide more algorithms than natively supported. However, we aim for tight platform integration,
especially wrt. hardware-backed key storage and in-hardware computation of cryptographic operations.
We have therefore limited ourselves to what is natively supported on all platforms and most relevant in practice.

## High-Level ASN.1 Abstractions

The _Indispensable_ module comes with a fully-features ASN.1 engine including a builder DSL.
In addition to low-level, generic abstractions, it also provides higher-level datatypes with enriched
semantics:

| Abstraction                  | Remarks                                                                                                                                                                              |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| X.509 Certificate            | Only supported algorithms can be parsed as certificate.<br> Certificates containing other algorithm can be parsed as generic ASN.1 structure. Parser is too lenient in some aspects. |
| X.509 Certificate Extension  | Almost no predefined extensions. Need to be manually created.                                                                                                                        |
| Relative Distinguished Names | Rather barebones with little to no validation.                                                                                                                                       |
| Alternative Names            | Only basic structural validation.                                                                                                                                                    |
| PKCS10 CSR                   | Almost certainly a bit too lenient.                                                                                                                                                  |
| PKCS10 CSR Attributes        | No predefined attributes. Need to be manually created.                                                                                                                               |
| X.509 Signature Algorithm    | Only supported algorithms.                                                                                                                                                           |
| Public Keys                  | Only supported types.                                                                                                                                                                |
| ASN.1 Integer                | Supports `Int`, `UInt`, `Long`, `ULong` and `BigInteger`.                                                                                                                            |
| ASN.1 Time                   | Maps from/to kotlinx-datetime `Instant`. Automatic choice of `GENERALIZED` and  `UTC` time                                                                                           |
| ASN.1 String                 | All types supported, with little to no validation, however.                                                                                                                          |
| ASN.1 Object Identifier      | Only `1` and `2` subtrees supported. `KnownOIDs` is generated from _dumpasn1_.                                                                                                       |
| ASN.1 Octet String           | Primitive octet strings and encapsulating complex structures natively supported for encoding and parsing.                                                                            |
| ASN.1 Bit String             | Relies on custom `BitSet` implementation, but also supports encoding raw bytes.                                                                                                      |