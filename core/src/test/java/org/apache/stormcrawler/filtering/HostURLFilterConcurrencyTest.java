/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
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

package org.apache.stormcrawler.filtering;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.stormcrawler.Metadata;
import org.apache.stormcrawler.filtering.host.HostURLFilter;
import org.apache.stormcrawler.util.URLUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Verifies that one HostURLFilter instance can be shared by concurrent callers. */
class HostURLFilterConcurrencyTest {

    private static final int ITERATIONS = 100_000;

    @Test
    void sharedInstanceNeverMixesSourceHosts() throws Exception {
        HostURLFilter filter = new HostURLFilter();
        ObjectNode params = new ObjectNode(JsonNodeFactory.instance);
        params.put("ignoreOutsideHost", true);
        params.put("ignoreOutsideDomain", false);
        Map<String, Object> conf = new HashMap<>();
        filter.configure(conf, params);

        URL sourceA = URLUtil.toURL("http://a.example.com/page");
        URL sourceB = URLUtil.toURL("http://b.example.org/page");
        Metadata metadata = new Metadata();
        AtomicLong admittedCrossHost = new AtomicLong();
        AtomicLong droppedSameHost = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);

        Thread a =
                new Thread(
                        () -> {
                            await(start);
                            for (int i = 0; i < ITERATIONS; i++) {
                                if (filter.filter(sourceA, metadata, "http://b.example.org/x")
                                        != null) {
                                    admittedCrossHost.incrementAndGet();
                                }
                            }
                        });

        Thread b =
                new Thread(
                        () -> {
                            await(start);
                            for (int i = 0; i < ITERATIONS; i++) {
                                if (filter.filter(sourceB, metadata, "http://b.example.org/y")
                                        == null) {
                                    droppedSameHost.incrementAndGet();
                                }
                            }
                        });

        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        // Check both race outcomes: admitting a cross-host URL and dropping a same-host URL.
        Assertions.assertEquals(0L, admittedCrossHost.get());
        Assertions.assertEquals(0L, droppedSameHost.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
