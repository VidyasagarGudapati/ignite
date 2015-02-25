/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.internal.processors.affinity.*;
import org.jetbrains.annotations.*;

/**
 * Factory for cache entries.
 */
public interface GridCacheMapEntryFactory<K, V> {
    /**
     * @param ctx Cache registry.
     * @param topVer Topology version.
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     * @param hdrId Header id.
     * @return New cache entry.
     */
    public GridCacheMapEntry<K, V> create(GridCacheContext<K, V> ctx, AffinityTopologyVersion topVer, K key, int hash,
        V val, @Nullable GridCacheMapEntry<K, V> next, long ttl, int hdrId);
}
