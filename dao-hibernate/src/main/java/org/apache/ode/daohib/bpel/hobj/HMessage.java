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
package org.apache.ode.daohib.bpel.hobj;


/**
 * Hibernate-managed table for keeping track of messages.
 *
 * @hibernate.class table="BPEL_MESSAGE"
 * @hibernate.query name="SELECT_MESSAGE_IDS_BY_MEX" query="select id from HMessage as m WHERE m.messageExchange = :messageExchange"
 * @hibernate.query name="SELECT_MESSAGE_IDS_BY_INSTANCES" query="select id from HMessage as m WHERE m.messageExchange IN(select e from HMessageExchange e where e.instance in (:instances))"
 */
public class HMessage extends HObject {
    public final static String SELECT_MESSAGE_IDS_BY_MEX = "SELECT_MESSAGE_IDS_BY_MEX";
    public final static String SELECT_MESSAGE_IDS_BY_INSTANCES = "SELECT_MESSAGE_IDS_BY_INSTANCES";

    private String _type;
    private HLargeData _data;
    private HLargeData _header;

    public void setType(String type) {
        _type = type;
    }

    /** @hibernate.property column="TYPE" */
    public String getType() {
        return _type;
    }

    /** @hibernate.many-to-one column="DATA" lazy="false" outer-join="true" foreign-key="none" */
    public HLargeData getMessageData() {
        return _data;
    }

    public void setMessageData(HLargeData data) {
        _data = data;
    }

    /** @hibernate.many-to-one column="HEADER" foreign-key="none" */
    public HLargeData getHeader() {
        return _header;
    }

    public void setHeader(HLargeData header) {
        _header = header;
    }
}
