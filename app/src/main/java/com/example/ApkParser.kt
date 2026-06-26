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

    private fun isSignatureFile(name: String): Boolean {
        val upperName = name.uppercase()
        if (!upperName.startsWith("META-INF/")) return false
        return upperName.endsWith("/MANIFEST.MF") ||
               upperName.endsWith(".SF") ||
               upperName.endsWith(".RSA") ||
               upperName.endsWith(".DSA") ||
               upperName.endsWith(".EC")
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
            val bytes = apkFile.readBytes()
            val bais = ByteArrayInputStream(bytes)
            val zis = ZipInputStream(bais)
            
            val tempFos = FileOutputStream(tempFile)
            val zos = ZipOutputStream(tempFos)
            
            val entriesData = java.util.LinkedHashMap<String, ByteArray>()
            
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !isSignatureFile(entry.name)) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var len = zis.read(buf)
                    while (len != -1) {
                        out.write(buf, 0, len)
                        len = zis.read(buf)
                    }
                    entriesData[entry.name] = out.toByteArray()
                }
                entry = zis.nextEntry
            }
            zis.close()
            
            // Apply modified entries
            for ((mName, mBytes) in modifiedEntries) {
                entriesData[mName] = mBytes
            }
            
            // Build Manifest
            val manifestSb = StringBuilder()
            manifestSb.append("Manifest-Version: 1.0\r\n")
            manifestSb.append("Created-By: 1.0 (Android)\r\n\r\n")
            
            val md = MessageDigest.getInstance("SHA-256")
            val entryBlocks = java.util.LinkedHashMap<String, ByteArray>()
            
            for ((name, data) in entriesData) {
                md.reset()
                val digest = md.digest(data)
                val base64Digest = Base64.encodeToString(digest, Base64.NO_WRAP)
                
                val blockSb = StringBuilder()
                blockSb.append("Name: $name\r\n")
                blockSb.append("SHA-256-Digest: $base64Digest\r\n\r\n")
                
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
            
            // Generate standard dynamic signature key and certificate
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
            
            // Sign the SF file and wrap as PKCS7 block
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
            
            // Write normal files
            for ((name, data) in entriesData) {
                val zEntry = ZipEntry(name)
                zos.putNextEntry(zEntry)
                zos.write(data)
                zos.closeEntry()
            }
            
            // Write signatures
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
}

data class ApkEntry(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)
