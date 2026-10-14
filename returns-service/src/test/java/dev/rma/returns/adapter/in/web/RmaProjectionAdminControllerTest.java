package dev.rma.returns.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.rma.returns.application.port.in.RebuildRmaProjectionUseCase;

class RmaProjectionAdminControllerTest {
    @Test
    void shouldRebuildProjectionAndReturnNoContent() throws Exception {
        AtomicInteger rebuildCalls = new AtomicInteger();
        RebuildRmaProjectionUseCase rebuild = () -> rebuildCalls.incrementAndGet();
        var mockMvc = MockMvcBuilders.standaloneSetup(new RmaProjectionAdminController(rebuild))
                .build();

        mockMvc.perform(post("/admin/projections/rebuild"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(rebuildCalls.get()).isEqualTo(1);
    }
}
