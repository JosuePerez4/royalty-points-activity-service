package com.lealtad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cliente_puntos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientePuntos {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cliente_id", nullable = false, unique = true)
    private String clienteId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private Integer puntos = 0;

    @Builder.Default
    @Column(nullable = false)
    private String estado = "ACTIVO";

    @Builder.Default
    @Column(name = "nivel_lealtad", nullable = false)
    private String nivelLealtad = "BRONZE";

    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_ultima_actualizacion")
    private LocalDateTime fechaUltimaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaUltimaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaUltimaActualizacion = LocalDateTime.now();
    }
}
