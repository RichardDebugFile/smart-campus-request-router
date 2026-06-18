package ec.udla.integracion.campus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.udla.integracion.campus.model.CanonicalRequest;
import ec.udla.integracion.campus.model.CanonicalRequest.Student;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementacion del patron <b>Message Translator</b>.
 *
 * Convierte el mensaje externo recibido en la cola de entrada
 * ({@code campus.requests.in}) al modelo canonico interno de la institucion
 * ({@link CanonicalRequest}).
 *
 * Tambien aplica una validacion basica de campos obligatorios. Si el mensaje no
 * los contiene todos, se marca como {@code INVALID}, se conserva el original con
 * el detalle de los campos ausentes y la ruta lo deriva a la cola de revision
 * manual.
 */
@Component
public class CanonicalRequestTranslator implements Processor {

    /** Campos obligatorios del mensaje externo. */
    private static final String[] REQUIRED_FIELDS = {
            "request_id", "student_name", "student_document",
            "request_type", "channel", "created_at"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        JsonNode external = objectMapper.readTree(exchange.getIn().getBody(String.class));

        List<String> missing = findMissingFields(external);
        if (!missing.isEmpty()) {
            rejectAsInvalid(exchange, external, missing);
            return;
        }

        translateToCanonical(exchange, external);
    }

    /** Traduce el mensaje externo al modelo canonico y publica el tipo detectado. */
    private void translateToCanonical(Exchange exchange, JsonNode external) throws Exception {
        String type = text(external, "request_type");

        CanonicalRequest canonical = new CanonicalRequest(
                text(external, "request_id"),
                new Student(text(external, "student_name"), text(external, "student_document")),
                type,
                text(external, "channel"),
                text(external, "created_at"));

        exchange.setProperty(CampusChannels.TYPE_PROPERTY, type);
        exchange.getIn().setBody(objectMapper.writeValueAsString(canonical));
    }

    /** Marca el mensaje como invalido conservando el original y el motivo. */
    private void rejectAsInvalid(Exchange exchange, JsonNode external, List<String> missing) throws Exception {
        RejectedRequest rejected = new RejectedRequest(
                CampusChannels.INVALID,
                "Mensaje incompleto. Campos obligatorios ausentes: " + String.join(", ", missing),
                external);

        exchange.setProperty(CampusChannels.TYPE_PROPERTY, CampusChannels.INVALID);
        exchange.getIn().setBody(objectMapper.writeValueAsString(rejected));
    }

    /** Devuelve la lista de campos obligatorios ausentes, nulos o vacios. */
    private List<String> findMissingFields(JsonNode json) {
        List<String> missing = new ArrayList<>();
        for (String field : REQUIRED_FIELDS) {
            if (!hasText(json, field)) {
                missing.add(field);
            }
        }
        return missing;
    }

    private String text(JsonNode json, String field) {
        return json.get(field).asText();
    }

    private boolean hasText(JsonNode json, String field) {
        return json.has(field)
                && !json.get(field).isNull()
                && !json.get(field).asText().isBlank();
    }

    /** Estructura de salida para mensajes que no superan la validacion. */
    private record RejectedRequest(String status, String reason, JsonNode originalMessage) {
    }
}
