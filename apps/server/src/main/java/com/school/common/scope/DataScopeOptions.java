package com.school.common.scope;

/**
 * {@link DataScope} 注解解析后的值对象（TS 语义来源：data-scope.options.ts 的 {@code DataScopeOptions}）。
 *
 * <p>{@link DataScopeInterceptor} 在运行时把本对象放入 request attribute，
 * Controller 通过 {@link ScopeQueryBuilder#fromRequest(jakarta.servlet.http.HttpServletRequest)} 重建查询构建器。</p>
 */
public final class DataScopeOptions {

    private final String resource;
    private final OwnershipType ownership;
    private final ScopeLevel requiredScope; // nullable
    private final String ownerField;
    private final String deptField;
    private final String classField;
    private final String termField;
    private final boolean forceSelf;
    private final boolean write;

    public DataScopeOptions(String resource, OwnershipType ownership, ScopeLevel requiredScope,
                            String ownerField, String deptField, String classField, String termField,
                            boolean forceSelf, boolean write) {
        this.resource = resource;
        this.ownership = ownership;
        this.requiredScope = requiredScope;
        this.ownerField = normalize(ownerField, "ownerId");
        this.deptField = normalize(deptField, "deptId");
        this.classField = normalize(classField, "");
        this.termField = normalize(termField, "");
        this.forceSelf = forceSelf;
        this.write = write;
    }

    public static DataScopeOptions from(DataScope ann) {
        ScopeLevel rs = (ann.requiredScope() == null || ann.requiredScope().isEmpty())
                ? null
                : ScopeLevel.valueOf(ann.requiredScope());
        return new DataScopeOptions(
                ann.resource(), ann.ownership(), rs,
                ann.ownerField(), ann.deptField(), ann.classField(), ann.termField(),
                ann.forceSelf(), ann.write());
    }

    public String resource() { return resource; }
    public OwnershipType ownership() { return ownership; }
    public ScopeLevel requiredScope() { return requiredScope; }
    public String ownerField() { return ownerField; }
    public String deptField() { return deptField; }
    public String classField() { return classField; }
    public String termField() { return termField; }
    public boolean forceSelf() { return forceSelf; }
    public boolean write() { return write; }

    private static String normalize(String v, String fallback) {
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
