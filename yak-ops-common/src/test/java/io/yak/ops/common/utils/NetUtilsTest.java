package io.yak.ops.common.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NetUtilsTest {

    @Test
    public void testGetAddr() {
        Assertions.assertEquals(NetUtils.getAddr(NetUtils.getHost(), 5678), NetUtils.getAddr(5678));
        Assertions.assertEquals("127.0.0.1:5678", NetUtils.getAddr("127.0.0.1", 5678));
        Assertions.assertEquals("localhost:1234", NetUtils.getAddr("localhost", 1234));
        Assertions.assertEquals("[fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831]:1234",
                NetUtils.getAddr("fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831", 1234));
        Assertions.assertEquals("[fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831]:1234",
                NetUtils.getAddr("[fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831]", 1234));
    }

    @Test
    public void testGetHostInNonKubernetesMode() {
        InetAddress address = mock(InetAddress.class);
        when(address.getCanonicalHostName())
                .thenReturn("yak-ops-worker-0.yak-ops-worker-headless.default.svc.cluster.local");
        when(address.getHostName()).thenReturn("yak-ops-worker-0");
        when(address.getHostAddress()).thenReturn("172.17.0.15");
        Assertions.assertEquals("172.17.0.15", NetUtils.getHost(address));
    }

    @Test
    public void testGetHostInNonKubernetesModeShouldStripIpv6Scope() throws UnknownHostException {
        Inet6Address address = Inet6Address.getByAddress(null,
                InetAddress.getByName("fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831").getAddress(), 2);
        Assertions.assertEquals("fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831", NetUtils.getHost(address));
    }

    @Test
    public void testGetLocalHost() {
        Assertions.assertNotNull(NetUtils.getHost());
    }

    @Test
    public void testIsValidAddress() {
        Assertions.assertFalse(NetUtils.isValidV4Address(null));
        Inet4Address address = mock(Inet4Address.class);
        when(address.isLoopbackAddress()).thenReturn(true);
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("localhost");
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("0.0.0.0");
        when(address.isAnyLocalAddress()).thenReturn(true);
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("127.0.0.1");
        when(address.isLoopbackAddress()).thenReturn(true);
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("1.2.3.4");
        Assertions.assertTrue(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("1.2.3.4:80");
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("256.0.0.1");
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("127.0.0.0.1");
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
        address = mock(Inet4Address.class);
        when(address.getHostAddress()).thenReturn("-1.2.3.4");
        Assertions.assertFalse(NetUtils.isValidV4Address(address));
    }

    @Test
    public void testIsValidV6Address() throws UnknownHostException {
        Assertions.assertFalse(NetUtils.isValidV6Address(null));

        Inet6Address globalAddress = (Inet6Address) InetAddress
                .getByName("fd15:4ba5:5a2b:1008:71bc:dd01:e661:e831");
        Assertions.assertTrue(NetUtils.isValidV6Address(globalAddress));

        Inet6Address scopedGlobalAddress = Inet6Address.getByAddress(null, globalAddress.getAddress(), 2);
        Assertions.assertTrue(NetUtils.isValidV6Address(scopedGlobalAddress));

        Inet6Address linkLocalAddress = (Inet6Address) InetAddress.getByName("fe80::20c:29ff:feac:72e7");
        Assertions.assertFalse(NetUtils.isValidV6Address(linkLocalAddress));

        Inet6Address loopbackAddress = (Inet6Address) InetAddress.getByName("::1");
        Assertions.assertFalse(NetUtils.isValidV6Address(loopbackAddress));
    }

}
