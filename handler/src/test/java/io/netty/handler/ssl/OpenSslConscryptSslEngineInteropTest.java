/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.ssl;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.SSLEngine;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.netty.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class OpenSslConscryptSslEngineInteropTest extends ConscryptSslEngineTest {

    @Parameterized.Parameters(name = "{index}: bufferType = {0}, combo = {1}, delegate = {2}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (BufferType type: BufferType.values()) {
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), false });
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), true });
        }
        return params;
    }

    public OpenSslConscryptSslEngineInteropTest(BufferType type, ProtocolCipherCombo combo, boolean delegate) {
        super(type, combo, delegate);
    }

    @BeforeClass
    public static void checkOpenssl() {
        assumeTrue(OpenSsl.isAvailable());
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.OPENSSL;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.JDK;
    }

    @Override
    protected Provider clientSslContextProvider() {
        return null;
    }

    @Override
    @Test
    @Ignore("TODO: Make this work with Conscrypt")
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() {
        super.testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth();
    }

    @Override
    @Test
    @Ignore("TODO: Make this work with Conscrypt")
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() {
        super.testMutualAuthValidClientCertChainTooLongFailRequireClientAuth();
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidClientException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidClientException(cause) || causedBySSLException(cause);
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth();
    }

    @Override
    @Test
    public void testSessionAfterHandshakeKeyManagerFactoryMutualAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionAfterHandshakeKeyManagerFactoryMutualAuth();
    }

    @Override
    @Test
    public void testSupportedSignatureAlgorithms() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSupportedSignatureAlgorithms();
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidServerException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidServerException(cause) || causedBySSLException(cause);
    }

    @Override
    protected SSLEngine wrapEngine(SSLEngine engine) {
        return Java8SslTestUtils.wrapSSLEngineForTesting(engine);
    }
}
