package com.lealtad.controller;

import com.lealtad.dto.PuntosResponse;
import com.lealtad.service.ClientePuntosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de puntos de lealtad de clientes.
 *
 * Expone endpoints para consultar, acumular y redimir puntos,
 * así como filtrar clientes por su nivel de lealtad (BRONZE, SILVER, GOLD).
 *
 * Base path: /api/puntos-lealtad
 */
@Slf4j
@RestController
@RequestMapping("/api/puntos-lealtad")
@RequiredArgsConstructor
public class ClientePuntosController {

    private final ClientePuntosService clientePuntosService;

    /**
     * Verifica que el microservicio de puntos de lealtad esté activo y respondiendo.
     *
     * @return mensaje de estado con código 200 si el servicio está operativo
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "servicio", "puntos-lealtad-service",
                "estado", "activo",
                "mensaje", "Servicio de Puntos de Lealtad está activo ✅"
        ));
    }

    /**
     * Consulta el detalle de puntos de lealtad de un cliente específico.
     *
     * @param clienteId identificador único del cliente (ej: "CLI-001")
     * @return PuntosResponse con los puntos acumulados, nivel de lealtad y datos del cliente
     * @throws com.lealtad.exception.ClienteNoEncontradoException si el clienteId no existe (404)
     */
    @GetMapping("/clientes/{clienteId}")
    public ResponseEntity<PuntosResponse> consultarPuntosDeCliente(@PathVariable String clienteId) {
        log.info("GET /api/puntos-lealtad/clientes/{}", clienteId);
        PuntosResponse response = clientePuntosService.obtenerPuntosPorClienteId(clienteId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista todos los clientes registrados en el programa de puntos de lealtad.
     *
     * @return lista de PuntosResponse con la información de todos los clientes
     */
    @GetMapping("/clientes")
    public ResponseEntity<List<PuntosResponse>> listarTodosLosClientes() {
        log.info("GET /api/puntos-lealtad/clientes");
        List<PuntosResponse> response = clientePuntosService.obtenerTodos();
        return ResponseEntity.ok(response);
    }

    /**
     * Filtra clientes por su nivel de lealtad actual.
     *
     * Niveles válidos:
     * - BRONZE (0-499 puntos)
     * - SILVER (500-999 puntos)
     * - GOLD   (1000+ puntos)
     *
     * @param nivel nivel de lealtad por el cual filtrar (BRONZE, SILVER o GOLD)
     * @return lista de PuntosResponse de los clientes que pertenecen al nivel indicado
     */
    @GetMapping("/niveles/{nivel}/clientes")
    public ResponseEntity<List<PuntosResponse>> listarClientesPorNivel(@PathVariable String nivel) {
        log.info("GET /api/puntos-lealtad/niveles/{}/clientes", nivel);
        List<PuntosResponse> response = clientePuntosService.obtenerClientesPorNivel(nivel);
        return ResponseEntity.ok(response);
    }

    /**
     * Acumula (suma) puntos al saldo de un cliente.
     * Si el nuevo saldo cruza un umbral de nivel, el nivel de lealtad se actualiza automáticamente.
     *
     * Ejemplo: POST /api/puntos-lealtad/clientes/CLI-001/acumular?cantidad=250
     *
     * @param clienteId identificador único del cliente
     * @param cantidad  cantidad de puntos a acumular (debe ser mayor a 0)
     * @return PuntosResponse actualizado con el nuevo saldo y nivel
     * @throws IllegalArgumentException si la cantidad es menor o igual a 0 (400)
     * @throws com.lealtad.exception.ClienteNoEncontradoException si el clienteId no existe (404)
     */
    @PostMapping("/clientes/{clienteId}/acumular")
    public ResponseEntity<PuntosResponse> acumularPuntos(
            @PathVariable String clienteId,
            @RequestParam Integer cantidad) {
        log.info("POST /api/puntos-lealtad/clientes/{}/acumular?cantidad={}", clienteId, cantidad);
        PuntosResponse response = clientePuntosService.sumarPuntos(clienteId, cantidad);
        return ResponseEntity.ok(response);
    }

    /**
     * Redime (resta) puntos del saldo de un cliente.
     * Valida que el cliente tenga suficientes puntos antes de la operación.
     * Si el nuevo saldo cruza un umbral de nivel, el nivel de lealtad se actualiza automáticamente.
     *
     * Ejemplo: POST /api/puntos-lealtad/clientes/CLI-001/redimir?cantidad=100
     *
     * @param clienteId identificador único del cliente
     * @param cantidad  cantidad de puntos a redimir (debe ser mayor a 0 y no exceder el saldo actual)
     * @return PuntosResponse actualizado con el nuevo saldo y nivel
     * @throws IllegalArgumentException si la cantidad es inválida o excede el saldo disponible (400)
     * @throws com.lealtad.exception.ClienteNoEncontradoException si el clienteId no existe (404)
     */
    @PostMapping("/clientes/{clienteId}/redimir")
    public ResponseEntity<PuntosResponse> redimirPuntos(
            @PathVariable String clienteId,
            @RequestParam Integer cantidad) {
        log.info("POST /api/puntos-lealtad/clientes/{}/redimir?cantidad={}", clienteId, cantidad);
        PuntosResponse response = clientePuntosService.restarPuntos(clienteId, cantidad);
        return ResponseEntity.ok(response);
    }
}
