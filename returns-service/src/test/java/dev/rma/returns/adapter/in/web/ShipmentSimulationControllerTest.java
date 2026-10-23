package dev.rma.returns.adapter.in.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.rma.returns.application.ShipmentSimulationService;

class ShipmentSimulationControllerTest {
    private static final String NUMBER = "RMA-2026-000001";
    private static final String BASE = "/internal/sim/returns/" + NUMBER;
    private final ShipmentSimulationService service = mock(ShipmentSimulationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ShipmentSimulationController(
                        service, Jackson2ObjectMapperBuilder.json().build()))
                .setControllerAdvice(new ReturnApiExceptionHandler())
                .build();
    }

    @Test
    void delegatesShipmentCommandsAndReturnsNoContent() throws Exception {
        mvc.perform(
                        post(BASE + "/label-issued")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"labelId":"label-1","trackingNumber":"tracking-1","labelFileKey":"labels/1.pdf"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(
                        post(BASE + "/pickup-scheduled")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"pickupId":"pickup-1","scheduledFor":"2026-10-24"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(post(BASE + "/parcel-in-transit"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(post(BASE + "/parcel-received")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"warehouse-1\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        var order = inOrder(service);
        order.verify(service).issueLabel(NUMBER, "label-1", "tracking-1", "labels/1.pdf");
        order.verify(service).schedulePickup(NUMBER, "pickup-1", LocalDate.of(2026, 10, 24));
        order.verify(service).markInTransit(NUMBER);
        order.verify(service).receiveParcel(NUMBER, "warehouse-1");
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            label-issued | {"labelId":"label","trackingNumber":"tracking"}
            label-issued | {"labelId":" ","trackingNumber":"tracking","labelFileKey":"file"}
            label-issued | {"labelId":123,"trackingNumber":"tracking","labelFileKey":"file"}
            pickup-scheduled | {"pickupId":"pickup"}
            pickup-scheduled | {"pickupId":"pickup","scheduledFor":"2026-02-30"}
            pickup-scheduled | {"pickupId":"pickup","scheduledFor":123}
            parcel-received | {"warehouseId":null}
            parcel-received | {"warehouseId":" "}
            parcel-received | {"warehouseId":"warehouse","unknown":true}
            parcel-received | {"warehouseId":"warehouse"} {}
            parcel-received | []
            parcel-received | null
            parcel-received | {broken
            """)
    void rejectsInvalidBodyBeforeCallingService(String endpoint, String body) throws Exception {
        mvc.perform(post(BASE + "/" + endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"label-issued", "pickup-scheduled", "parcel-in-transit", "parcel-received"})
    void rejectsInvalidBusinessNumber(String endpoint) throws Exception {
        mvc.perform(post("/internal/sim/returns/invalid/" + endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"label-issued", "pickup-scheduled", "parcel-received"})
    void requiresJsonBody(String endpoint) throws Exception {
        mvc.perform(post(BASE + "/" + endpoint).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + endpoint)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }
}
