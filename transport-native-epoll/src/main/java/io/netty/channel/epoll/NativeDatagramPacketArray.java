/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.Limits;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.netty.channel.unix.Limits.UIO_MAX_IOV;
import static io.netty.channel.unix.NativeInetAddress.copyIpv4MappedIpv6Address;

/**
 * Support <a href="http://linux.die.net/man/2/sendmmsg">sendmmsg(...)</a> on linux with GLIBC 2.14+
 */
final class NativeDatagramPacketArray {

    // Use UIO_MAX_IOV as this is the maximum number we can write with one sendmmsg(...) call.
    private final NativeDatagramPacket[] packets = new NativeDatagramPacket[UIO_MAX_IOV];

    // We share one IovArray for all NativeDatagramPackets to reduce memory overhead. This will allow us to write
    // up to IOV_MAX iovec across all messages in one sendmmsg(...) call.
    private final IovArray iovArray = new IovArray();

    // temporary array to copy the ipv4 part of ipv6-mapped-ipv4 addresses and then create a Inet4Address out of it.
    private final byte[] ipv4Bytes = new byte[4];
    private final MyMessageProcessor processor = new MyMessageProcessor();

    private int count;

    NativeDatagramPacketArray() {
        for (int i = 0; i < packets.length; i++) {
            packets[i] = new NativeDatagramPacket();
        }
    }

    boolean addWritable(ByteBuf buf, int index, int len) {
        return add0(buf, index, len, null);
    }

    private boolean add0(ByteBuf buf, int index, int len, InetSocketAddress recipient) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per
            // recvmmsg(...) / sendmmsg(...) call, we will try again later.
            return false;
        }
        if (len == 0) {
            return true;
        }
        int offset = iovArray.count();
        if (offset == Limits.IOV_MAX || !iovArray.add(buf, index, len)) {
            // Not enough space to hold the whole content, we will try again later.
            return false;
        }
        NativeDatagramPacket p = packets[count];
        p.init(iovArray.memoryAddress(offset), iovArray.count() - offset, recipient);

        count++;
        return true;
    }

    void add(ChannelOutboundBuffer buffer, boolean connected) throws Exception {
        processor.connected = connected;
        buffer.forEachFlushedMessage(processor);
    }

    /**
     * Returns the count
     */
    int count() {
        return count;
    }

    /**
     * Returns an array with {@link #count()} {@link NativeDatagramPacket}s filled.
     */
    NativeDatagramPacket[] packets() {
        return packets;
    }

    void clear() {
        this.count = 0;
        this.iovArray.clear();
    }

    void release() {
        iovArray.release();
    }

    private final class MyMessageProcessor implements MessageProcessor {
        private boolean connected;

        @Override
        public boolean processMessage(Object msg) {
            if (msg instanceof DatagramPacket) {
                DatagramPacket packet = (DatagramPacket) msg;
                ByteBuf buf = packet.content();
                return add0(buf, buf.readerIndex(), buf.readableBytes(), packet.recipient());
            }
            if (msg instanceof ByteBuf && connected) {
                ByteBuf buf = (ByteBuf) msg;
                return add0(buf, buf.readerIndex(), buf.readableBytes(), null);
            }
            return false;
        }
    }

    /**
     * Used to pass needed data to JNI.
     */
    @SuppressWarnings("unused")
    final class NativeDatagramPacket {

        // This is the actual struct iovec*
        private long memoryAddress;
        private int count;

        private final byte[] addr = new byte[16];

        private int addrLen;
        private int scopeId;
        private int port;

        private void init(long memoryAddress, int count, InetSocketAddress recipient) {
            this.memoryAddress = memoryAddress;
            this.count = count;

            if (recipient == null) {
                this.scopeId = 0;
                this.port = 0;
                this.addrLen = 0;
            } else {
                InetAddress address = recipient.getAddress();
                if (address instanceof Inet6Address) {
                    System.arraycopy(address.getAddress(), 0, addr, 0, addr.length);
                    scopeId = ((Inet6Address) address).getScopeId();
                } else {
                    copyIpv4MappedIpv6Address(address.getAddress(), addr);
                    scopeId = 0;
                }
                addrLen = addr.length;
                port = recipient.getPort();
            }
        }

        DatagramPacket newDatagramPacket(ByteBuf buffer, InetSocketAddress localAddress) throws UnknownHostException {
            final InetAddress address;
            if (addrLen == ipv4Bytes.length) {
                System.arraycopy(addr, 0, ipv4Bytes, 0, addrLen);
                address = InetAddress.getByAddress(ipv4Bytes);
            } else {
                address = Inet6Address.getByAddress(null, addr, scopeId);
            }
            return new DatagramPacket(buffer.writerIndex(count),
                    localAddress, new InetSocketAddress(address, port));
        }
    }
}
