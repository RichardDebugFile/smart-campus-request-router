package ec.udla.integracion.campus;

/**
 * Nombres de los canales de RabbitMQ y utilidades para construir los endpoints
 * de Camel. Centralizar aqui el exchange, las colas y el formato de las URIs
 * evita duplicar cadenas en la ruta y deja un solo punto de cambio.
 */
public final class CampusChannels {

    private CampusChannels() {
    }

    /** Exchange directo por el que entran y salen todos los mensajes. */
    public static final String EXCHANGE = "campus.exchange";

    /** Cola comun de entrada donde llegan las solicitudes externas. */
    public static final String INBOUND_QUEUE = "campus.requests.in";

    /** Cola de revision manual para mensajes no reconocidos o invalidos. */
    public static final String MANUAL_REVIEW_QUEUE = "campus.manual-review.queue";

    /** Propiedad del Exchange usada como criterio del Content-Based Router. */
    public static final String TYPE_PROPERTY = "requestType";

    /** Marca de tipo para los mensajes que no superan la validacion. */
    public static final String INVALID = "INVALID";

    /** Endpoint consumidor sobre la cola de entrada (no auto-declara recursos). */
    public static final String INBOUND_ENDPOINT = "spring-rabbitmq:" + EXCHANGE
            + "?queues=" + INBOUND_QUEUE
            + "&routingKey=" + INBOUND_QUEUE
            + "&autoDeclare=false";

    /** Construye el endpoint productor que publica con la routing key indicada. */
    public static String to(String routingKey) {
        return "spring-rabbitmq:" + EXCHANGE + "?routingKey=" + routingKey;
    }
}
