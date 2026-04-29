package com.lealtad.exception;

public class ClienteNoEncontradoException extends RuntimeException {

    public ClienteNoEncontradoException(String clienteId) {
        super(String.format("Cliente con ID '%s' no encontrado", clienteId));
    }
}
