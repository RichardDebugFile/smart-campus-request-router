package ec.udla.integracion.campus.model;

/**
 * Tipos de solicitud reconocidos por el enrutador y la cola destino asociada a
 * cada uno.
 *
 * Mantener el mapeo tipo -> cola en un unico lugar evita "strings magicos"
 * repartidos por la ruta y facilita extender la solucion: agregar un nuevo tipo
 * (por ejemplo {@code SCHOLARSHIP}) se reduce a anadir una constante aqui y su
 * rama en el Content-Based Router.
 */
public enum RequestType {

    ADMISSION("campus.admissions.queue"),
    PAYMENT("campus.payments.queue"),
    SUPPORT("campus.support.queue"),
    ACADEMIC("campus.academic.queue");

    private final String queue;

    RequestType(String queue) {
        this.queue = queue;
    }

    /** Cola destino canonica para este tipo de solicitud. */
    public String queue() {
        return queue;
    }

    /**
     * Resuelve el tipo a partir del valor recibido. Devuelve {@code null} cuando
     * el valor no corresponde a ningun tipo reconocido, de modo que la ruta lo
     * derive a revision manual.
     */
    public static RequestType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (RequestType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
