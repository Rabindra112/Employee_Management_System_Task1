package com.example.ems.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StatusErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "400, Passwords do not match",
            "401, Invalid email or password",
            "409, Email already registered"
    })
    void preservesExplicitStatusAndReason(int statusCode, String reason) throws Exception {
        mvc.perform(get("/status-error/{statusCode}", statusCode).param("reason", reason))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.message").value(reason))
                .andExpect(jsonPath("$.path").value("/status-error/" + statusCode));
    }

    @Test
    void handlesStatusExceptionWithoutReason() throws Exception {
        mvc.perform(get("/status-error/401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @RestController
    static class StatusErrorController {

        @GetMapping("/status-error/{statusCode}")
        void fail(@PathVariable int statusCode,
                  @RequestParam(required = false) String reason) {
            throw new ResponseStatusException(HttpStatus.valueOf(statusCode), reason);
        }
    }
}
