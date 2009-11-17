/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ode.bpel.engine.migration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.dao.BpelDAOConnection;
import org.apache.ode.bpel.dao.ProcessDAO;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.IMAManager;
import org.apache.ode.bpel.engine.OutstandingRequestManager;
import org.apache.ode.bpel.engine.IMAManager.Entry;
import org.apache.ode.jacob.vpu.ExecutionQueueImpl;

/**
 * Migrates OutstandingRequestManager to IMAManager
 *
 */
public class OutstandingRequestsMigration implements Migration {
    private static Log __log = LogFactory.getLog(OutstandingRequestsMigration.class);

    public boolean migrate(Set<BpelProcess> registeredProcesses, BpelDAOConnection connection) {
        boolean migrationResult = true;
        for (BpelProcess process : registeredProcesses) {
            ProcessDAO processDao = connection.getProcess(process.getConf().getProcessId());
            Collection<ProcessInstanceDAO> pis = processDao.getActiveInstances();

            for (ProcessInstanceDAO instance : pis) {
                __log.debug("Migrating outstanding requests for for instance " + instance.getInstanceId());
                instance.getExecutionState();

                ExecutionQueueImpl soup = new ExecutionQueueImpl(null);
                soup.setReplacementMap(process.getReplacementMap(processDao.getProcessId()));
                Object data = soup.getGlobalData();
                if (data instanceof OutstandingRequestManager) {
                    OutstandingRequestManager orm = (OutstandingRequestManager) data;

                    Set<Entry> entries = orm.getIMAEntries();
                    IMAManager imaManager = new IMAManager();
                    imaManager.migrateEntries(entries);
                    soup.setGlobalData(imaManager);
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    soup.write(bos);
                    instance.setExecutionState(bos.toByteArray());
                    __log.debug("Migrated outstanding requests for for instance" + instance.getInstanceId());
                } catch (IOException e) {
                    __log.error("", e);
                    migrationResult = false;
                }
            }
        }
        
        return migrationResult;
    }
}
