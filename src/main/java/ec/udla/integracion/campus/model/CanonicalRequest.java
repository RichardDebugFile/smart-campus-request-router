package ec.udla.integracion.campus.model;

/**
 * Modelo canonico interno (Canonical Data Model) de una solicitud estudiantil.
 *
 * Es el formato comun hacia el que se traduce cualquier mensaje externo antes de
 * ser enrutado. Al estar definido como {@code record}, Jackson serializa sus
 * componentes en el mismo orden de declaracion, produciendo exactamente el JSON
 * canonico esperado por el taller:
 *
 * <pre>
 * {
 *   "requestId": "REQ-1001",
 *   "student": { "fullName": "Ana Perez", "document": "1712345678" },
 *   "type": "ADMISSION",
 *   "sourceChannel": "web",
 *   "createdAt": "2026-06-10T10:30:00"
 * }
 * </pre>
 */
public record CanonicalRequest(
        String requestId,
        Student student,
        String type,
        String sourceChannel,
        String createdAt) {

    /** Datos del estudiante dentro del modelo canonico. */
    public record Student(String fullName, String document) {
    }
}
