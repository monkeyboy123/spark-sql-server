/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.server.service

import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.server.{SQLServer, SQLServerConf}

private[server] trait CLI {

  def openSession(userName: String, passwd: String, ipAddress: String): Int

  def closeSession(sessionId: Int): Unit

  def executeStatement(sessionId: Int, statement: String): ExecuteStatementOperation

}

private[server] class SparkSQLCLIService(pgServer: SQLServer, sqlContext: SQLContext)
    extends CompositeService with CLI {

  private var sessionManager: SessionManager = _
  private var operationManager: OperationManager = _

  override def init(sqlContext: SQLContext) {
    if (sqlContext.conf.contains("spark.yarn.keytab")) {
      // If you have enabled Kerberos, the following 2 params must be set
      val principalName = sqlContext.conf.getConfString("spark.yarn.keytab")
      val keytabFilename = sqlContext.conf.getConfString("spark.yarn.principal")
      SparkHadoopUtil.get.loginUserFromKeytab(principalName, keytabFilename)
    }

    sessionManager = new SessionManager(pgServer, sqlContext)
    addService(sessionManager)
    operationManager = new OperationManager(pgServer, sqlContext)
    addService(operationManager)
    super.init(sqlContext)
  }

  override def openSession(userName: String, passwd: String, ipAddress: String): Int = {
    sessionManager.openSession(userName, passwd, ipAddress)
  }

  override def closeSession(sessionId: Int): Unit = {
    sessionManager.closeSession(sessionId)
  }

  override def executeStatement(sessionId: Int, statement: String): ExecuteStatementOperation = {
    operationManager.newExecuteStatementOperation(
      sessionManager.getSession(sessionId),
      sessionId,
      statement)
  }
}
