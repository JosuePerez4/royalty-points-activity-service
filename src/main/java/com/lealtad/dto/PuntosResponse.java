package com.lealtad.dto;

import com.lealtad.entity.ClientePuntos;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PuntosResponse {

    private String clienteId;
    private String nombre;
    private Integer puntos;
    private String nivelLealtad;
    private String estado;
    private LocalDateTime fechaCreacion;

    public static PuntosResponse of(ClientePuntos cliente) {
        return PuntosResponse.builder()
                .clienteId(cliente.getClienteId())
                .nombre(cliente.getNombre())
                .puntos(cliente.getPuntos())
                .nivelLealtad(cliente.getNivelLealtad())
                .estado(cliente.getEstado())
                .fechaCreacion(cliente.getFechaCreacion())
                .build();
    }
}
