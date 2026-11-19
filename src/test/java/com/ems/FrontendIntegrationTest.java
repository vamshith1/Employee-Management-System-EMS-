package com.ems;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The static frontend is public; the data behind it is not. */
class FrontendIntegrationTest extends IntegrationTestSupport {

    @Test
    void frontendAssetsAreServedWithoutLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/assets/app.js")));
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/styles.css")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
    }

    @Test
    void apiStillRequiresToken() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
    }
}
