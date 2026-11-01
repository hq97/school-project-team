package com.school.common.scope;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求主体（TS 语义来源：scope.types.ts 的 {@code Principal}）。
 *
 * <p>每次请求实时从库计算，不做授权快照缓存（ADR-05 / docs/04 §2.2）。</p>
 */
public final class Principal {

    private final String userId;
    private final String realName;
    private final List<String> roleCodes;
    /** 有效数据范围 = MAX(各角色范围)，SELF &lt; GRADE &lt; ALL */
    private final ScopeLevel scope;
    /** 角色数据范围是否配置完整；false → 运行时按 SELF 兜底 + 告警（AC-RBAC-2） */
    private final boolean scopeComplete;
    /** 挂靠部门节点 */
    private final List<String> deptIds;
    /** 向上找到的 GRADE 节点（跨年级兼课可取多个，取并集） */
    private final List<String> gradeNodeIds;
    /** 可见部门子树：GRADE 范围下的全部子孙节点（CLASS/SUBJECT 在内） */
    private final List<String> visibleDeptIds;
    /** 班主任身份（换届快照 E3）：全量返回 {classId, termId}，不按当前学期过滤 */
    private final List<HeadTeacherTerm> headTeacherTerms;
    private final String studentId;
    /** 家长关系型：关联子女并集 */
    private final List<String> childStudentIds;
    private final List<String> menuCodes;
    private final List<String> buttonCodes;
    private final String currentTermId;

    private Principal(Builder b) {
        this.userId = b.userId;
        this.realName = b.realName;
        this.roleCodes = List.copyOf(b.roleCodes);
        this.scope = b.scope;
        this.scopeComplete = b.scopeComplete;
        this.deptIds = List.copyOf(b.deptIds);
        this.gradeNodeIds = List.copyOf(b.gradeNodeIds);
        this.visibleDeptIds = List.copyOf(b.visibleDeptIds);
        this.headTeacherTerms = List.copyOf(b.headTeacherTerms);
        this.studentId = b.studentId;
        this.childStudentIds = List.copyOf(b.childStudentIds);
        this.menuCodes = List.copyOf(b.menuCodes);
        this.buttonCodes = List.copyOf(b.buttonCodes);
        this.currentTermId = b.currentTermId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String userId() { return userId; }
    public String realName() { return realName; }
    public List<String> roleCodes() { return roleCodes; }
    public ScopeLevel scope() { return scope; }
    public boolean scopeComplete() { return scopeComplete; }
    public List<String> deptIds() { return deptIds; }
    public List<String> gradeNodeIds() { return gradeNodeIds; }
    public List<String> visibleDeptIds() { return visibleDeptIds; }
    public List<HeadTeacherTerm> headTeacherTerms() { return headTeacherTerms; }
    public String studentId() { return studentId; }
    public List<String> childStudentIds() { return childStudentIds; }
    public List<String> menuCodes() { return menuCodes; }
    public List<String> buttonCodes() { return buttonCodes; }
    public String currentTermId() { return currentTermId; }

    public static final class Builder {
        private String userId;
        private String realName = "";
        private List<String> roleCodes = new ArrayList<>();
        private ScopeLevel scope = ScopeLevel.SELF;
        private boolean scopeComplete = true;
        private List<String> deptIds = new ArrayList<>();
        private List<String> gradeNodeIds = new ArrayList<>();
        private List<String> visibleDeptIds = new ArrayList<>();
        private List<HeadTeacherTerm> headTeacherTerms = new ArrayList<>();
        private String studentId;
        private List<String> childStudentIds = new ArrayList<>();
        private List<String> menuCodes = new ArrayList<>();
        private List<String> buttonCodes = new ArrayList<>();
        private String currentTermId = "";

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder realName(String v) { this.realName = v; return this; }
        public Builder roleCodes(List<String> v) { this.roleCodes = v; return this; }
        public Builder scope(ScopeLevel v) { this.scope = v; return this; }
        public Builder scopeComplete(boolean v) { this.scopeComplete = v; return this; }
        public Builder deptIds(List<String> v) { this.deptIds = v; return this; }
        public Builder gradeNodeIds(List<String> v) { this.gradeNodeIds = v; return this; }
        public Builder visibleDeptIds(List<String> v) { this.visibleDeptIds = v; return this; }
        public Builder headTeacherTerms(List<HeadTeacherTerm> v) { this.headTeacherTerms = v; return this; }
        public Builder studentId(String v) { this.studentId = v; return this; }
        public Builder childStudentIds(List<String> v) { this.childStudentIds = v; return this; }
        public Builder menuCodes(List<String> v) { this.menuCodes = v; return this; }
        public Builder buttonCodes(List<String> v) { this.buttonCodes = v; return this; }
        public Builder currentTermId(String v) { this.currentTermId = v; return this; }

        public Principal build() {
            if (userId == null || userId.isEmpty()) {
                throw new IllegalStateException("Principal.userId 不能为空");
            }
            return new Principal(this);
        }
    }
}
