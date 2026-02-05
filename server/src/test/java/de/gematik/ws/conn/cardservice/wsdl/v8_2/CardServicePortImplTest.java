
package de.gematik.ws.conn.cardservice.wsdl.v8_2;
import de.servicehealth.popp.model.StandardScenarioMessage;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import static org.junit.jupiter.api.Assertions.*;




class CardServicePortImplTest {

    @Test
    void parseStandardScenarioMessage_validJson_returnsObject() {
        String json = "{\"message\": {"
                + "\"clientSessionId\":\"abc-123\","
                + "\"steps\":[{\"commandApdu\":\"00a4040c\",\"expectedStatusWords\":[\"9000\"]}]"
                + "}}";
        InputStream inputStream = new ByteArrayInputStream(json.getBytes());
        CardServicePortImpl impl = new CardServicePortImpl();

        StandardScenarioMessage result = impl.parseStandardScenarioMessage(inputStream);

        assertNotNull(result);
        assertEquals("abc-123", result.getClientSessionId());
        assertNotNull(result.getSteps());
        assertEquals(1, result.getSteps().size());
        assertEquals("00a4040c", result.getSteps().get(0).getCommandApdu());
        assertEquals("9000", result.getSteps().get(0).getExpectedStatusWords().get(0));
    }

    @Test
    void parseStandardScenarioMessage_invalidJson_throwsException() {
        String invalidJson = "{invalid json}";
        InputStream inputStream = new ByteArrayInputStream(invalidJson.getBytes());
        CardServicePortImpl impl = new CardServicePortImpl();

        assertThrows(Exception.class, () -> impl.parseStandardScenarioMessage(inputStream));
    }

    @Test
    void parseStandardScenarioMessage_emptyInput_returnsNullOrThrows() {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        CardServicePortImpl impl = new CardServicePortImpl();

        assertThrows(Exception.class, () -> impl.parseStandardScenarioMessage(inputStream));
    }
}