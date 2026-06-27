package com.example

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.util.Base64

object ApkParser {

    // --- BINARY XML DECODER ---
    // A robust binary XML decoder for Android (e.g. AndroidManifest.xml inside APK)
    fun decompileBinaryXml(bytes: ByteArray): String {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.short.toInt() and 0xFFFF
            val headerSize = buffer.short.toInt() and 0xFFFF
            val fileSize = buffer.int

            if (magic != 0x0003 || headerSize != 0x0008) {
                // Try reading with offset if structure is slightly different
                return "Error: Invalid Binary XML Magic Number (expected 0x00080003, got 0x${Integer.toHexString(magic)})"
            }

            var strings = emptyList<String>()
            val sb = StringBuilder()
            var indent = 0

            fun getIndent(): String = "  ".repeat(indent)

            while (buffer.hasRemaining()) {
                val chunkType = buffer.int
                val chunkSize = buffer.int

                when (chunkType) {
                    0x001C0001 -> { // String Pool
                        val stringCount = buffer.int
                        val styleCount = buffer.int
                        val flags = buffer.int
                        val stringStart = buffer.int
                        val styleStart = buffer.int

                        val stringOffsets = IntArray(stringCount)
                        for (i in 0 until stringCount) {
                            stringOffsets[i] = buffer.int
                        }

                        if (styleCount > 0) {
                            for (i in 0 until styleCount) {
                                buffer.int // skip style offset
                            }
                        }

                        val stringPoolBytes = ByteArray(chunkSize - (buffer.position() - (buffer.position() - chunkSize)))
                        val poolStartPos = buffer.position() - 20 - stringCount * 4 - styleCount * 4
                        
                        val isUtf8 = (flags and (1 shl 8)) != 0
                        val stringList = mutableListOf<String>()

                        for (i in 0 until stringCount) {
                            val offset = stringOffsets[i]
                            val absolutePos = poolStartPos + stringStart + offset
                            buffer.position(absolutePos)

                            if (isUtf8) {
                                // UTF-8 format
                                val len1 = buffer.get().toInt() and 0xFF
                                val len = if ((len1 and 0x80) != 0) {
                                    val len2 = buffer.get().toInt() and 0xFF
                                    ((len1 and 0x7F) shl 8) or len2
                                } else {
                                    len1
                                }
                                val uLen1 = buffer.get().toInt() and 0xFF
                                val uLen = if ((uLen1 and 0x80) != 0) {
                                    val uLen2 = buffer.get().toInt() and 0xFF
                                    ((uLen1 and 0x7F) shl 8) or uLen2
                                } else {
                                    uLen1
                                }
                                val strBytes = ByteArray(uLen)
                                buffer.get(strBytes)
                                stringList.add(String(strBytes, Charsets.UTF_8))
                            } else {
                                // UTF-16 format
                                val len1 = buffer.short.toInt() and 0xFFFF
                                val len = if ((len1 and 0x8000) != 0) {
                                    val len2 = buffer.short.toInt() and 0xFFFF
                                    ((len1 and 0x7FFF) shl 16) or len2
                                } else {
                                    len1
                                }
                                val chars = CharArray(len)
                                for (c in 0 until len) {
                                    chars[c] = buffer.char
                                }
                                stringList.add(String(chars))
                            }
                        }
                        strings = stringList
                        // Seek to the end of the string pool chunk
                        buffer.position(poolStartPos + chunkSize)
                    }

                    0x00080180 -> { // Start Namespace
                        val lineNumber = buffer.int
                        val commentIndex = buffer.int
                        val prefixIndex = buffer.int
                        val uriIndex = buffer.int
                        // Just keep going
                    }

                    0x00080181 -> { // End Namespace
                        val lineNumber = buffer.int
                        val commentIndex = buffer.int
                        val prefixIndex = buffer.int
                        val uriIndex = buffer.int
                    }

                    0x00080102 -> { // Start Element Tag
                        val lineNumber = buffer.int
                        val commentIndex = buffer.int
                        val namespaceUriIndex = buffer.int
                        val nameIndex = buffer.int
                        val attributeStart = buffer.short.toInt() and 0xFFFF
                        val attributeSize = buffer.short.toInt() and 0xFFFF
                        val attributeCount = buffer.short.toInt() and 0xFFFF
                        val idAttributeIndex = buffer.short.toInt() and 0xFFFF
                        val classAttributeIndex = buffer.short.toInt() and 0xFFFF
                        val styleAttributeIndex = buffer.short.toInt() and 0xFFFF

                        val tagName = if (nameIndex >= 0 && nameIndex < strings.size) strings[nameIndex] else "tag_$nameIndex"
                        
                        sb.append(getIndent()).append("<").append(tagName)
                        indent++

                        // Read attributes
                        for (a in 0 until attributeCount) {
                            val attrNamespaceIndex = buffer.int
                            val attrNameIndex = buffer.int
                            val attrValueRawIndex = buffer.int
                            val attrType = buffer.short.toInt() and 0xFFFF
                            buffer.get() // skip hot
                            buffer.get() // skip data type info
                            val attrData = buffer.int

                            val attrName = if (attrNameIndex >= 0 && attrNameIndex < strings.size) strings[attrNameIndex] else "attr_$attrNameIndex"
                            val attrValue = if (attrValueRawIndex >= 0 && attrValueRawIndex < strings.size) {
                                strings[attrValueRawIndex]
                            } else {
                                // fallback to data description
                                when (attrType) {
                                    0x03000008 -> "0x" + Integer.toHexString(attrData)
                                    0x12000008 -> if (attrData != 0) "true" else "false"
                                    0x10000008 -> attrData.toString()
                                    else -> "ref_0x" + Integer.toHexString(attrData)
                                }
                            }
                            sb.append("\n").append(getIndent()).append(attrName).append("=\"").append(attrValue).append("\"")
                        }
                        sb.append(">\n")
                    }

                    0x00080103 -> { // End Element Tag
                        val lineNumber = buffer.int
                        val commentIndex = buffer.int
                        val namespaceUriIndex = buffer.int
                        val nameIndex = buffer.int

                        val tagName = if (nameIndex >= 0 && nameIndex < strings.size) strings[nameIndex] else "tag_$nameIndex"
                        indent--
                        sb.append(getIndent()).append("</").append(tagName).append(">\n")
                    }

                    0x00080104 -> { // Text / CDATA
                        val lineNumber = buffer.int
                        val commentIndex = buffer.int
                        val textIndex = buffer.int
                        val rawIndex = buffer.int
                        val text = if (textIndex >= 0 && textIndex < strings.size) strings[textIndex] else ""
                        sb.append(getIndent()).append(text).append("\n")
                        // Seek remainder of chunk
                        buffer.position(buffer.position() + chunkSize - 28)
                    }

                    else -> {
                        // Unknown chunk or skip to chunk size
                        val skipAmount = chunkSize - 8
                        if (skipAmount > 0 && skipAmount <= buffer.remaining()) {
                            buffer.position(buffer.position() + skipAmount)
                        } else {
                            break
                        }
                    }
                }
            }

            return sb.toString()
        } catch (e: Exception) {
            return "Decompilation failed: ${e.localizedMessage}\n\nStack Trace:\n${e.stackTraceToString()}"
        }
    }

    // --- DEX FILE HEADER PARSER ---
    // Reads magic info, string counts, class definitions, and header info of classes.dex
    fun parseDexHeader(bytes: ByteArray): String {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < 0x70) {
                return "Error: File too small to be a valid DEX file."
            }

            val magicBytes = ByteArray(8)
            buffer.get(magicBytes)
            val magic = String(magicBytes)
            if (!magic.startsWith("dex\n")) {
                return "Error: Invalid DEX Magic Number (got: ${magic.replace("\n", "\\n")})"
            }

            val checksum = buffer.int
            val signature = ByteArray(20)
            buffer.get(signature)
            val fileSize = buffer.int
            val headerSize = buffer.int
            val endianTag = buffer.int
            val linkSize = buffer.int
            val linkOff = buffer.int
            val mapOff = buffer.int
            val stringIdsSize = buffer.int
            val stringIdsOff = buffer.int
            val typeIdsSize = buffer.int
            val typeIdsOff = buffer.int
            val protoIdsSize = buffer.int
            val protoIdsOff = buffer.int
            val fieldIdsSize = buffer.int
            val fieldIdsOff = buffer.int
            val methodIdsSize = buffer.int
            val methodIdsOff = buffer.int
            val classDefsSize = buffer.int
            val classDefsOff = buffer.int

            val isBigEndian = endianTag == 0x78563412

            val sb = StringBuilder()
            sb.append("=== DEX File Information ===\n")
            sb.append("Magic: ${magic.replace("\n", "\\n")}\n")
            sb.append("Checksum: 0x${Integer.toHexString(checksum)}\n")
            sb.append("File Size: $fileSize bytes (${String.format("%.2f", fileSize / 1024.0)} KB)\n")
            sb.append("Header Size: $headerSize bytes\n")
            sb.append("Endianness: ${if (isBigEndian) "Big Endian (Non-standard)" else "Little Endian (Standard)"}\n")
            sb.append("String IDs: $stringIdsSize (Offset: 0x${Integer.toHexString(stringIdsOff)})\n")
            sb.append("Type IDs: $typeIdsSize (Offset: 0x${Integer.toHexString(typeIdsOff)})\n")
            sb.append("Proto IDs: $protoIdsSize (Offset: 0x${Integer.toHexString(protoIdsOff)})\n")
            sb.append("Field IDs: $fieldIdsSize (Offset: 0x${Integer.toHexString(fieldIdsOff)})\n")
            sb.append("Method IDs: $methodIdsSize (Offset: 0x${Integer.toHexString(methodIdsOff)})\n")
            sb.append("Class Definitions: $classDefsSize (Offset: 0x${Integer.toHexString(classDefsOff)})\n\n")

            sb.append("=== Parsed Class Structures (Quick Summary) ===\n")
            if (classDefsSize > 0 && classDefsOff < bytes.size) {
                buffer.position(classDefsOff)
                val countToShow = minOf(classDefsSize, 50)
                sb.append("Displaying first $countToShow defined classes:\n")
                
                // Class definitions start at classDefsOff
                // Structure of ClassDef:
                // class_idx (uint), access_flags (uint), superclass_idx (uint), interfaces_off (uint), source_file_idx (uint), annotations_off (uint), class_data_off (uint), static_values_off (uint)
                // Let's print out the raw offset list of the classes
                for (i in 0 until countToShow) {
                    val classIdx = buffer.int
                    val accessFlags = buffer.int
                    val superclassIdx = buffer.int
                    val interfacesOff = buffer.int
                    val sourceFileIdx = buffer.int
                    val annotationsOff = buffer.int
                    val classDataOff = buffer.int
                    val staticValuesOff = buffer.int

                    sb.append("- Class #$i (Type ID: $classIdx, Flags: 0x${Integer.toHexString(accessFlags)}, Superclass ID: $superclassIdx, Code Offset: 0x${Integer.toHexString(classDataOff)})\n")
                }
                if (classDefsSize > countToShow) {
                    sb.append("... and ${classDefsSize - countToShow} more classes.")
                }
            } else {
                sb.append("No defined classes found or offset exceeds file bounds.\n")
            }

            return sb.toString()
        } catch (e: Exception) {
            return "Failed to parse DEX header: ${e.localizedMessage}\n\n${e.stackTraceToString()}"
        }
    }

    // --- ARSC FILE PARSER ---
    // Parses resources.arsc chunk types and displays resources table
    fun parseArscHeader(bytes: ByteArray): String {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < 12) {
                return "Error: File too small to be a valid resources.arsc file."
            }

            val type = buffer.short.toInt() and 0xFFFF
            val headerSize = buffer.short.toInt() and 0xFFFF
            val size = buffer.int
            val packageCount = buffer.int

            if (type != 0x0002) {
                return "Error: Invalid ARSC Type magic (expected 0x0002, got 0x${Integer.toHexString(type)})"
            }

            val sb = StringBuilder()
            sb.append("=== Resources Table (ARSC) Header ===\n")
            sb.append("Type: Resource Table (0x0002)\n")
            sb.append("Header Size: $headerSize bytes\n")
            sb.append("Total Size: $size bytes\n")
            sb.append("Package Count: $packageCount\n\n")

            // Parse children chunks
            while (buffer.hasRemaining()) {
                val cPos = buffer.position()
                if (cPos + 8 > bytes.size) break
                val cType = buffer.short.toInt() and 0xFFFF
                val cHeaderSize = buffer.short.toInt() and 0xFFFF
                val cSize = buffer.int

                sb.append("Chunk detected: ")
                when (cType) {
                    0x0001 -> { // String Pool
                        sb.append("STRING_POOL (Size: $cSize, Header: $cHeaderSize)\n")
                    }
                    0x0200 -> { // Package Chunk
                        if (cPos + 284 <= bytes.size) {
                            buffer.position(cPos + 8)
                            val pkgId = buffer.int
                            val pkgNameChars = CharArray(128)
                            for (i in 0 until 128) {
                                pkgNameChars[i] = buffer.char
                            }
                            val pkgName = String(pkgNameChars).trim { it <= ' ' || it.code == 0 }
                            sb.append("PACKAGE_INFO (ID: $pkgId, Name: \"$pkgName\", Total Size: $cSize)\n")
                        } else {
                            sb.append("PACKAGE_INFO (Truncated, size: $cSize)\n")
                        }
                    }
                    0x0201 -> { // Type String Pool
                        sb.append("TYPE_STRING_POOL (Size: $cSize)\n")
                    }
                    0x0202 -> { // Key String Pool
                        sb.append("KEY_STRING_POOL (Size: $cSize)\n")
                    }
                    0x0203 -> { // Type Spec
                        sb.append("TYPE_SPEC (Size: $cSize)\n")
                    }
                    0x0204 -> { // Type Entry (resource specs)
                        sb.append("TYPE_ENTRY (Size: $cSize)\n")
                    }
                    else -> {
                        sb.append("UNKNOWN CHUNK [0x${Integer.toHexString(cType)}] (Size: $cSize)\n")
                    }
                }

                if (cSize > 0 && cPos + cSize <= bytes.size) {
                    buffer.position(cPos + cSize)
                } else {
                    break
                }
            }

            return sb.toString()
        } catch (e: Exception) {
            return "Failed to parse ARSC table: ${e.localizedMessage}\n\n${e.stackTraceToString()}"
        }
    }

    // --- EXPLORE APK ZIP ENTRIES ---
    // Lists contents of selected APK
    fun listApkEntries(apkBytes: ByteArray): List<ApkEntry> {
        val list = mutableListOf<ApkEntry>()
        try {
            val bais = ByteArrayInputStream(apkBytes)
            val zis = ZipInputStream(bais)
            var entry = zis.nextEntry
            while (entry != null) {
                list.add(
                    ApkEntry(
                        name = entry.name,
                        size = entry.size,
                        compressedSize = entry.compressedSize,
                        isDirectory = entry.isDirectory
                    )
                )
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            // Ignored
        }
        return list
    }

    // Extract exact file from APK bytes
    fun extractEntryBytes(apkBytes: ByteArray, entryName: String): ByteArray? {
        try {
            val bais = ByteArrayInputStream(apkBytes)
            val zis = ZipInputStream(bais)
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var len = zis.read(buf)
                    while (len != -1) {
                        out.write(buf, 0, len)
                        len = zis.read(buf)
                    }
                    zis.close()
                    return out.toByteArray()
                }
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            // Ignored
        }
        return null
    }

    private fun asn1(tag: Byte, vararg elements: ByteArray): ByteArray {
        val content = elements.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
        return wrapTag(tag, content)
    }

    private fun wrapTag(tag: Byte, content: ByteArray): ByteArray {
        val len = content.size
        val lenBytes = if (len < 128) {
            byteArrayOf(len.toByte())
        } else {
            val bytesList = mutableListOf<Byte>()
            var temp = len
            while (temp > 0) {
                bytesList.add(0, (temp and 0xFF).toByte())
                temp = temp ushr 8
            }
            val firstByte = (0x80 or bytesList.size).toByte()
            byteArrayOf(firstByte) + bytesList.toByteArray()
        }
        return byteArrayOf(tag) + lenBytes + content
    }

    private fun oid(oidStr: String): ByteArray {
        val parts = oidStr.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (i in 2 until parts.size) {
            var num = parts[i]
            val temp = mutableListOf<Byte>()
            temp.add((num and 0x7F).toByte())
            while (num >= 128) {
                num = num ushr 7
                temp.add(0, ((num and 0x7F) or 0x80).toByte())
            }
            out.write(temp.toByteArray())
        }
        return wrapTag(0x06, out.toByteArray())
    }

    private fun integer(value: Long): ByteArray {
        var temp = value
        val list = mutableListOf<Byte>()
        list.add((temp and 0xFF).toByte())
        while (temp > 127 || temp < -128) {
            temp = temp shr 8
            list.add(0, (temp and 0xFF).toByte())
        }
        return wrapTag(0x02, list.toByteArray())
    }

    private fun octetString(bytes: ByteArray): ByteArray {
        return wrapTag(0x04, bytes)
    }

    fun signApkWithEntries(apkFile: File, modifiedEntries: Map<String, ByteArray> = emptyMap()) {
        val tempFile = File(apkFile.parentFile, apkFile.name + ".tmp")
        try {
            val md = MessageDigest.getInstance("SHA-256")
            
            // First Pass: Scan the original APK, compute SHA-256 digests of all entries, and collect metadata
            val entryDigests = java.util.LinkedHashMap<String, String>()
            val entriesMeta = java.util.LinkedHashMap<String, ZipEntryInfo>()
            
            var fis = java.io.FileInputStream(apkFile)
            var zis = ZipInputStream(fis)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !isSignatureFile(entry.name)) {
                    val name = entry.name
                    val dataBytes = modifiedEntries[name]
                    
                    val (digestStr, size, crc) = if (dataBytes != null) {
                        // Use modified data
                        md.reset()
                        val digest = md.digest(dataBytes)
                        val b64 = Base64.encodeToString(digest, Base64.NO_WRAP)
                        Triple(b64, dataBytes.size.toLong(), calculateCrc32(dataBytes))
                    } else {
                        // Compute digest of original data by streaming it
                        md.reset()
                        val crcCalculator = java.util.zip.CRC32()
                        val buf = ByteArray(4096)
                        var totalLen = 0L
                        var len = zis.read(buf)
                        while (len != -1) {
                            md.update(buf, 0, len)
                            crcCalculator.update(buf, 0, len)
                            totalLen += len
                            len = zis.read(buf)
                        }
                        val digest = md.digest()
                        val b64 = Base64.encodeToString(digest, Base64.NO_WRAP)
                        Triple(b64, totalLen, crcCalculator.value)
                    }
                    
                    entryDigests[name] = digestStr
                    entriesMeta[name] = ZipEntryInfo(
                        name = name,
                        method = entry.method,
                        size = size,
                        crc = crc
                    )
                }
                entry = zis.nextEntry
            }
            zis.close()
            fis.close()
            
            // Add any newly added modified entries that didn't exist in original APK
            for ((mName, mBytes) in modifiedEntries) {
                if (!entryDigests.containsKey(mName)) {
                    md.reset()
                    val digest = md.digest(mBytes)
                    val b64 = Base64.encodeToString(digest, Base64.NO_WRAP)
                    entryDigests[mName] = b64
                    entriesMeta[mName] = ZipEntryInfo(
                        name = mName,
                        method = ZipEntry.DEFLATED,
                        size = mBytes.size.toLong(),
                        crc = calculateCrc32(mBytes)
                    )
                }
            }
            
            // Build Manifest
            val manifestSb = StringBuilder()
            manifestSb.append("Manifest-Version: 1.0\r\n")
            manifestSb.append("Created-By: 1.0 (Android)\r\n\r\n")
            
            val entryBlocks = java.util.LinkedHashMap<String, ByteArray>()
            for ((name, digestStr) in entryDigests) {
                val blockSb = StringBuilder()
                blockSb.append("Name: $name\r\n")
                blockSb.append("SHA-256-Digest: $digestStr\r\n\r\n")
                
                val blockBytes = blockSb.toString().toByteArray(Charsets.UTF_8)
                entryBlocks[name] = blockBytes
                manifestSb.append(blockSb)
            }
            val manifestBytes = manifestSb.toString().toByteArray(Charsets.UTF_8)
            
            // Build SF (Signature File)
            val sfSb = StringBuilder()
            sfSb.append("Signature-Version: 1.0\r\n")
            sfSb.append("Created-By: 1.0 (Android)\r\n")
            
            md.reset()
            val manifestDigest = md.digest(manifestBytes)
            val base64ManifestDigest = Base64.encodeToString(manifestDigest, Base64.NO_WRAP)
            sfSb.append("SHA-256-Digest-Manifest: $base64ManifestDigest\r\n\r\n")
            
            for ((name, blockBytes) in entryBlocks) {
                md.reset()
                val blockDigest = md.digest(blockBytes)
                val base64BlockDigest = Base64.encodeToString(blockDigest, Base64.NO_WRAP)
                sfSb.append("Name: $name\r\n")
                sfSb.append("SHA-256-Digest: $base64BlockDigest\r\n\r\n")
            }
            val sfBytes = sfSb.toString().toByteArray(Charsets.UTF_8)
            
            // Signature generation (RSA and PKCS7 block)
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            val keyPair = keyGen.generateKeyPair()
            
            val serialNumber = integer(System.currentTimeMillis())
            val sigAlg = asn1(0x30, oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00)) // SHA256withRSA
            
            val cnOid = oid("2.5.4.3")
            val cnValue = wrapTag(0x0C, "Android Test".toByteArray(Charsets.UTF_8))
            val cnSeq = asn1(0x30, cnOid, cnValue)
            val issuerName = asn1(0x30, wrapTag(0x31, cnSeq))
            
            val start = wrapTag(0x17, "260101000000Z".toByteArray(Charsets.US_ASCII))
            val end = wrapTag(0x17, "360101000000Z".toByteArray(Charsets.US_ASCII))
            val validity = asn1(0x30, start, end)
            
            val tbsCert = asn1(0x30,
                wrapTag(0xA0.toByte(), integer(2)), // Version 3
                serialNumber,
                sigAlg,
                issuerName,
                validity,
                issuerName,
                keyPair.public.encoded
            )
            
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(keyPair.private)
            signature.update(tbsCert)
            val sigBytes = signature.sign()
            val signatureBitString = wrapTag(0x03, byteArrayOf(0x00) + sigBytes)
            
            val certificateBytes = asn1(0x30, tbsCert, sigAlg, signatureBitString)
            
            val rsaSigner = Signature.getInstance("SHA256withRSA")
            rsaSigner.initSign(keyPair.private)
            rsaSigner.update(sfBytes)
            val rawSignature = rsaSigner.sign()
            val signatureOctetString = octetString(rawSignature)
            
            val signerId = asn1(0x30, issuerName, serialNumber)
            val digestAlg = asn1(0x30, oid("2.16.840.1.101.3.4.2.1"), byteArrayOf(0x05, 0x00))
            val signatureAlgId = asn1(0x30, oid("1.2.840.113549.1.1.1"), byteArrayOf(0x05, 0x00))
            
            val signerInfo = asn1(0x30,
                integer(1),
                signerId,
                digestAlg,
                signatureAlgId,
                signatureOctetString
            )
            
            val signedData = asn1(0x30,
                integer(1),
                wrapTag(0x31, sigAlg),
                asn1(0x30, oid("1.2.840.113549.1.7.1")),
                wrapTag(0xA0.toByte(), certificateBytes),
                wrapTag(0x31, signerInfo)
            )
            
            val pkcs7Block = asn1(0x30,
                oid("1.2.840.113549.1.7.2"),
                wrapTag(0xA0.toByte(), signedData)
            )
            
            // Second Pass: Write entries to temp output APK
            val tempFos = FileOutputStream(tempFile)
            val zos = ZipOutputStream(tempFos)
            
            fis = java.io.FileInputStream(apkFile)
            zis = ZipInputStream(fis)
            entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !isSignatureFile(entry.name)) {
                    val name = entry.name
                    val meta = entriesMeta[name] ?: ZipEntryInfo(name, entry.method, entry.size, entry.crc)
                    
                    val zEntry = ZipEntry(name)
                    zEntry.method = meta.method
                    if (meta.method == ZipEntry.STORED) {
                        zEntry.size = meta.size
                        zEntry.compressedSize = meta.size
                        zEntry.crc = meta.crc
                    }
                    zos.putNextEntry(zEntry)
                    
                    val mBytes = modifiedEntries[name]
                    if (mBytes != null) {
                        zos.write(mBytes)
                    } else {
                        val buf = ByteArray(4096)
                        var len = zis.read(buf)
                        while (len != -1) {
                            zos.write(buf, 0, len)
                            len = zis.read(buf)
                        }
                    }
                    zos.closeEntry()
                }
                entry = zis.nextEntry
            }
            zis.close()
            fis.close()
            
            // Add any newly added modified entries that didn't exist in original APK
            for ((mName, mBytes) in modifiedEntries) {
                if (!entriesMeta.containsKey(mName)) {
                    val zEntry = ZipEntry(mName)
                    zos.putNextEntry(zEntry)
                    zos.write(mBytes)
                    zos.closeEntry()
                }
            }
            
            // Write signature files
            val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
            zos.putNextEntry(manifestEntry)
            zos.write(manifestBytes)
            zos.closeEntry()
            
            val sfEntry = ZipEntry("META-INF/CERT.SF")
            zos.putNextEntry(sfEntry)
            zos.write(sfBytes)
            zos.closeEntry()
            
            val rsaEntry = ZipEntry("META-INF/CERT.RSA")
            zos.putNextEntry(rsaEntry)
            zos.write(pkcs7Block)
            zos.closeEntry()
            
            zos.close()
            tempFos.close()
            
            if (tempFile.exists()) {
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
    
    private fun calculateCrc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }
    
    private fun isSignatureFile(name: String): Boolean {
        val upperName = name.uppercase()
        if (!upperName.startsWith("META-INF/")) return false
        val fileName = name.substringAfterLast('/')
        val ext = fileName.substringAfterLast('.', "").uppercase()
        return fileName == "MANIFEST.MF" || ext == "SF" || ext == "RSA" || ext == "DSA" || ext == "EC"
    }

    fun parseDexClasses(bytes: ByteArray): List<DexClass> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 0x70) return emptyList()
        
        // Get all strings first
        val dexStrings = parseDexStrings(bytes)
        val stringMap = dexStrings.associateBy { it.index }

        // Read type IDs
        buffer.position(0x40)
        val typeIdsSize = buffer.int
        val typeIdsOff = buffer.int
        if (typeIdsOff <= 0 || typeIdsOff >= bytes.size) return emptyList()
        
        val typeStringIndexes = IntArray(typeIdsSize)
        buffer.position(typeIdsOff)
        for (i in 0 until typeIdsSize) {
            typeStringIndexes[i] = buffer.int
        }

        // Read Class Defs
        buffer.position(0x60)
        val classDefsSize = buffer.int
        val classDefsOff = buffer.int
        if (classDefsOff <= 0 || classDefsOff >= bytes.size) return emptyList()
        buffer.position(classDefsOff)

        val classes = mutableListOf<DexClass>()
        for (i in 0 until classDefsSize) {
            val classIdx = buffer.int
            val accessFlags = buffer.int
            val superclassIdx = buffer.int
            val interfacesOff = buffer.int
            val sourceFileIdx = buffer.int
            val annotationsOff = buffer.int
            val classDataOff = buffer.int
            val staticValuesOff = buffer.int

            if (classIdx >= 0 && classIdx < typeIdsSize) {
                val stringIdx = typeStringIndexes[classIdx]
                val dexStr = stringMap[stringIdx]
                if (dexStr != null) {
                    classes.add(
                        DexClass(
                            name = dexStr.value,
                            typeIdx = classIdx,
                            stringIndex = stringIdx,
                            stringOffset = dexStr.offset,
                            byteLength = dexStr.byteLength
                        )
                    )
                }
            }
        }
        return classes
    }

    fun parseDexStrings(bytes: ByteArray): List<DexString> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 0x70) return emptyList()
        buffer.position(0x38)
        val stringIdsSize = buffer.int
        val stringIdsOff = buffer.int
        if (stringIdsOff <= 0 || stringIdsOff >= bytes.size) return emptyList()
        
        buffer.position(stringIdsOff)
        val stringOffsets = IntArray(stringIdsSize)
        for (i in 0 until stringIdsSize) {
            stringOffsets[i] = buffer.int
        }

        val list = mutableListOf<DexString>()
        for (i in 0 until stringIdsSize) {
            val offset = stringOffsets[i]
            if (offset <= 0 || offset >= bytes.size) continue
            buffer.position(offset)
            
            // Read uleb128 size
            var result = 0
            var shift = 0
            var b: Byte
            do {
                b = buffer.get()
                result = result or ((b.toInt() and 0x7f) shl shift)
                shift += 7
            } while ((b.toInt() and 0x80) != 0)

            val stringStartPos = buffer.position()
            // Read bytes until null terminator
            val out = ByteArrayOutputStream()
            while (true) {
                if (buffer.position() >= bytes.size) break
                val charByte = buffer.get()
                if (charByte == 0.toByte()) break
                out.write(charByte.toInt())
            }
            val value = String(out.toByteArray(), Charsets.UTF_8)
            val byteLength = buffer.position() - 1 - stringStartPos

            list.add(DexString(i, offset, value, byteLength))
        }
        return list
    }

    fun writeDexStringInPlace(bytes: ByteArray, dexString: DexString, newValue: String): ByteArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = dexString.offset
        
        // Determine original ULEB128 size in bytes
        var ulebSize = 0
        var b: Byte
        buffer.position(offset)
        do {
            b = buffer.get()
            ulebSize++
        } while ((b.toInt() and 0x80) != 0)
        
        // Encode the new length as a padded ULEB128 of the same size
        val newUleb = ByteArray(ulebSize)
        var temp = newValue.length
        for (i in 0 until ulebSize) {
            var valByte = temp and 0x7f
            temp = temp ushr 7
            if (i < ulebSize - 1) {
                valByte = valByte or 0x80
            }
            newUleb[i] = valByte.toByte()
        }
        
        // Write the new ULEB128 size back
        buffer.position(offset)
        buffer.put(newUleb)
        
        val stringStartPos = buffer.position()
        val newBytes = newValue.toByteArray(Charsets.UTF_8)
        
        // Write new bytes
        buffer.put(newBytes)
        
        // Write a null terminator
        buffer.put(0.toByte())
        
        // Fill remainder of the original byteLength with 0x00 null bytes
        val writtenBytes = newBytes.size + 1
        val remaining = (dexString.byteLength + 1) - writtenBytes
        if (remaining > 0) {
            for (k in 0 until remaining) {
                buffer.put(0.toByte())
            }
        }

        // Recalculate SHA-1 signature
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(bytes, 32, bytes.size - 32)
        val sha1 = digest.digest()
        
        // Write signature back at offset 12
        System.arraycopy(sha1, 0, bytes, 12, 20)
        
        // Adler32 checksum
        val adler = java.util.zip.Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val checksum = adler.value.toInt()
        
        // Write checksum back at offset 8 in Little Endian
        bytes[8] = (checksum and 0xFF).toByte()
        bytes[9] = ((checksum shr 8) and 0xFF).toByte()
        bytes[10] = ((checksum shr 16) and 0xFF).toByte()
        bytes[11] = ((checksum shr 24) and 0xFF).toByte()

        return bytes
    }

    private fun readUleb128(buffer: ByteBuffer): Int {
        var result = 0
        var shift = 0
        var b: Byte
        do {
            b = buffer.get()
            result = result or ((b.toInt() and 0x7f) shl shift)
            shift += 7
        } while ((b.toInt() and 0x80) != 0)
        return result
    }

    fun decompileClassMethods(bytes: ByteArray, dexClass: DexClass): List<DexMethod> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 0x70) return emptyList()

        val strings = parseDexStrings(bytes).map { it.value }

        // Read types
        buffer.position(0x40)
        val typeIdsSize = buffer.int
        val typeIdsOff = buffer.int
        val typeNames = Array(typeIdsSize) { "" }
        if (typeIdsOff > 0 && typeIdsOff < bytes.size) {
            val typeBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            typeBuf.position(typeIdsOff)
            for (i in 0 until typeIdsSize) {
                val sIdx = typeBuf.int
                typeNames[i] = if (sIdx >= 0 && sIdx < strings.size) strings[sIdx] else ""
            }
        }

        // Read methods
        buffer.position(0x4c)
        val methodIdsSize = buffer.int
        val methodIdsOff = buffer.int

        data class SimpleMethodRef(val classType: String, val name: String)
        val methodRefs = Array(methodIdsSize) { SimpleMethodRef("", "") }
        if (methodIdsOff > 0 && methodIdsOff < bytes.size) {
            val methodBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            methodBuf.position(methodIdsOff)
            for (i in 0 until methodIdsSize) {
                val classIdx = methodBuf.short.toInt() and 0xffff
                val protoIdx = methodBuf.short.toInt() and 0xffff
                val nameIdx = methodBuf.int

                val className = if (classIdx in typeNames.indices) typeNames[classIdx] else ""
                val methodName = if (nameIdx in strings.indices) strings[nameIdx] else ""
                methodRefs[i] = SimpleMethodRef(className, methodName)
            }
        }

        // Read Class Defs
        buffer.position(0x60)
        val classDefsSize = buffer.int
        val classDefsOff = buffer.int
        if (classDefsOff <= 0 || classDefsOff >= bytes.size) return emptyList()

        var classDataOff = 0
        val classDefBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        classDefBuf.position(classDefsOff)
        for (i in 0 until classDefsSize) {
            val classIdx = classDefBuf.int
            val accessFlags = classDefBuf.int
            val superclassIdx = classDefBuf.int
            val interfacesOff = classDefBuf.int
            val sourceFileIdx = classDefBuf.int
            val annotationsOff = classDefBuf.int
            val cDataOff = classDefBuf.int
            val staticValuesOff = classDefBuf.int

            if (classIdx == dexClass.typeIdx) {
                classDataOff = cDataOff
                break
            }
        }

        if (classDataOff <= 0 || classDataOff >= bytes.size) return emptyList()

        val cDataBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        cDataBuf.position(classDataOff)

        val staticFieldsSize = readUleb128(cDataBuf)
        val instanceFieldsSize = readUleb128(cDataBuf)
        val directMethodsSize = readUleb128(cDataBuf)
        val virtualMethodsSize = readUleb128(cDataBuf)

        // Skip fields
        for (i in 0 until staticFieldsSize) {
            readUleb128(cDataBuf) // field_idx_diff
            readUleb128(cDataBuf) // access_flags
        }
        for (i in 0 until instanceFieldsSize) {
            readUleb128(cDataBuf) // field_idx_diff
            readUleb128(cDataBuf) // access_flags
        }

        val methods = mutableListOf<DexMethod>()

        // Direct methods
        var methodIdx = 0
        for (i in 0 until directMethodsSize) {
            val methodIdxDiff = readUleb128(cDataBuf)
            val accessFlags = readUleb128(cDataBuf)
            val codeOff = readUleb128(cDataBuf)
            methodIdx += methodIdxDiff

            val mRef = if (methodIdx in methodRefs.indices) methodRefs[methodIdx] else SimpleMethodRef("", "")
            methods.add(decompileMethod(bytes, methodIdx, mRef.name, accessFlags, codeOff, strings, typeNames))
        }

        // Virtual methods
        methodIdx = 0
        for (i in 0 until virtualMethodsSize) {
            val methodIdxDiff = readUleb128(cDataBuf)
            val accessFlags = readUleb128(cDataBuf)
            val codeOff = readUleb128(cDataBuf)
            methodIdx += methodIdxDiff

            val mRef = if (methodIdx in methodRefs.indices) methodRefs[methodIdx] else SimpleMethodRef("", "")
            methods.add(decompileMethod(bytes, methodIdx, mRef.name, accessFlags, codeOff, strings, typeNames))
        }

        return methods
    }

    private fun decompileMethod(
        bytes: ByteArray,
        methodIdx: Int,
        name: String,
        accessFlags: Int,
        codeOff: Int,
        strings: List<String>,
        typeNames: Array<String>
    ): DexMethod {
        if (codeOff <= 0 || codeOff >= bytes.size) {
            return DexMethod(
                methodIdx = methodIdx,
                name = name,
                accessFlags = accessFlags,
                codeOffset = codeOff,
                registersSize = 0,
                insSize = 0,
                outsSize = 0,
                insnsSize = 0,
                instructionsSmali = listOf("# abstract or native method"),
                originalInsnBytes = byteArrayOf()
            )
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(codeOff)
        val registersSize = buf.short.toInt() and 0xffff
        val insSize = buf.short.toInt() and 0xffff
        val outsSize = buf.short.toInt() and 0xffff
        val triesSize = buf.short.toInt() and 0xffff
        val debugInfoOff = buf.int
        val insnsSize = buf.int

        val insnsBytes = ByteArray(insnsSize * 2)
        buf.get(insnsBytes)

        val instructionsSmali = decodeInstructions(insnsBytes, strings, typeNames)

        return DexMethod(
            methodIdx = methodIdx,
            name = name,
            accessFlags = accessFlags,
            codeOffset = codeOff,
            registersSize = registersSize,
            insSize = insSize,
            outsSize = outsSize,
            insnsSize = insnsSize,
            instructionsSmali = instructionsSmali,
            originalInsnBytes = insnsBytes
        )
    }

    private fun decodeInstructions(insnsBytes: ByteArray, strings: List<String>, typeNames: Array<String>): List<String> {
        val list = mutableListOf<String>()
        val len = insnsBytes.size / 2
        var i = 0
        while (i < len) {
            val word1 = (insnsBytes[i * 2].toInt() and 0xff) or ((insnsBytes[i * 2 + 1].toInt() and 0xff) shl 8)
            val opcode = word1 and 0xff

            when (opcode) {
                0x00 -> {
                    list.add("nop")
                    i += 1
                }
                0x0e -> {
                    list.add("return-void")
                    i += 1
                }
                0x0f -> {
                    val rA = (word1 shr 8) and 0xff
                    list.add("return v$rA")
                    i += 1
                }
                0x10 -> {
                    val rA = (word1 shr 8) and 0xff
                    list.add("return-wide v$rA")
                    i += 1
                }
                0x11 -> {
                    val rA = (word1 shr 8) and 0xff
                    list.add("return-object v$rA")
                    i += 1
                }
                0x12 -> {
                    val rA = (word1 shr 8) and 0xf
                    var literal = (word1 shr 12) and 0xf
                    if (literal > 7) literal -= 16
                    list.add("const/4 v$rA, $literal")
                    i += 1
                }
                0x13 -> {
                    if (i + 1 < len) {
                        val rA = (word1 shr 8) and 0xff
                        val literalWord = (insnsBytes[(i + 1) * 2].toInt() and 0xff) or ((insnsBytes[(i + 1) * 2 + 1].toInt() and 0xff) shl 8)
                        var literal = literalWord
                        if (literal > 32767) literal -= 65536
                        list.add("const/16 v$rA, $literal")
                        i += 2
                    } else {
                        list.add("const/16 error")
                        i += 1
                    }
                }
                0x14 -> {
                    if (i + 2 < len) {
                        val rA = (word1 shr 8) and 0xff
                        val low = (insnsBytes[(i + 1) * 2].toInt() and 0xff) or ((insnsBytes[(i + 1) * 2 + 1].toInt() and 0xff) shl 8)
                        val high = (insnsBytes[(i + 2) * 2].toInt() and 0xff) or ((insnsBytes[(i + 2) * 2 + 1].toInt() and 0xff) shl 8)
                        val literal = low or (high shl 16)
                        list.add("const v$rA, $literal")
                        i += 3
                    } else {
                        list.add("const error")
                        i += 1
                    }
                }
                0x1a -> {
                    if (i + 1 < len) {
                        val rA = (word1 shr 8) and 0xff
                        val strIdx = (insnsBytes[(i + 1) * 2].toInt() and 0xff) or ((insnsBytes[(i + 1) * 2 + 1].toInt() and 0xff) shl 8)
                        val strVal = if (strIdx in strings.indices) strings[strIdx] else ""
                        val escaped = strVal.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                        list.add("const-string v$rA, \"$escaped\"")
                        i += 2
                    } else {
                        list.add("const-string error")
                        i += 1
                    }
                }
                0x1c -> {
                    if (i + 1 < len) {
                        val rA = (word1 shr 8) and 0xff
                        val typeIdx = (insnsBytes[(i + 1) * 2].toInt() and 0xff) or ((insnsBytes[(i + 1) * 2 + 1].toInt() and 0xff) shl 8)
                        val typeVal = if (typeIdx in typeNames.indices) typeNames[typeIdx] else ""
                        list.add("const-class v$rA, $typeVal")
                        i += 2
                    } else {
                        list.add("const-class error")
                        i += 1
                    }
                }
                0x22 -> {
                    if (i + 1 < len) {
                        val rA = (word1 shr 8) and 0xff
                        val typeIdx = (insnsBytes[(i + 1) * 2].toInt() and 0xff) or ((insnsBytes[(i + 1) * 2 + 1].toInt() and 0xff) shl 8)
                        val typeVal = if (typeIdx in typeNames.indices) typeNames[typeIdx] else ""
                        list.add("new-instance v$rA, $typeVal")
                        i += 2
                    } else {
                        list.add("new-instance error")
                        i += 1
                    }
                }
                0x01 -> {
                    val rA = (word1 shr 8) and 0xf
                    val rB = (word1 shr 12) and 0xf
                    list.add("move v$rA, v$rB")
                    i += 1
                }
                0x07 -> {
                    val rA = (word1 shr 8) and 0xf
                    val rB = (word1 shr 12) and 0xf
                    list.add("move-object v$rA, v$rB")
                    i += 1
                }
                else -> {
                    val formatName = getOpcodeMnemonic(opcode)
                    val wordsCount = getOpcodeWordsCount(opcode)
                    if (wordsCount > 1 && i + wordsCount <= len) {
                        val sb = StringBuilder()
                        sb.append(formatName)
                        for (w in 0 until wordsCount) {
                            val rawWord = (insnsBytes[(i + w) * 2].toInt() and 0xff) or ((insnsBytes[(i + w) * 2 + 1].toInt() and 0xff) shl 8)
                            sb.append(String.format(" 0x%04X", rawWord))
                        }
                        list.add(sb.toString())
                        i += wordsCount
                    } else {
                        list.add(String.format("%s 0x%04X", formatName, word1))
                        i += 1
                    }
                }
            }
        }
        return list
    }

    private fun getOpcodeMnemonic(opcode: Int): String {
        return when (opcode) {
            0x00 -> "nop"
            0x01 -> "move"
            0x02 -> "move/from16"
            0x03 -> "move/16"
            0x04 -> "move-wide"
            0x05 -> "move-wide/from16"
            0x06 -> "move-wide/16"
            0x07 -> "move-object"
            0x08 -> "move-object/from16"
            0x09 -> "move-object/16"
            0x0a -> "move-result"
            0x0b -> "move-result-wide"
            0x0c -> "move-result-object"
            0x0d -> "move-exception"
            0x0e -> "return-void"
            0x0f -> "return"
            0x10 -> "return-wide"
            0x11 -> "return-object"
            0x12 -> "const/4"
            0x13 -> "const/16"
            0x14 -> "const"
            0x15 -> "const/high16"
            0x16 -> "const-wide/16"
            0x17 -> "const-wide/32"
            0x18 -> "const-wide"
            0x19 -> "const-wide/high16"
            0x1a -> "const-string"
            0x1b -> "const-string/jumbo"
            0x1c -> "const-class"
            0x1d -> "monitor-enter"
            0x1e -> "monitor-exit"
            0x1f -> "check-cast"
            0x20 -> "instance-of"
            0x21 -> "array-length"
            0x22 -> "new-instance"
            0x23 -> "new-array"
            0x24 -> "filled-new-array"
            0x25 -> "filled-new-array/range"
            0x26 -> "fill-array-data"
            0x27 -> "throw"
            0x28 -> "goto"
            0x29 -> "goto/16"
            0x2a -> "goto/32"
            0x2b -> "packed-switch"
            0x2c -> "sparse-switch"
            0x2d -> "cmpl-float"
            0x2e -> "cmpg-float"
            0x2f -> "cmpl-double"
            0x30 -> "cmpg-double"
            0x31 -> "cmp-long"
            0x32 -> "if-eq"
            0x33 -> "if-ne"
            0x34 -> "if-lt"
            0x35 -> "if-ge"
            0x36 -> "if-gt"
            0x37 -> "if-le"
            0x38 -> "if-eqz"
            0x39 -> "if-nez"
            0x3a -> "if-ltz"
            0x3b -> "if-gez"
            0x3c -> "if-gtz"
            0x3d -> "if-lez"
            in 0x44..0x51 -> "array-op"
            in 0x52..0x5f -> "field-op"
            in 0x6e..0x72 -> "invoke-op"
            in 0x74..0x78 -> "invoke-range-op"
            else -> "op_" + String.format("%02X", opcode)
        }
    }

    private fun getOpcodeWordsCount(opcode: Int): Int {
        return when (opcode) {
            0x00 -> 1
            0x01 -> 1
            0x02 -> 2
            0x03 -> 3
            0x04 -> 1
            0x05 -> 2
            0x06 -> 3
            0x07 -> 1
            0x08 -> 2
            0x09 -> 3
            0x0a -> 1
            0x0b -> 1
            0x0c -> 1
            0x0d -> 1
            0x0e -> 1
            0x0f -> 1
            0x10 -> 1
            0x11 -> 1
            0x12 -> 1
            0x13 -> 2
            0x14 -> 3
            0x15 -> 2
            0x16 -> 2
            0x17 -> 3
            0x18 -> 5
            0x19 -> 2
            0x1a -> 2
            0x1b -> 3
            0x1c -> 2
            0x1d -> 1
            0x1e -> 1
            0x1f -> 2
            0x20 -> 2
            0x21 -> 1
            0x22 -> 2
            0x23 -> 2
            0x24 -> 3
            0x25 -> 3
            0x26 -> 3
            0x27 -> 1
            0x28 -> 1
            0x29 -> 2
            0x2a -> 3
            0x2b -> 3
            0x2c -> 3
            in 0x2d..0x31 -> 1
            in 0x32..0x37 -> 2
            in 0x38..0x3d -> 2
            in 0x44..0x51 -> 2
            in 0x52..0x5f -> 2
            in 0x6e..0x72 -> 3
            in 0x74..0x78 -> 3
            else -> 1
        }
    }

    fun assembleInstructions(lines: List<String>, strings: List<DexString>, typeNames: List<DexClass>): ByteArray {
        val out = ByteArrayOutputStream()
        val stringMap = strings.associateBy { it.value }
        val typeMap = typeNames.associateBy { it.name }

        for (lineRaw in lines) {
            val line = lineRaw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            val parts = line.split(Regex("\\s+"), 2)
            val mnemonic = parts[0]
            val operands = if (parts.size > 1) parts[1] else ""

            when (mnemonic) {
                "nop" -> {
                    out.write(0x00)
                    out.write(0x00)
                }
                "return-void" -> {
                    out.write(0x0e)
                    out.write(0x00)
                }
                "return" -> {
                    val regNum = extractRegNum(operands)
                    out.write(0x0f)
                    out.write(regNum)
                }
                "return-wide" -> {
                    val regNum = extractRegNum(operands)
                    out.write(0x10)
                    out.write(regNum)
                }
                "return-object" -> {
                    val regNum = extractRegNum(operands)
                    out.write(0x11)
                    out.write(regNum)
                }
                "const/4" -> {
                    val ops = operands.split(",")
                    val regNum = extractRegNum(ops[0])
                    var lit = ops[1].trim().toIntOrNull() ?: 0
                    if (lit < 0) lit += 16
                    lit = lit and 0xf

                    val word = (lit shl 12) or (regNum shl 8) or 0x12
                    out.write(word and 0xff)
                    out.write((word shr 8) and 0xff)
                }
                "const/16" -> {
                    val ops = operands.split(",")
                    val regNum = extractRegNum(ops[0])
                    var lit = ops[1].trim().toIntOrNull() ?: 0
                    if (lit < 0) lit += 65536
                    lit = lit and 0xffff

                    val word1 = (regNum shl 8) or 0x13
                    out.write(word1 and 0xff)
                    out.write((word1 shr 8) and 0xff)
                    out.write(lit and 0xff)
                    out.write((lit shr 8) and 0xff)
                }
                "const" -> {
                    val ops = operands.split(",")
                    val regNum = extractRegNum(ops[0])
                    val lit = ops[1].trim().toIntOrNull() ?: 0

                    val word1 = (regNum shl 8) or 0x14
                    out.write(word1 and 0xff)
                    out.write((word1 shr 8) and 0xff)
                    out.write(lit and 0xff)
                    out.write((lit shr 8) and 0xff)
                    out.write((lit shr 16) and 0xff)
                    out.write((lit shr 24) and 0xff)
                }
                "const-string" -> {
                    val ops = operands.split(",", limit = 2)
                    val regNum = extractRegNum(ops[0])
                    var strVal = ops[1].trim()
                    if (strVal.startsWith("\"") && strVal.endsWith("\"")) {
                        strVal = strVal.substring(1, strVal.length - 1)
                    }
                    strVal = strVal.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r")

                    val strIdx = stringMap[strVal]?.index ?: 0
                    val word1 = (regNum shl 8) or 0x1a
                    out.write(word1 and 0xff)
                    out.write((word1 shr 8) and 0xff)
                    out.write(strIdx and 0xff)
                    out.write((strIdx shr 8) and 0xff)
                }
                "const-class" -> {
                    val ops = operands.split(",")
                    val regNum = extractRegNum(ops[0])
                    val typeVal = ops[1].trim()
                    val typeIdx = typeMap[typeVal]?.typeIdx ?: 0

                    val word1 = (regNum shl 8) or 0x1c
                    out.write(word1 and 0xff)
                    out.write((word1 shr 8) and 0xff)
                    out.write(typeIdx and 0xff)
                    out.write((typeIdx shr 8) and 0xff)
                }
                "new-instance" -> {
                    val ops = operands.split(",")
                    val regNum = extractRegNum(ops[0])
                    val typeVal = ops[1].trim()
                    val typeIdx = typeMap[typeVal]?.typeIdx ?: 0

                    val word1 = (regNum shl 8) or 0x22
                    out.write(word1 and 0xff)
                    out.write((word1 shr 8) and 0xff)
                    out.write(typeIdx and 0xff)
                    out.write((typeIdx shr 8) and 0xff)
                }
                "move" -> {
                    val ops = operands.split(",")
                    val rA = extractRegNum(ops[0])
                    val rB = extractRegNum(ops[1])
                    val word = (rB shl 12) or (rA shl 8) or 0x01
                    out.write(word and 0xff)
                    out.write((word shr 8) and 0xff)
                }
                "move-object" -> {
                    val ops = operands.split(",")
                    val rA = extractRegNum(ops[0])
                    val rB = extractRegNum(ops[1])
                    val word = (rB shl 12) or (rA shl 8) or 0x07
                    out.write(word and 0xff)
                    out.write((word shr 8) and 0xff)
                }
                else -> {
                    if (mnemonic.startsWith("op_") || mnemonic.endsWith("-op") || mnemonic.endsWith("-range-op")) {
                        val opsList = operands.split(Regex("\\s+"))
                        for (op in opsList) {
                            val cleanOp = op.trim()
                            if (cleanOp.startsWith("0x") || cleanOp.startsWith("0X")) {
                                val value = cleanOp.substring(2).toInt(16)
                                out.write(value and 0xff)
                                out.write((value shr 8) and 0xff)
                            }
                        }
                    } else {
                        val opsList = line.split(Regex("\\s+"))
                        for (op in opsList) {
                            val cleanOp = op.trim()
                            if (cleanOp.startsWith("0x") || cleanOp.startsWith("0X")) {
                                val value = cleanOp.substring(2).toInt(16)
                                out.write(value and 0xff)
                                out.write((value shr 8) and 0xff)
                            }
                        }
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun extractRegNum(operand: String): Int {
        val clean = operand.replace("v", "").replace("r", "").trim()
        return clean.toIntOrNull() ?: 0
    }

    fun writeDexMethodBytecode(
        bytes: ByteArray,
        dexMethod: DexMethod,
        newBytecode: ByteArray
    ): ByteArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val codeOff = dexMethod.codeOffset
        if (codeOff <= 0 || codeOff >= bytes.size) return bytes

        buffer.position(codeOff + 12)
        val originalInsnsSize = buffer.int
        val originalByteLength = originalInsnsSize * 2

        val newInsnsSize = newBytecode.size / 2

        buffer.position(codeOff + 12)
        buffer.putInt(newInsnsSize)

        buffer.put(newBytecode)

        val remaining = originalByteLength - newBytecode.size
        if (remaining > 0) {
            for (i in 0 until remaining) {
                buffer.put(0x00.toByte())
            }
        }

        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(bytes, 32, bytes.size - 32)
        val sha1 = digest.digest()

        System.arraycopy(sha1, 0, bytes, 12, 20)

        val adler = java.util.zip.Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val checksum = adler.value.toInt()

        bytes[8] = (checksum and 0xFF).toByte()
        bytes[9] = ((checksum shr 8) and 0xFF).toByte()
        bytes[10] = ((checksum shr 16) and 0xFF).toByte()
        bytes[11] = ((checksum shr 24) and 0xFF).toByte()

        return bytes
    }
}


data class ZipEntryInfo(
    val name: String,
    val method: Int,
    val size: Long,
    val crc: Long
)

data class DexString(
    val index: Int,
    val offset: Int,
    val value: String,
    val byteLength: Int
)

data class DexClass(
    val name: String,
    val typeIdx: Int,
    val stringIndex: Int,
    val stringOffset: Int,
    val byteLength: Int
)

data class DexMethod(
    val methodIdx: Int,
    val name: String,
    val accessFlags: Int,
    val codeOffset: Int,
    val registersSize: Int,
    val insSize: Int,
    val outsSize: Int,
    val insnsSize: Int,
    val instructionsSmali: List<String>,
    val originalInsnBytes: ByteArray
)

data class ApkEntry(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)
