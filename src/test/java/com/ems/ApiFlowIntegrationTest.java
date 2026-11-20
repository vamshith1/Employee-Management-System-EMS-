package com.ems;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end business flows. Each test runs in a transaction that is rolled back. */
@Transactional
class ApiFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    void employeeCrudWithValidationAndConflicts() throws Exception {
        String hr = hrToken();
        Map<String, Object> body = new HashMap<>(Map.of(
                "firstName", "Meera", "lastName", "Joshi", "email", "meera.joshi@ems.local",
                "jobTitle", "Data Analyst", "salary", 67000, "hireDate", "2025-06-01", "departmentId", 1));

        String created = mvc.perform(auth(post("/api/employees"), hr).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/employees/")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.departmentName").value("Engineering"))
                .andReturn().getResponse().getContentAsString();
        long id = read(created).get("id").asLong();

        // duplicate email -> 409
        mvc.perform(auth(post("/api/employees"), hr).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isConflict());

        // update
        body.put("jobTitle", "Senior Data Analyst");
        body.put("status", "ON_LEAVE");
        mvc.perform(auth(put("/api/employees/" + id), hr).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Senior Data Analyst"))
                .andExpect(jsonPath("$.status").value("ON_LEAVE"));

        // delete then 404
        mvc.perform(auth(delete("/api/employees/" + id), hr)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/employees/" + id), hr))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Employee not found with id " + id));
    }

    @Test
    void invalidEmployeeReturnsFieldErrors() throws Exception {
        Map<String, Object> bad = Map.of("firstName", "", "email", "not-an-email", "salary", -5,
                "hireDate", LocalDate.now().plusDays(10).toString());

        String response = mvc.perform(auth(post("/api/employees"), hrToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andReturn().getResponse().getContentAsString();

        JsonNode errors = read(response).get("fieldErrors");
        assertThat(errors.findValuesAsText("field"))
                .contains("firstName", "lastName", "email", "jobTitle", "salary", "hireDate", "departmentId");
    }

    @Test
    void searchFiltersPaginatesAndSorts() throws Exception {
        String hr = hrToken();

        mvc.perform(auth(get("/api/employees").param("departmentId", "1").param("sort", "salary,desc").param("size", "2"), hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].fullName").value("Neha Gupta"))
                .andExpect(jsonPath("$.content[*].departmentName", everyItem(is("Engineering"))));

        mvc.perform(auth(get("/api/employees").param("q", "priya sharma"), hr))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("priya.sharma@ems.local"));

        mvc.perform(auth(get("/api/employees").param("status", "INACTIVE"), hr))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Kapoor"));

        mvc.perform(auth(get("/api/employees").param("status", "SLEEPING"), hr))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/employees").param("sort", "password"), hr))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Cannot sort by 'password'")));
    }

    @Test
    void leaveRequestLifecycle() throws Exception {
        String employee = employeeToken();
        String hr = hrToken();
        LocalDate start = LocalDate.now().plusDays(30);

        String submitted = mvc.perform(auth(post("/api/leaves"), employee).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "SICK", "startDate", start.toString(),
                                "endDate", start.plusDays(1).toString(), "reason", "Surgery recovery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.days").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = read(submitted).get("id").asLong();

        // overlapping request -> 409
        mvc.perform(auth(post("/api/leaves"), employee).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "CASUAL", "startDate", start.plusDays(1).toString(),
                                "endDate", start.plusDays(2).toString(), "reason", "Overlap"))))
                .andExpect(status().isConflict());

        // end before start -> 400 from bean validation
        mvc.perform(auth(post("/api/leaves"), employee).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "CASUAL", "startDate", start.plusDays(10).toString(),
                                "endDate", start.plusDays(9).toString(), "reason", "Backwards"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("End date must be on or after start date"));

        mvc.perform(auth(get("/api/leaves").param("status", "PENDING"), hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));

        mvc.perform(auth(put("/api/leaves/" + id + "/approve"), hr).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "Get well soon"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("hr"));

        // already decided -> 409; employee can no longer cancel
        mvc.perform(auth(put("/api/leaves/" + id + "/reject"), hr)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/leaves/" + id + "/cancel"), employee)).andExpect(status().isConflict());
    }

    @Test
    void attendanceCheckInAndOut() throws Exception {
        String employee = employeeToken();

        mvc.perform(auth(post("/api/attendance/check-out"), employee))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have not checked in today"));

        mvc.perform(auth(post("/api/attendance/check-in"), employee))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.status", oneOf("PRESENT", "LATE")));

        mvc.perform(auth(post("/api/attendance/check-in"), employee)).andExpect(status().isConflict());

        mvc.perform(auth(post("/api/attendance/check-out"), employee).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("notes", "Done for the day"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkOut").isNotEmpty())
                .andExpect(jsonPath("$.notes").value("Done for the day"));

        mvc.perform(auth(get("/api/dashboard"), hrToken()))
                .andExpect(jsonPath("$.todayAttendance.present").value(1))
                .andExpect(jsonPath("$.todayAttendance.checkedOut").value(1));
    }

    @Test
    void hrRecordsManualAttendanceWithValidation() throws Exception {
        String hr = hrToken();
        String day = LocalDate.now().minusDays(30).toString();

        mvc.perform(auth(post("/api/attendance"), hr).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", 2, "workDate", day, "status", "ABSENT", "notes", "No show"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABSENT"));

        mvc.perform(auth(post("/api/attendance"), hr).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", 2, "workDate", day, "status", "ABSENT"))))
                .andExpect(status().isConflict());

        mvc.perform(auth(post("/api/attendance"), hr).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", 2, "workDate", day, "status", "PRESENT",
                                "checkIn", day + "T18:00:00", "checkOut", day + "T09:00:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Check-out must be after check-in"));

        mvc.perform(auth(get("/api/attendance").param("employeeId", "2").param("status", "ABSENT"), hr))
                .andExpect(jsonPath("$.totalElements").value(1));

        // date-range filters (what the UI sends by default)
        mvc.perform(auth(get("/api/attendance").param("from", day).param("to", day), hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].workDate").value(day));
        mvc.perform(auth(get("/api/attendance").param("from", day), hr)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/attendance").param("to", day).param("status", "ABSENT"), hr))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/attendance").param("from", day).param("to", LocalDate.now().minusDays(40).toString()), hr))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownAttendanceFilteredByDate() throws Exception {
        String employee = employeeToken();
        String today = LocalDate.now().toString();
        mvc.perform(auth(post("/api/attendance/check-in"), employee)).andExpect(status().isCreated());

        mvc.perform(auth(get("/api/attendance/me").param("from", today).param("to", today).param("size", "1"), employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].workDate").value(today));
        mvc.perform(auth(get("/api/attendance/me").param("to", LocalDate.now().minusDays(1).toString()), employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(10));
    }

    @Test
    void dashboardSummarisesWorkforce() throws Exception {
        mvc.perform(auth(get("/api/dashboard"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(9))
                .andExpect(jsonPath("$.activeEmployees").value(8))
                .andExpect(jsonPath("$.totalDepartments").value(5))
                .andExpect(jsonPath("$.pendingLeaveRequests").value(7))
                .andExpect(jsonPath("$.employeesOnLeaveToday").value(1))
                .andExpect(jsonPath("$.headcountByDepartment.length()").value(5))
                .andExpect(jsonPath("$.headcountByDepartment[?(@.departmentName=='Engineering')].headcount").value(3))
                .andExpect(jsonPath("$.headcountByDepartment[?(@.departmentName=='Finance')].headcount").value(1))
                .andExpect(jsonPath("$.headcountByDepartment[?(@.departmentName=='Marketing')].headcount").value(0));
    }

    @Test
    void demoLeaveRequestsCoverEveryStatus() throws Exception {
        String hr = hrToken();
        for (String s : new String[]{"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) {
            mvc.perform(auth(get("/api/leaves").param("status", s), hr))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements", greaterThan(0)));
        }
        mvc.perform(auth(get("/api/leaves").param("status", "REJECTED"), hr))
                .andExpect(jsonPath("$.content[0].reviewedBy").value("hr"))
                .andExpect(jsonPath("$.content[0].reviewComment", not(emptyOrNullString())));
    }

    @Test
    void departmentRules() throws Exception {
        String admin = adminToken();

        mvc.perform(auth(post("/api/departments"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "engineering"))))
                .andExpect(status().isConflict());

        mvc.perform(auth(delete("/api/departments/1"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("still has employees")));

        String created = mvc.perform(auth(post("/api/departments"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Legal", "description", "Contracts"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(auth(delete("/api/departments/" + read(created).get("id").asLong()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCreatesLoginForEmployeeWhoCanThenSignIn() throws Exception {
        String admin = adminToken();

        mvc.perform(auth(post("/api/users"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "neha", "password", "Welcome@2026", "role", "EMPLOYEE", "employeeId", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeName").value("Neha Gupta"));

        mvc.perform(auth(post("/api/users"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "neha2", "password", "Welcome@2026", "role", "EMPLOYEE", "employeeId", 3))))
                .andExpect(status().isConflict());

        String token = login("neha", "Welcome@2026");
        mvc.perform(auth(get("/api/employees/me"), token))
                .andExpect(jsonPath("$.email").value("neha.gupta@ems.local"));
    }
}
