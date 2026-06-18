package ec.udla.integracion.campus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de la aplicacion Spring Boot.
 * Inicia el contexto de Spring y, junto con el main-run-controller de Camel,
 * mantiene en ejecucion las rutas de integracion definidas con Apache Camel.
 */
@SpringBootApplication
public class SmartCampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCampusApplication.class, args);
    }
}
