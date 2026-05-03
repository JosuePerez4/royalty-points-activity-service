package com.lealtad;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lealtad.dto.ClienteCreadoEvent;
import com.lealtad.dto.PuntosResponse;
import com.lealtad.entity.ClientePuntos;
import com.lealtad.exception.ClienteNoEncontradoException;
import com.lealtad.repository.ClientePuntosRepository;
import com.lealtad.service.ClientePuntosService;

@ExtendWith(MockitoExtension.class)
class ClientePuntosServiceTest {

    @Mock
    private ClientePuntosRepository clientePuntosRepository;

    @InjectMocks
    private ClientePuntosService clientePuntosService;

    private ClienteCreadoEvent eventoCreacion;
    private ClientePuntos clienteExistente;

    @BeforeEach
    void setUp() {
        eventoCreacion = ClienteCreadoEvent.builder()
                .clienteId("CLI-001")
                .nombre("Juan Pérez")
                .email("juan@correo.com")
                .telefono("555-1234")
                .timestamp(LocalDateTime.now())
                .eventoId("EVT-001")
                .build();

        clienteExistente = ClientePuntos.builder()
                .id(UUID.randomUUID())
                .clienteId("CLI-001")
                .nombre("Juan Pérez")
                .email("juan@correo.com")
                .puntos(0)
                .estado("ACTIVO")
                .nivelLealtad("BRONZE")
                .fechaCreacion(LocalDateTime.now())
                .fechaUltimaActualizacion(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Crear cliente de puntos con 0 puntos y nivel BRONZE")
    void crearClientePuntos_debeCrearConCeroPuntos() {
        when(clientePuntosRepository.existsByClienteId(anyString())).thenReturn(false);
        when(clientePuntosRepository.save(any(ClientePuntos.class))).thenReturn(clienteExistente);

        PuntosResponse response = clientePuntosService.crearClientePuntos(eventoCreacion);

        assertNotNull(response);
        assertEquals("CLI-001", response.getClienteId());
        assertEquals("Juan Pérez", response.getNombre());
        assertEquals(0, response.getPuntos());
        assertEquals("BRONZE", response.getNivelLealtad());
        verify(clientePuntosRepository).save(any(ClientePuntos.class));
    }

    @Test
    @DisplayName("No permitir crear cliente duplicado")
    void crearClientePuntos_debeLanzarExcepcionSiDuplicado() {
        when(clientePuntosRepository.existsByClienteId("CLI-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> clientePuntosService.crearClientePuntos(eventoCreacion));

        verify(clientePuntosRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sumar puntos y verificar cambio de nivel a SILVER")
    void sumarPuntos_debeIncrementarYCambiarNivel() {
        clienteExistente.setPuntos(400);
        when(clientePuntosRepository.findByClienteId("CLI-001")).thenReturn(Optional.of(clienteExistente));
        when(clientePuntosRepository.save(any(ClientePuntos.class))).thenAnswer(inv -> inv.getArgument(0));

        PuntosResponse response = clientePuntosService.sumarPuntos("CLI-001", 200);

        assertEquals(600, response.getPuntos());
        assertEquals("SILVER", response.getNivelLealtad());
    }

    @Test
    @DisplayName("Sumar puntos y verificar cambio de nivel a GOLD")
    void sumarPuntos_debeActualizarAGold() {
        clienteExistente.setPuntos(900);
        when(clientePuntosRepository.findByClienteId("CLI-001")).thenReturn(Optional.of(clienteExistente));
        when(clientePuntosRepository.save(any(ClientePuntos.class))).thenAnswer(inv -> inv.getArgument(0));

        PuntosResponse response = clientePuntosService.sumarPuntos("CLI-001", 200);

        assertEquals(1100, response.getPuntos());
        assertEquals("GOLD", response.getNivelLealtad());
    }

    @Test
    @DisplayName("Restar puntos correctamente")
    void restarPuntos_debeDecrementarPuntos() {
        clienteExistente.setPuntos(500);
        clienteExistente.setNivelLealtad("SILVER");
        when(clientePuntosRepository.findByClienteId("CLI-001")).thenReturn(Optional.of(clienteExistente));
        when(clientePuntosRepository.save(any(ClientePuntos.class))).thenAnswer(inv -> inv.getArgument(0));

        PuntosResponse response = clientePuntosService.restarPuntos("CLI-001", 100);

        assertEquals(400, response.getPuntos());
        assertEquals("BRONZE", response.getNivelLealtad());
    }

    @Test
    @DisplayName("Lanzar excepción al restar más puntos de los disponibles")
    void restarPuntos_debeLanzarExcepcionSiInsuficientes() {
        clienteExistente.setPuntos(50);
        when(clientePuntosRepository.findByClienteId("CLI-001")).thenReturn(Optional.of(clienteExistente));

        assertThrows(IllegalArgumentException.class,
                () -> clientePuntosService.restarPuntos("CLI-001", 100));

        verify(clientePuntosRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lanzar ClienteNoEncontradoException cuando no existe el cliente")
    void obtenerPuntos_debeLanzarExcepcionSiNoExiste() {
        when(clientePuntosRepository.findByClienteId("CLI-999")).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> clientePuntosService.obtenerPuntosPorClienteId("CLI-999"));
    }

    @Test
    @DisplayName("Sumar puntos negativos debe lanzar excepción")
    void sumarPuntos_debeLanzarExcepcionConPuntosNegativos() {
        assertThrows(IllegalArgumentException.class,
                () -> clientePuntosService.sumarPuntos("CLI-001", -10));
    }

    @Test
    @DisplayName("Obtener todos los clientes de puntos")
    void obtenerTodos_debeRetornarListaCompleta() {
        ClientePuntos cliente2 = ClientePuntos.builder()
                .id(UUID.randomUUID())
                .clienteId("CLI-002")
                .nombre("María López")
                .email("maria@correo.com")
                .puntos(750)
                .estado("ACTIVO")
                .nivelLealtad("SILVER")
                .fechaCreacion(LocalDateTime.now())
                .build();

        when(clientePuntosRepository.findAll()).thenReturn(List.of(clienteExistente, cliente2));

        List<PuntosResponse> resultado = clientePuntosService.obtenerTodos();

        assertEquals(2, resultado.size());
        verify(clientePuntosRepository).findAll();
    }

    @Test
    @DisplayName("Obtener clientes por nivel SILVER")
    void obtenerClientesPorNivel_debeRetornarFiltrados() {
        ClientePuntos clienteSilver = ClientePuntos.builder()
                .id(UUID.randomUUID())
                .clienteId("CLI-003")
                .nombre("Carlos Ruiz")
                .email("carlos@correo.com")
                .puntos(600)
                .estado("ACTIVO")
                .nivelLealtad("SILVER")
                .fechaCreacion(LocalDateTime.now())
                .build();

        when(clientePuntosRepository.findByNivelLealtad("SILVER")).thenReturn(List.of(clienteSilver));

        List<PuntosResponse> resultado = clientePuntosService.obtenerClientesPorNivel("SILVER");

        assertEquals(1, resultado.size());
        assertEquals("SILVER", resultado.get(0).getNivelLealtad());
    }
}
