package org.example.meeter;

import java.net.*;
import org.junit.jupiter.api.Test;
import org.example.meeter.photos.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PhotoUrlPolicyTests {
    @Test void rejectsPrivateAddressesAlternateIpFormatsAndUnsupportedSchemes() throws Exception {
        for(String url:new String[]{"file:///etc/passwd","ftp://example.org/a.jpg","https://user:password@example.org/a.jpg","http://127.0.0.1/a.jpg","http://2130706433/a.jpg","http://[::1]/a.jpg","http://169.254.169.254/a.jpg","https://example.org:8443/a.jpg"})
            assertThrows(ResponseStatusException.class,()->PhotoUrlPolicy.validate(url),url);
        for(String address:new String[]{"127.0.0.1","10.0.0.1","172.16.1.2","192.168.1.1","100.64.0.1","169.254.169.254","0.0.0.0","224.0.0.1","::1","fe80::1","fc00::1","2001:db8::1","2002:7f00:1::1"})
            assertFalse(PhotoUrlPolicy.isPublic(InetAddress.getByName(address)),address);
        assertTrue(PhotoUrlPolicy.isPublic(InetAddress.getByName("8.8.8.8")));
        assertTrue(PhotoUrlPolicy.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
        assertEquals("https",PhotoUrlPolicy.validate("https://example.org/photo.jpg?version=1").getScheme());
    }
}
