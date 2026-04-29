package com.lealtad.service;

import com.lealtad.config.RabbitMQConfig;
import com.lealtad.dto.ClienteCreadoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventoListener {

    private final ClientePuntosService clientePuntosService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PUNTOS)
    public void procesarClienteCreado(ClienteCreadoEvent evento) {
        log.info("Evento recibido - Cliente creado: {} (eventoId: {})",
                evento.getClienteId(), evento.getEventoId());
        try {
            clientePuntosService.crearClientePuntos(evento);
            log.info("Evento procesado exitosamente para cliente: {}", evento.getClienteId());
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al procesar evento para cliente {}: {}",
                    evento.getClienteId(), e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al procesar evento para cliente {}: {}",
                    evento.getClienteId(), e.getMessage(), e);
        }
    }
}
