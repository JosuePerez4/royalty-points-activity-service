package com.lealtad.service;

import com.lealtad.dto.ClienteCreadoEvent;
import com.lealtad.dto.PuntosResponse;
import com.lealtad.entity.ClientePuntos;
import com.lealtad.exception.ClienteNoEncontradoException;
import com.lealtad.repository.ClientePuntosRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientePuntosService {

    private final ClientePuntosRepository clientePuntosRepository;

    @Transactional
    public PuntosResponse crearClientePuntos(ClienteCreadoEvent evento) {
        log.info("Creando registro de puntos para cliente: {}", evento.getClienteId());

        if (clientePuntosRepository.existsByClienteId(evento.getClienteId())) {
            log.error("El cliente con ID '{}' ya tiene un registro de puntos", evento.getClienteId());
            throw new IllegalArgumentException(
                    String.format("El cliente con ID '%s' ya tiene un registro de puntos", evento.getClienteId()));
        }

        ClientePuntos cliente = ClientePuntos.builder()
                .clienteId(evento.getClienteId())
                .nombre(evento.getNombre())
                .email(evento.getEmail())
                .puntos(0)
                .estado("ACTIVO")
                .nivelLealtad("BRONZE")
                .build();

        ClientePuntos guardado = clientePuntosRepository.save(cliente);
        log.info("Registro de puntos creado exitosamente para cliente: {} con nivel: {}",
                guardado.getClienteId(), guardado.getNivelLealtad());

        return PuntosResponse.of(guardado);
    }

    @Transactional(readOnly = true)
    public PuntosResponse obtenerPuntosPorClienteId(String clienteId) {
        log.info("Consultando puntos del cliente: {}", clienteId);
        ClientePuntos cliente = buscarClienteOLanzarExcepcion(clienteId);
        log.debug("Cliente encontrado: {} con {} puntos", clienteId, cliente.getPuntos());
        return PuntosResponse.of(cliente);
    }

    @Transactional
    public PuntosResponse sumarPuntos(String clienteId, Integer puntos) {
        log.info("Sumando {} puntos al cliente: {}", puntos, clienteId);

        if (puntos == null || puntos <= 0) {
            throw new IllegalArgumentException("La cantidad de puntos a sumar debe ser mayor a 0");
        }

        ClientePuntos cliente = buscarClienteOLanzarExcepcion(clienteId);
        cliente.setPuntos(cliente.getPuntos() + puntos);
        actualizarNivelLealtad(cliente);

        ClientePuntos actualizado = clientePuntosRepository.save(cliente);
        log.info("Puntos actualizados para cliente: {}. Total: {}, Nivel: {}",
                clienteId, actualizado.getPuntos(), actualizado.getNivelLealtad());

        return PuntosResponse.of(actualizado);
    }

    @Transactional
    public PuntosResponse restarPuntos(String clienteId, Integer puntos) {
        log.info("Restando {} puntos al cliente: {}", puntos, clienteId);

        if (puntos == null || puntos <= 0) {
            throw new IllegalArgumentException("La cantidad de puntos a restar debe ser mayor a 0");
        }

        ClientePuntos cliente = buscarClienteOLanzarExcepcion(clienteId);

        if (cliente.getPuntos() < puntos) {
            throw new IllegalArgumentException(
                    String.format("El cliente '%s' no tiene suficientes puntos. Disponibles: %d, Solicitados: %d",
                            clienteId, cliente.getPuntos(), puntos));
        }

        cliente.setPuntos(cliente.getPuntos() - puntos);
        actualizarNivelLealtad(cliente);

        ClientePuntos actualizado = clientePuntosRepository.save(cliente);
        log.info("Puntos actualizados para cliente: {}. Total: {}, Nivel: {}",
                clienteId, actualizado.getPuntos(), actualizado.getNivelLealtad());

        return PuntosResponse.of(actualizado);
    }

    @Transactional(readOnly = true)
    public List<PuntosResponse> obtenerTodos() {
        log.info("Consultando todos los registros de puntos");
        List<PuntosResponse> resultado = clientePuntosRepository.findAll()
                .stream()
                .map(PuntosResponse::of)
                .collect(Collectors.toList());
        log.debug("Se encontraron {} registros de puntos", resultado.size());
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<PuntosResponse> obtenerClientesPorNivel(String nivel) {
        log.info("Consultando clientes con nivel: {}", nivel);
        List<PuntosResponse> resultado = clientePuntosRepository.findByNivelLealtad(nivel.toUpperCase())
                .stream()
                .map(PuntosResponse::of)
                .collect(Collectors.toList());
        log.debug("Se encontraron {} clientes con nivel {}", resultado.size(), nivel);
        return resultado;
    }

    private void actualizarNivelLealtad(ClientePuntos cliente) {
        String nivelAnterior = cliente.getNivelLealtad();
        String nuevoNivel;

        if (cliente.getPuntos() >= 1000) {
            nuevoNivel = "GOLD";
        } else if (cliente.getPuntos() >= 500) {
            nuevoNivel = "SILVER";
        } else {
            nuevoNivel = "BRONZE";
        }

        cliente.setNivelLealtad(nuevoNivel);

        if (!nivelAnterior.equals(nuevoNivel)) {
            log.info("Nivel de lealtad actualizado para cliente '{}': {} -> {}",
                    cliente.getClienteId(), nivelAnterior, nuevoNivel);
        }
    }

    private ClientePuntos buscarClienteOLanzarExcepcion(String clienteId) {
        return clientePuntosRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException(clienteId));
    }
}
