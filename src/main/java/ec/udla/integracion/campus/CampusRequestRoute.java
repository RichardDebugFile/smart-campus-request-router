package ec.udla.integracion.campus;

import ec.udla.integracion.campus.model.RequestType;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Ruta de integracion de Apache Camel. Combina dos patrones:
 *
 * <ul>
 *   <li><b>Message Translator</b>: {@code .process(translator)} convierte el
 *       mensaje externo al modelo canonico interno.</li>
 *   <li><b>Content-Based Router</b>: el bloque {@code choice/when/otherwise}
 *       decide la cola destino segun la propiedad {@code requestType}.</li>
 * </ul>
 *
 * El productor solo conoce la cola de entrada; toda la logica de enrutamiento
 * vive aqui, lo que desacopla a los emisores de los sistemas consumidores.
 */
@Component
public class CampusRequestRoute extends RouteBuilder {

    private final CanonicalRequestTranslator translator;

    public CampusRequestRoute(CanonicalRequestTranslator translator) {
        this.translator = translator;
    }

    @Override
    public void configure() {
        from(CampusChannels.INBOUND_ENDPOINT)
            .routeId("smart-campus-request-router")
            .log("Mensaje recibido desde " + CampusChannels.INBOUND_QUEUE + ": ${body}")

            // --- Patron Message Translator ---
            .process(translator)
            .log("Mensaje transformado a formato canonico: ${body}")
            .log("Tipo de solicitud detectado: ${exchangeProperty." + CampusChannels.TYPE_PROPERTY + "}")

            // --- Patron Content-Based Router ---
            .choice()
                .when(routedAs(RequestType.ADMISSION))
                    .log("Enrutando solicitud de admision")
                    .to(CampusChannels.to(RequestType.ADMISSION.queue()))
                .when(routedAs(RequestType.PAYMENT))
                    .log("Enrutando solicitud de pago")
                    .to(CampusChannels.to(RequestType.PAYMENT.queue()))
                .when(routedAs(RequestType.SUPPORT))
                    .log("Enrutando solicitud de soporte")
                    .to(CampusChannels.to(RequestType.SUPPORT.queue()))
                .when(routedAs(RequestType.ACADEMIC))
                    .log("Enrutando solicitud academica")
                    .to(CampusChannels.to(RequestType.ACADEMIC.queue()))
                .otherwise()
                    .log("Solicitud no reconocida o invalida. Enviando a revision manual")
                    .to(CampusChannels.to(CampusChannels.MANUAL_REVIEW_QUEUE))
            .end();
    }

    /** Predicado: el tipo detectado coincide con el tipo de solicitud dado. */
    private org.apache.camel.Predicate routedAs(RequestType type) {
        return exchangeProperty(CampusChannels.TYPE_PROPERTY).isEqualTo(type.name());
    }
}
