/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package datawave.accumulo.inmemory;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.accumulo.core.client.BatchScanner;
import org.apache.accumulo.core.client.admin.TimeType;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.clientImpl.Namespace;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.metadata.MetadataTable;
import org.apache.accumulo.core.metadata.RootTable;
import org.apache.accumulo.core.replication.ReplicationTable;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.security.NamespacePermission;
import org.apache.accumulo.core.security.SystemPermission;
import org.apache.accumulo.core.security.TablePermission;
import org.apache.accumulo.core.util.tables.TableNameUtil;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;

public class InMemoryAccumulo {
    final Map<String,InMemoryTable> tables = new HashMap<>();
    final Map<String,InMemoryNamespace> namespaces = new HashMap<>();
    final Map<String,String> systemProperties = new HashMap<>();
    Map<String,InMemoryUser> users = new HashMap<>();
    final FileSystem fs;
    final AtomicInteger tableIdCounter = new AtomicInteger(0);
    
    InMemoryAccumulo(FileSystem fs) {
        InMemoryUser root = new InMemoryUser("root", new PasswordToken(new byte[0]), Authorizations.EMPTY);
        root.permissions.add(SystemPermission.SYSTEM);
        users.put(root.name, root);
        namespaces.put(Namespace.DEFAULT.name(), new InMemoryNamespace());
        namespaces.put(Namespace.ACCUMULO.name(), new InMemoryNamespace());
        createTable("root", RootTable.NAME, true, TimeType.LOGICAL);
        createTable("root", MetadataTable.NAME, true, TimeType.LOGICAL);
        createTable("root", ReplicationTable.NAME, true, TimeType.LOGICAL);
        this.fs = fs;
    }
    
    public FileSystem getFileSystem() {
        return fs;
    }
    
    void setProperty(String key, String value) {
        systemProperties.put(key, value);
    }
    
    String removeProperty(String key) {
        return systemProperties.remove(key);
    }
    
    public void addMutation(String table, Mutation m) {
        InMemoryTable t = tables.get(table);
        t.addMutation(m);
    }
    
    public BatchScanner createBatchScanner(String tableName, Authorizations authorizations) {
        return new InMemoryBatchScanner(tables.get(tableName), authorizations);
    }
    
    public void createTable(String username, String tableName, boolean useVersions, TimeType timeType) {
        Map<String,String> opts = Collections.emptyMap();
        createTable(username, tableName, useVersions, timeType, opts);
    }
    
    public void createTable(String username, String tableName, boolean useVersions, TimeType timeType, Map<String,String> properties) {
        String namespace = TableNameUtil.qualify(tableName).getFirst();
        
        if (!namespaceExists(namespace)) {
            return;
        }
        
        InMemoryNamespace n = namespaces.get(namespace);
        InMemoryTable t = new InMemoryTable(n, useVersions, timeType, Integer.toString(tableIdCounter.incrementAndGet()), properties);
        t.userPermissions.put(username, EnumSet.allOf(TablePermission.class));
        t.setNamespaceName(namespace);
        t.setNamespace(n);
        tables.put(tableName, t);
    }
    
    public void createTable(String username, String tableName, TimeType timeType, Map<String,String> properties) {
        String namespace = TableNameUtil.qualify(tableName).getFirst();
        HashMap<String,String> props = new HashMap<>(properties);
        
        if (!namespaceExists(namespace)) {
            return;
        }
        
        InMemoryNamespace n = namespaces.get(namespace);
        InMemoryTable t = new InMemoryTable(n, timeType, Integer.toString(tableIdCounter.incrementAndGet()), props);
        t.userPermissions.put(username, EnumSet.allOf(TablePermission.class));
        t.setNamespaceName(namespace);
        t.setNamespace(n);
        tables.put(tableName, t);
    }
    
    public void createNamespace(String username, String namespace) {
        if (!namespaceExists(namespace)) {
            InMemoryNamespace n = new InMemoryNamespace();
            n.userPermissions.put(username, EnumSet.allOf(NamespacePermission.class));
            namespaces.put(namespace, n);
        }
    }
    
    public void addSplits(String tableName, SortedSet<Text> partitionKeys) {
        tables.get(tableName).addSplits(partitionKeys);
    }
    
    public Collection<Text> getSplits(String tableName) {
        return tables.get(tableName).getSplits();
    }
    
    public void merge(String tableName, Text start, Text end) {
        tables.get(tableName).merge(start, end);
    }
    
    private boolean namespaceExists(String namespace) {
        return namespaces.containsKey(namespace);
    }
}
