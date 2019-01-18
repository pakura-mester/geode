/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.internal.net;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;

import org.junit.Test;

import org.apache.geode.distributed.internal.DMStats;

public class BuffersTest {

  @Test
  public void expandBuffer() throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(256);
    buffer.clear();
    for (int i = 0; i < 256; i++) {
      byte b = (byte) (i & 0xff);
      buffer.put(b);
    }
    createAndVerifyNewWriteBuffer(buffer, false);

    createAndVerifyNewWriteBuffer(buffer, true);


    createAndVerifyNewReadBuffer(buffer, false);

    createAndVerifyNewReadBuffer(buffer, true);


  }

  private void createAndVerifyNewWriteBuffer(ByteBuffer buffer, boolean useDirectBuffer) {
    ByteBuffer newBuffer =
        Buffers.expandWriteBufferIfNeeded(Buffers.BufferType.UNTRACKED, buffer, 500,
            mock(DMStats.class));
    assertEquals(buffer.position(), newBuffer.position());
    assertEquals(500, newBuffer.capacity());
    newBuffer.flip();
    for (int i = 0; i < 256; i++) {
      byte expected = (byte) (i & 0xff);
      byte actual = (byte) (newBuffer.get() & 0xff);
      assertEquals(expected, actual);
    }
  }

  private void createAndVerifyNewReadBuffer(ByteBuffer buffer, boolean useDirectBuffer) {
    ByteBuffer newBuffer =
        Buffers.expandReadBufferIfNeeded(Buffers.BufferType.UNTRACKED, buffer, 500,
            mock(DMStats.class));
    assertEquals(0, newBuffer.position());
    assertEquals(500, newBuffer.capacity());
    for (int i = 0; i < 256; i++) {
      byte expected = (byte) (i & 0xff);
      byte actual = (byte) (newBuffer.get() & 0xff);
      assertEquals(expected, actual);
    }
  }
}