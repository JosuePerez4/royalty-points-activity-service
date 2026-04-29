package com.lealtad.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteCreadoEvent {

    @JsonProperty("cliente_id")
    private String clienteId;

    private String nombre;

    private String email;

    private String telefono;

    private LocalDateTime timestamp;

    @JsonProperty("evento_id")
    private String eventoId;
}
