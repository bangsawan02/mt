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
import com.android.apksig.ApkSigner
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

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

    val DEBUG_KEYSTORE_B64 = "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"

    fun signApkWithEntries(apkFile: File, modifiedEntries: Map<String, ByteArray> = emptyMap()) {
        val tempFile = File(apkFile.parentFile, apkFile.name + ".unsigned")
        try {
            val entriesMeta = java.util.LinkedHashMap<String, ZipEntryInfo>()
            val seenEntries = mutableSetOf<String>()
            
            var fis = java.io.FileInputStream(apkFile)
            var zis = ZipInputStream(fis)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !isSignatureFile(entry.name)) {
                    val name = entry.name
                    seenEntries.add(name)
                    val dataBytes = modifiedEntries[name]
                    
                    if (dataBytes != null) {
                        entriesMeta[name] = ZipEntryInfo(name, entry.method, dataBytes.size.toLong(), calculateCrc32(dataBytes))
                    } else {
                        val crcCalculator = java.util.zip.CRC32()
                        val buf = ByteArray(4096)
                        var totalLen = 0L
                        var len = zis.read(buf)
                        while (len != -1) {
                            crcCalculator.update(buf, 0, len)
                            totalLen += len
                            len = zis.read(buf)
                        }
                        entriesMeta[name] = ZipEntryInfo(name, entry.method, totalLen, crcCalculator.value)
                    }
                }
                entry = zis.nextEntry
            }
            zis.close()
            fis.close()
            
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
            
            // Add newly added modified entries
            for ((mName, mBytes) in modifiedEntries) {
                if (!seenEntries.contains(mName)) {
                    val zEntry = ZipEntry(mName)
                    zos.putNextEntry(zEntry)
                    zos.write(mBytes)
                    zos.closeEntry()
                }
            }
            
            zos.close()
            tempFos.close()

            // Now sign with apksig
            val ksBytes = Base64.decode(DEBUG_KEYSTORE_B64, Base64.DEFAULT)
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(ByteArrayInputStream(ksBytes), "android".toCharArray())
            val key = ks.getKey("androiddebugkey", "android".toCharArray()) as PrivateKey
            val cert = ks.getCertificate("androiddebugkey") as X509Certificate

            val signerConfig = ApkSigner.SignerConfig.Builder("androiddebugkey", key, listOf(cert)).build()

            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(tempFile)
                .setOutputApk(apkFile)
                .build()
            
            apkSigner.sign()
            
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
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
