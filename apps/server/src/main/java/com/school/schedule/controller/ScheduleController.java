package com.school.schedule.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.schedule.dto.EntryCreateRequest;
import com.school.schedule.dto.PublishRequest;
import com.school.schedule.service.ScheduleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教务域排课（M06）+ 查课（M07）。
 *
 * <p>排课写接口（entries/publish）由 R-DEAN（ALL）经 BTN-SCH-ARRANGE / BTN-SCH-PUBLISH 门禁；
 * 查课按数据范围收窄（我的课表 forceSelf、班级课表 classField、全校课表 BTN-SCH-VIEW-ALL）。</p>
 */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 手动排课（M0602）。 */
    @DataScope(resource = "timetable_entry", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId", termField = "termId")
    @RequireButton("BTN-SCH-ARRANGE")
    @RequireMenu("M0602")
    @PostMapping("/entries")
    public Map<String, Object> arrange(@Valid @RequestBody EntryCreateRequest req) {
        return ApiResponse.ok(scheduleService.arrange(req));
    }

    /** 可选场地（M0602，PUBLIC）。 */
    @DataScope(resource = "space", ownership = OwnershipType.PUBLIC)
    @RequireMenu("M0602")
    @GetMapping("/spaces")
    public Map<String, Object> spaces() {
        return ApiResponse.ok(scheduleService.spaces());
    }

    /** 课表发布（M0604）。 */
    @DataScope(resource = "timetable_entry", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId", termField = "termId")
    @RequireButton("BTN-SCH-PUBLISH")
    @RequireMenu("M0604")
    @PostMapping("/publish")
    public Map<String, Object> publish(@Valid @RequestBody PublishRequest req) {
        return ApiResponse.ok(scheduleService.publish(req));
    }

    /** 我的课表（M0701，forceSelf）。 */
    @DataScope(resource = "timetable_entry", ownership = OwnershipType.ORG,
            ownerField = "teacherId", termField = "termId", forceSelf = true)
    @RequireMenu("M0701")
    @GetMapping("/my-timetable")
    public Map<String, Object> myTimetable(HttpServletRequest request,
                                           @RequestParam(required = false) Integer week) {
        return ApiResponse.ok(scheduleService.myTimetable(request, week));
    }

    /** 班级课表（M0702，班主任 head_teacher 收窄 / 普通教师按范围）。 */
    @DataScope(resource = "timetable_entry", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId", classField = "classId", termField = "termId")
    @RequireMenu("M0702")
    @GetMapping("/class-timetable")
    public Map<String, Object> classTimetable(HttpServletRequest request,
                                              @RequestParam(required = false) String classId,
                                              @RequestParam(required = false) Integer week) {
        return ApiResponse.ok(scheduleService.classTimetable(request, classId, week));
    }

    /** 全校课表总览（M0703，BTN-SCH-VIEW-ALL）。 */
    @DataScope(resource = "timetable_entry", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId", termField = "termId")
    @RequireButton("BTN-SCH-VIEW-ALL")
    @RequireMenu("M0703")
    @GetMapping("/school-timetable")
    public Map<String, Object> schoolTimetable(HttpServletRequest request,
                                               @RequestParam(required = false) Integer week) {
        return ApiResponse.ok(scheduleService.schoolTimetable(request, week));
    }
}
