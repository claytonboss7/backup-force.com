/*
 * Copyright (c) 2013 Andrey Gavrikov.
 * this file is part of Backup-force.com application
 * https://github.com/neowit/backup-force.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.neowit.apex.backup

import junit.framework._
import Assert._
import java.io.FileNotFoundException
import java.util.Properties

object ConfigTest {
    def suite: Test = {
        val suite = new TestSuite(classOf[ConfigTest])
        suite
    }

    def main(args : Array[String]) {
        junit.textui.TestRunner.run(suite)
    }

}
class ConfigTest extends TestCase("Config") {
    def isUnix = {
        val os = System.getProperty("os.name").toLowerCase
        os.contains("nux") || os.contains("mac")
    }
    val FAIL = false

    def testNoCommandParams() {
        try {
            Config.load(List())
        } catch {
            case ex: InvalidCommandLineException => println("OK")
            case ex: Throwable => assert(FAIL, "Expected InvalidCommandLineException for empty command line. " + ex)
        }
    }
    def testWrongParam() {
        try {
            Config.load(List("--incorrect param"))
        } catch {
            case ex: InvalidCommandLineException => println("OK")
            case ex: Throwable => assert(FAIL, "Expected InvalidCommandLineException for empty command line." + ex)
        }
    }
    def testNoConfigParam() {
        try {
            //Config.load(List("--config", "/some/path", "--sf.username", "aaa@bb.cc"))
            Config.load(List("--sf.username", "aaa@bb.cc"))
            Config.getProperty("password")
        } catch {
            case ex: NoSuchElementException => println("OK")
            case ex: Throwable => assert(FAIL, "Expected NoSuchElementException for missing config parameter. " + ex)
        }
    }
    def testMissingConfigFile() {
        try {
            Config.load(List("--config", "/some/path"))
        } catch {
            case ex: FileNotFoundException => println("OK")
            case ex: Throwable => assert(FAIL, "Expected FileNotFoundException for missing config parameter. " + ex)
        }
    }

    def testCommandLineKeyWithoutValue() {
        try {
            Config.load(List("--config"))
        } catch {
            case ex: InvalidCommandLineException => println("OK")
            case ex: Throwable => assert(FAIL, "Expected InvalidCommandLineException for missing value of config parameter. " + ex)
        }
    }
    def testParameterOverride() {
        //Config.load(List("--config", "/some/path"))
        //Config.mainProps.setProperty("prop1", "value1")
    }
    def testShellEvaluation() {
        if (isUnix) {
            val s1 = "nothing to evaluate"
            assertEquals(s1, Config.evalShellCommands(s1))

            val s2 = "`broken string"
            assertEquals(s2, Config.evalShellCommands(s2))

            val s3 = "`echo abcd`"
            assertEquals("abcd", Config.evalShellCommands(s3))

            val s4 = """`echo abcd`"""
            assertEquals("abcd", Config.evalShellCommands(s4))

            val s5 = """some text`echo abcd`"""
            assertEquals("some textabcd", Config.evalShellCommands(s5))

            val s6 = """text before`echo abcd`text after"""
            assertEquals("text beforeabcdtext after", Config.evalShellCommands(s6))

            val s7 = """text before`echo abcd`text after`echo efgh`"""
            assertEquals("text beforeabcdtext afterefgh", Config.evalShellCommands(s7))

            //val s8 = """before`date +%Y%m%d-%H:%m`after"""
            //assertEquals("before2013after", Config.evalShellCommands(s8))
        }

    }

}
