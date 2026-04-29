package com.lealtad.repository;

import com.lealtad.entity.ClientePuntos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientePuntosRepository extends JpaRepository<ClientePuntos, UUID> {

    Optional<ClientePuntos> findByClienteId(String clienteId);

    boolean existsByClienteId(String clienteId);

    List<ClientePuntos> findByNivelLealtad(String nivelLealtad);
}
