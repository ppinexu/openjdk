/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* @test
 * @run testng ValidateSelectionKeyOps
 * @summary Test that the operations on SelectionKey function as specified.
 */

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.testng.annotations.Test;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static org.testng.Assert.*;

@Test
public class AtomicSelectionKeyOps {

    public void testBaseImplementation() throws Exception {
        SelectionKey testKeyImpl = new SelectionKey() {
            private int ops;

            public SelectableChannel channel() {
                return null;
            }

            public Selector selector() {
                return null;
            }

            public boolean isValid() {
                return true;
            }

            public void cancel() {
            }

            public int interestOps() {
                return ops;
            }

            public SelectionKey interestOps(final int ops) {
                this.ops = ops;
                return this;
            }

            public int readyOps() {
                return 0;
            }
        };

        assertEquals(testKeyImpl.interestOps(), 0);
        testKeyImpl.interestOps(OP_READ);
        assertEquals(testKeyImpl.interestOps(), OP_READ);
        testKeyImpl.interestOpsOr(OP_ACCEPT | OP_WRITE);
        assertEquals(testKeyImpl.interestOps(), OP_READ | OP_WRITE | OP_ACCEPT);
        testKeyImpl.interestOpsAnd(OP_WRITE | OP_CONNECT);
        assertEquals(testKeyImpl.interestOps(), OP_WRITE);
        testKeyImpl.interestOpsOr(OP_CONNECT);
        assertEquals(testKeyImpl.interestOps(), OP_WRITE | OP_CONNECT);
        testKeyImpl.interestOpsAnd(OP_READ);
        assertEquals(testKeyImpl.interestOps(), 0);
    }

    public void testNioImplementation() throws Exception {
        final Selector selector = Selector.open();
        final ConnectionPair pair = new ConnectionPair();
        final SocketChannel channel = pair.channel1();
        channel.configureBlocking(false);
        final SelectionKey selectionKey = channel.register(selector, 0);
        assertEquals(selectionKey.interestOps(), 0);
        selectionKey.interestOps(OP_READ);
        assertEquals(selectionKey.interestOps(), OP_READ);
        try {
            selectionKey.interestOpsOr(OP_ACCEPT | OP_WRITE);
            fail("Expected exception");
        } catch (IllegalArgumentException okay) {}
        selectionKey.interestOpsOr(OP_WRITE);
        assertEquals(selectionKey.interestOps(), OP_READ | OP_WRITE);
        selectionKey.interestOpsAnd(OP_WRITE | OP_CONNECT);
        assertEquals(selectionKey.interestOps(), OP_WRITE);
        selectionKey.interestOpsOr(OP_CONNECT);
        assertEquals(selectionKey.interestOps(), OP_WRITE | OP_CONNECT);
        selectionKey.interestOpsAnd(OP_READ);
        assertEquals(selectionKey.interestOps(), 0);
        pair.close();
    }

    static class ConnectionPair implements Closeable {

        private final SocketChannel sc1;
        private final SocketChannel sc2;

        ConnectionPair() throws IOException {
            InetAddress lb = InetAddress.getLoopbackAddress();
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(lb, 0));
                this.sc1 = SocketChannel.open(ssc.getLocalAddress());
                this.sc2 = ssc.accept();
            }
        }

        SocketChannel channel1() {
            return sc1;
        }

        SocketChannel channel2() {
            return sc2;
        }

        public void close() throws IOException {
            sc1.close();
            sc2.close();
        }
    }
}

