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

import com.sforce.soap.partner.DescribeGlobalSObjectResult
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration

object BackupRunner {

    type OptionMap = Map[String, Any]

    //some types can not be queried en masse, so just exclude those
    val EXCLUDED_TYPES = Set("") //Set("ContentDocumentLink", "Vote")

    def isAcceptable(sobj: DescribeGlobalSObjectResult):Boolean ={
        sobj.isCreateable && sobj.isQueryable && !EXCLUDED_TYPES.contains(sobj.getName)
    }

    def main(args: Array[String]) {
        if (args.isEmpty) {
            new ConfigGenerator
        } else {
            try {
                Config.load(args.toList)
                run()
            } catch {
                case ex: InvalidCommandLineException => Config.help()
                case ex: MissingRequiredConfigParameterException => println(ex.getMessage)
            } finally {
                Config.lastQueryPropsActor ! "exit"
            }
        }
    }

    def run() {

        val config = new com.sforce.ws.ConnectorConfig()
        config.setUsername(Config.username)
        config.setPassword(Config.password)

        val endpoint = Config.soapEndpoint
        if (null != endpoint)
            config.setAuthEndpoint(endpoint)

        config.setCompression(true)

        val proxyHost = Config.getProperty("http.proxyHost")
        val proxyPort = Config.getProperty("http.proxyPort")
        if (None != proxyHost && None != proxyPort)
            config.setProxy(proxyHost.get, proxyPort.get.toInt)

        val proxyUsername = Config.getProperty("http.proxyUsername")
        if (None != proxyUsername )
            config.setProxyUsername(proxyUsername.get)

        val proxyPassword = Config.getProperty("http.proxyPassword")
        if (None != proxyPassword )
            config.setProxyPassword(proxyPassword.get)

        val ntlmDomain = Config.getProperty("http.ntlmDomain")
        if (None != ntlmDomain )
            config.setNtlmDomain(ntlmDomain.get)

        val connectionTimeoutSecs = Config.getProperty("http.connectionTimeoutSecs")
        if (None != connectionTimeoutSecs )
            config.setConnectionTimeout(connectionTimeoutSecs.get.toInt * 1000)

        val readTimeoutSecs = Config.getProperty("http.readTimeoutSecs")
        if (None != readTimeoutSecs )
            config.setReadTimeout(readTimeoutSecs.get.toInt * 1000)

        val connection = com.sforce.soap.partner.Connector.newConnection(config)
        println("Auth EndPoint: "+config.getAuthEndpoint)
        println("Service EndPoint: "+config.getServiceEndpoint)
        println("Username: "+config.getUsername)
        //println("SessionId: "+config.getSessionId)

        //list of objects that have custom SOQL query defined
        val customSoqlObjects = Config.objectsWithCustomSoql

        val objListProperty = Config.backupObjects match {
            case Some("*") => Set("*")
            case Some(x) => x.replaceAll(" ", "").split(",").toSet[String]
            case None => Set()
        }
        val mainSObjectsSet =
            if (Set("*") != objListProperty)
                objListProperty
            else
                connection.describeGlobal().getSobjects.filter(sobj => isAcceptable(sobj)).map(sobj => sobj.getName).toSet

        val allSobjectSet = mainSObjectsSet ++ customSoqlObjects

        require(!allSobjectSet.isEmpty, "config file contains no objects to backup")
        def runAllProcesses() {
            if (!allSobjectSet.isEmpty) {
                Config.HookGlobalBefore.execute()
            }
            if (!Config.useBulkApi) {
                for (objApiName <- allSobjectSet) {
                    val backuper = new BackupSObject(connection, objApiName)
                    backuper.run()
                }
            } else {
                def callSequentially(objApiNames: List[String], processes: List[Future[(OperationMode, Boolean)]]): List[Future[(OperationMode, Boolean)]] = {
                    objApiNames match {
                        case objApiName :: tail =>
                            val backuper = new BackupSObject(connection, objApiName)
                            val f = backuper.run()
                            callSequentially(tail, f :: processes)

                        case _ => processes
                    }

                }
                val futureList = callSequentially(allSobjectSet.toList, List[Future[(OperationMode, Boolean)]]() )

                import scala.concurrent.ExecutionContext.Implicits.global
                Await.result(Future.sequence(futureList), Duration.Inf)
            }
            if (!allSobjectSet.isEmpty) {
                Config.HookGlobalAfter.execute()
            }
        }
        Benchmark("useBulkApi = " + Config.useBulkApi) {
            runAllProcesses()
        }

    }

}

/*
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import scala.concurrent.duration.Duration

 */
