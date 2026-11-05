package com.twinndroid.engine

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TamperScanTest {
    @get:Rule val temp = TemporaryFolder()

    private fun apk(vararg entries: String): File {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { zip.putNextEntry(ZipEntry(it)); zip.closeEntry() }
        }
        return file
    }

    @Test
    fun findsShieldFromItsIntegrityFiles() {
        assertEquals(TamperRisk.SHIELD, TamperScan.scan(listOf(apk("assets/libg.so.text.ecc"))).risk)
    }

    @Test
    fun findsSupercellGamesWithoutIntegrityFiles() {
        val base = apk("classes.dex", "assets/khbieklakjmh.dat")
        val split = apk("lib/arm64-v8a/libsupercell_clashofclans.so")
        assertEquals(TamperRisk.SHIELD, TamperScan.scan(listOf(base, split)).risk)
    }

    @Test
    fun leavesOrdinaryAppsAlone() {
        assertEquals(TamperRisk.NONE, TamperScan.scan(listOf(apk("classes.dex", "lib/arm64-v8a/libapp.so"))).risk)
    }
}
