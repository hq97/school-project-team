package com.school.schedule.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.schedule.dto.TeacherCourseRequest;
import com.school.schedule.service.TeacherCourseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教务域「F. 排课管理」REQ-F1 教师任课表。
 *
 * <p>列表（GET）按归属范围收窄（R-TEACHER 仅本人、R-DEAN 全校）；写操作（POST/PUT/DELETE）
 * 由 BTN-SCH-BASIC-EDIT 门禁（R-DEAN / R-VP / R-SUBJECT）+ 服务端 matchRecord 判归属（越权 40304）。</p>
 */
@RestController
@RequestMapping("/api/schedule/teacher-courses")
public class TeacherCourseController {

    private final TeacherCourseService teacherCourseService;

    public TeacherCourseController(TeacherCourseService teacherCourseService) {
        this.teacherCourseService = teacherCourseService;
    }

    /** 任课列表（M0601，?termId= 可选，缺省当前学期）。 */
    @DataScope(resource = "teacher_course", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId")
    @RequireMenu("M0601")
    @GetMapping
    public Map<String, Object> list(HttpServletRequest request,
                                    @RequestParam(required = false) String termId) {
        return ApiResponse.ok(teacherCourseService.list(request, termId));
    }

    /** 新建任课（M0601 + BTN-SCH-BASIC-EDIT）。 */
    @DataScope(resource = "teacher_course", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId")
    @RequireButton("BTN-SCH-BASIC-EDIT")
    @RequireMenu("M0601")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody TeacherCourseRequest req) {
        return ApiResponse.ok(teacherCourseService.create(req));
    }

    /** 更新任课（M0601 + BTN-SCH-BASIC-EDIT）。 */
    @DataScope(resource = "teacher_course", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId")
    @RequireButton("BTN-SCH-BASIC-EDIT")
    @RequireMenu("M0601")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, HttpServletRequest request,
                                      @Valid @RequestBody TeacherCourseRequest req) {
        return ApiResponse.ok(teacherCourseService.update(request, id, req));
    }

    /** 删除任课（M0601 + BTN-SCH-BASIC-EDIT）。 */
    @DataScope(resource = "teacher_course", ownership = OwnershipType.ORG,
            ownerField = "teacherId", deptField = "classId")
    @RequireButton("BTN-SCH-BASIC-EDIT")
    @RequireMenu("M0601")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, HttpServletRequest request) {
        teacherCourseService.delete(request, id);
        return ApiResponse.ok(null);
    }
}
