package com.ems.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate date,
        long totalEmployees,
        long activeEmployees,
        long totalDepartments,
        List<DepartmentHeadcount> headcountByDepartment,
        TodayAttendance todayAttendance,
        long pendingLeaveRequests,
        long employeesOnLeaveToday) {

    public record TodayAttendance(long present, long late, long checkedOut, long notCheckedIn) {
    }
}
