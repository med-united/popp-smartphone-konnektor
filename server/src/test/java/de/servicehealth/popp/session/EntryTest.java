package de.servicehealth.popp.session;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import jakarta.json.JsonObject;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class EntryTest {
    @Test
    void testSetRegisterEgkPayload_parsesCertificateAndSetsCardInfoType() throws Exception {
        // Arrange
        Entry entry = new Entry("CN=test", Mockito.mock(Session.class));
        JsonObject payload = Mockito.mock(JsonObject.class);

        // Use a real X509Certificate for testing
        Mockito.when(payload.getString("x509AuthECC")).thenReturn("MIIDbjCCAxSgAwIBAgIHAv8CpbCrEjAKBggqhkjOPQQDAjCBljELMAkGA1UEBhMCREUxHzAdBgNVBAoMFmdlbWF0aWsgR21iSCBOT1QtVkFMSUQxRTBDBgNVBAsMPEVsZWt0cm9uaXNjaGUgR2VzdW5kaGVpdHNrYXJ0ZS1DQSBkZXIgVGVsZW1hdGlraW5mcmFzdHJ1a3R1cjEfMB0GA1UEAwwWR0VNLkVHSy1DQTUxIFRFU1QtT05MWTAeFw0yNDA0MDIwMDAwMDBaFw0yOTA0MDEyMzU5NTlaMIHtMQswCQYDVQQGEwJERTEdMBsGA1UECgwUVGVzdCBHS1YtU1ZOT1QtVkFMSUQxEjAQBgNVBAsMCTEwOTUwMDk2OTETMBEGA1UECwwKWDExMDU2Nzk1NTETMBEGA1UEBAwKVGFuZ2Vyw7BhbDE8MDoGA1UEKgwzQW5uaWthIFZlcmEgTWVsaXNzYSBCcnVuaGlsZCBBcGhyb2RpdGUgRnJlaWZyYXUgdm9uMUMwQQYDVQQDDDpBbm5pa2EgVmVyYSBNZWxpc3NhIEIuIEEuIEZyZWlmcmF1IHZvbiBUYW5nZXLDsGFsVEVTVC1PTkxZMFowFAYHKoZIzj0CAQYJKyQDAwIIAQEHA0IABI1CINDvdCsVmzkRK7Uj/oulU/5CK/wWwI0oS6NIS2+9k/KFUKaVHQmmUKbDKWs5aMzlBcR/d0rnwIGauoLwZvijgfIwge8wOwYIKwYBBQUHAQEELzAtMCsGCCsGAQUFBzABhh9odHRwOi8vZWhjYS5nZW1hdGlrLmRlL2VjYy1vY3NwMCAGA1UdIAQZMBcwCgYIKoIUAEwEgSMwCQYHKoIUAEwERjAwBgUrJAgDAwQnMCUwIzAhMB8wHTAQDA5WZXJzaWNoZXJ0ZS8tcjAJBgcqghQATAQxMA4GA1UdDwEB/wQEAwIHgDAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFHTp+RSD4QvmEfYqrJfs2a+ZQ8HwMB0GA1UdDgQWBBRdbQO99+E/N/3QduuuQPwWMYyqfzAKBggqhkjOPQQDAgNIADBFAiEAqIrpdN2yN7G9zGzLV99zgopp69jbjsp+7hNSUkoPJhICIFWxIbPxNSv6ksUeRJ7sgdse1KDdyeFKhHQzmMyQtQ8Y");
        Mockito.when(payload.getString("cardSessionId")).thenReturn("session123");

        // Act
        entry.setRegisterEgkPayload(payload);

        // Assert
        X509Certificate cert = entry.getX509AuthECC();
        assertNotNull(cert);
        CardInfoType cardInfo = entry.getCardInfoType();
        assertNotNull(cardInfo);
        assertEquals("session123", cardInfo.getCardHandle());
        assertEquals(cert.getSerialNumber().toString(), cardInfo.getIccsn());
        // Additional asserts for KVNR, CardType, etc. can be added
        assertEquals("X110567955", cardInfo.getKvnr());
        assertEquals("Annika Vera Melissa B. A. Freifrau von TangerðalTEST-ONLY", cardInfo.getCardHolderName());
    }

    
}
